package pe.ayni.bank.identity.application.usecase;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.identity.domain.model.CodigoDeConfirmacionIncorrectoException;
import pe.ayni.bank.identity.domain.model.CodigoDesafioInvalidoException;
import pe.ayni.bank.identity.domain.model.CodigoTotp;
import pe.ayni.bank.identity.domain.model.ConfirmacionDeOperacion;
import pe.ayni.bank.identity.domain.model.ConfirmacionEmitida;
import pe.ayni.bank.identity.domain.model.ConfirmacionIniciada;
import pe.ayni.bank.identity.domain.model.ConfirmacionNoDisponibleException;
import pe.ayni.bank.identity.domain.model.ControlDeAcceso;
import pe.ayni.bank.identity.domain.model.CuentaBloqueadaException;
import pe.ayni.bank.identity.domain.model.DesafioExpiradoException;
import pe.ayni.bank.identity.domain.model.HuellaDeCliente;
import pe.ayni.bank.identity.domain.model.MaximoIntentosDesafioExcedidoException;
import pe.ayni.bank.identity.domain.model.MetodoDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.OperacionAConfirmar;
import pe.ayni.bank.identity.domain.model.SegundoFactor;
import pe.ayni.bank.identity.domain.model.SinSegundoFactorException;
import pe.ayni.bank.identity.domain.model.TipoDeEventoDeAcceso;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;
import pe.ayni.bank.identity.domain.port.in.ConfirmarOperacionUseCase;
import pe.ayni.bank.identity.domain.port.in.GenerarDesafioCodigoUseCase;
import pe.ayni.bank.identity.domain.port.in.VerificarDesafioCodigoUseCase;
import pe.ayni.bank.identity.domain.port.out.EmisorDeConfirmacionesPort;
import pe.ayni.bank.identity.domain.port.out.GeneradorDeTotpPort;
import pe.ayni.bank.identity.domain.port.out.PistaDeAuditoriaPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeConfirmacionesPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeControlDeAccesoPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeMetodoSegundoFactorPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSegundoFactorPort;

/**
 * HU-07 · Confirmacion de operaciones con el segundo factor. Ver ADR-0031.
 *
 * <p>El metodo se elige igual que en el ingreso: si el titular eligio el correo (o el SMS),
 * gana el mas reciente y se le envia un codigo; si no, la app autenticadora.
 *
 * <p><strong>Los fallos cuentan para el mismo contador que el ingreso.</strong> Cada
 * confirmacion admite tres intentos, pero quien tiene la sesion podria abrir confirmaciones
 * sin fin y probar codigos de tres en tres. Al compartir el contador, al sexto fallo se
 * pausa todo, ingreso incluido, igual que con la contrasena.
 */
@Service
public class ConfirmarOperacionService implements ConfirmarOperacionUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConfirmarOperacionService.class);

    private final RepositorioDeConfirmacionesPort confirmaciones;
    private final RepositorioDeMetodoSegundoFactorPort metodos;
    private final RepositorioDeSegundoFactorPort segundosFactores;
    private final GeneradorDeTotpPort totp;
    private final GenerarDesafioCodigoUseCase generarCodigo;
    private final VerificarDesafioCodigoUseCase verificarCodigo;
    private final RepositorioDeControlDeAccesoPort controles;
    private final PistaDeAuditoriaPort auditoria;
    private final EmisorDeConfirmacionesPort emisor;
    private final Clock reloj;

    @SuppressWarnings("java:S107") // Un puerto por colaborador: es lo que pide la arquitectura hexagonal.
    public ConfirmarOperacionService(RepositorioDeConfirmacionesPort confirmaciones,
                                     RepositorioDeMetodoSegundoFactorPort metodos,
                                     RepositorioDeSegundoFactorPort segundosFactores,
                                     GeneradorDeTotpPort totp,
                                     GenerarDesafioCodigoUseCase generarCodigo,
                                     VerificarDesafioCodigoUseCase verificarCodigo,
                                     RepositorioDeControlDeAccesoPort controles,
                                     PistaDeAuditoriaPort auditoria,
                                     EmisorDeConfirmacionesPort emisor,
                                     Clock reloj) {
        this.confirmaciones = confirmaciones;
        this.metodos = metodos;
        this.segundosFactores = segundosFactores;
        this.totp = totp;
        this.generarCodigo = generarCodigo;
        this.verificarCodigo = verificarCodigo;
        this.controles = controles;
        this.auditoria = auditoria;
        this.emisor = emisor;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public ConfirmacionIniciada iniciar(UUID usuarioId, OperacionAConfirmar operacion, HuellaDeCliente cliente) {
        Instant momento = reloj.instant();
        exigirNoBloqueado(usuarioId, momento);

        Optional<TipoDeSegundoFactor> porCodigo = metodos.listarPorUsuario(usuarioId).stream()
                .filter(m -> m.tipo() == TipoDeSegundoFactor.CORREO_ELECTRONICO || m.tipo() == TipoDeSegundoFactor.SMS)
                .max(Comparator.comparing(MetodoDeSegundoFactor::creadoEn))
                .map(MetodoDeSegundoFactor::tipo);

        ConfirmacionDeOperacion confirmacion;
        if (porCodigo.isPresent()) {
            UUID desafio = generarCodigo.generar(usuarioId, porCodigo.get()).desafio().id();
            confirmacion = ConfirmacionDeOperacion.abrir(UUID.randomUUID(), usuarioId, operacion.huella(),
                    porCodigo.get(), desafio, momento);
        } else {
            segundoFactorConfirmado(usuarioId);
            confirmacion = ConfirmacionDeOperacion.abrir(UUID.randomUUID(), usuarioId, operacion.huella(),
                    TipoDeSegundoFactor.APP_AUTENTICADORA, null, momento);
        }
        confirmaciones.guardar(confirmacion);
        log.info("Confirmacion de operacion abierta. usuarioId={} metodo={}", usuarioId, confirmacion.metodo());
        return new ConfirmacionIniciada(confirmacion.id(), confirmacion.metodo(), confirmacion.expiraEn());
    }

    // noRollbackFor: el intento fallido y el contador se escriben antes de lanzar la excepcion
    // y tienen que sobrevivirla (la leccion de AYNI-161).
    @Override
    @Transactional(noRollbackFor = {CodigoDeConfirmacionIncorrectoException.class, CuentaBloqueadaException.class})
    public ConfirmacionEmitida verificar(UUID usuarioId, UUID confirmacionId, String codigo, HuellaDeCliente cliente) {
        Instant momento = reloj.instant();
        ConfirmacionDeOperacion confirmacion = confirmaciones.buscar(confirmacionId)
                .filter(c -> c.usuarioId().equals(usuarioId))
                .filter(c -> c.estaVigente(momento))
                .orElseThrow(ConfirmacionNoDisponibleException::new);
        ControlDeAcceso control = exigirNoBloqueado(usuarioId, momento);

        if (!codigoCorrecto(confirmacion, codigo, momento)) {
            ConfirmacionDeOperacion fallida = confirmacion.registrarFallo();
            confirmaciones.guardar(fallida);
            controles.guardar(control.registrarFallo(momento));
            auditoria.registrar(TipoDeEventoDeAcceso.CONFIRMACION_FALLIDA, usuarioId, cliente);
            throw new CodigoDeConfirmacionIncorrectoException(fallida.intentosRestantes());
        }

        ConfirmacionDeOperacion usada = confirmacion.usar(momento);
        confirmaciones.guardar(usada);
        controles.guardar(control.registrarAcierto());
        auditoria.registrar(TipoDeEventoDeAcceso.OPERACION_CONFIRMADA, usuarioId, cliente);
        log.info("Operacion confirmada. usuarioId={} confirmacionId={}", usuarioId, confirmacionId);
        return new ConfirmacionEmitida(emisor.emitir(usada), usada.expiraEn());
    }

    private boolean codigoCorrecto(ConfirmacionDeOperacion confirmacion, String codigo, Instant momento) {
        if (codigo == null || !codigo.matches("^\\d{6}$")) {
            return false;
        }
        if (confirmacion.metodo() == TipoDeSegundoFactor.APP_AUTENTICADORA) {
            SegundoFactor segundoFactor = segundoFactorConfirmado(confirmacion.usuarioId());
            return totp.verificar(segundoFactor.secreto(), new CodigoTotp(codigo), momento);
        }
        try {
            return verificarCodigo.verificar(confirmacion.desafioCodigoId(), codigo);
        } catch (CodigoDesafioInvalidoException | MaximoIntentosDesafioExcedidoException
                 | DesafioExpiradoException e) {
            return false;
        }
    }

    private SegundoFactor segundoFactorConfirmado(UUID usuarioId) {
        return segundosFactores.buscarPorUsuario(usuarioId)
                .filter(SegundoFactor::estaConfirmado)
                .orElseThrow(SinSegundoFactorException::new);
    }

    private ControlDeAcceso exigirNoBloqueado(UUID usuarioId, Instant momento) {
        ControlDeAcceso control = controles.cargar(usuarioId);
        if (control.estaBloqueado(momento)) {
            throw new CuentaBloqueadaException(control.esperaRestante(momento));
        }
        return control;
    }
}
