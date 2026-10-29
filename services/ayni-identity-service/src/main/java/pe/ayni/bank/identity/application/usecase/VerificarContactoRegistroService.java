package pe.ayni.bank.identity.application.usecase;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.identity.domain.model.CodigoDesafioInvalidoException;
import pe.ayni.bank.identity.domain.model.CodigoTotp;
import pe.ayni.bank.identity.domain.model.DesafioExpiradoException;
import pe.ayni.bank.identity.domain.model.DesafioPorCodigo;
import pe.ayni.bank.identity.domain.model.MaximoIntentosDesafioExcedidoException;
import pe.ayni.bank.identity.domain.model.MetodoDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.SecretoTotp;
import pe.ayni.bank.identity.domain.model.SegundoFactor;
import pe.ayni.bank.identity.domain.model.SolicitudVerificacionContacto;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;
import pe.ayni.bank.identity.domain.port.in.VerificarContactoRegistroUseCase;
import pe.ayni.bank.identity.domain.port.out.GeneradorDeTotpPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeDesafioPorCodigoPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeMetodoSegundoFactorPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSegundoFactorPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSolicitudesPort;

/**
 * Servicio de aplicación para verificar el segundo factor / contacto inicial durante el registro (HU-22 / AYNI-130).
 */
@Service
public class VerificarContactoRegistroService implements VerificarContactoRegistroUseCase {

    private final RepositorioDeDesafioPorCodigoPort repositorioDesafio;
    private final RepositorioDeMetodoSegundoFactorPort repositorioMetodos;
    private final Clock reloj;
    private final GeneradorDeTotpPort generadorTotp;
    private final RepositorioDeSegundoFactorPort segundosFactores;
    private final RepositorioDeSolicitudesPort solicitudes;

    public VerificarContactoRegistroService(RepositorioDeDesafioPorCodigoPort repositorioDesafio,
                                             RepositorioDeMetodoSegundoFactorPort repositorioMetodos) {
        this(repositorioDesafio, repositorioMetodos, Clock.systemUTC(), null, null, null);
    }

    public VerificarContactoRegistroService(RepositorioDeDesafioPorCodigoPort repositorioDesafio,
                                             RepositorioDeMetodoSegundoFactorPort repositorioMetodos,
                                             Clock reloj) {
        this(repositorioDesafio, repositorioMetodos, reloj, null, null, null);
    }

    @Autowired
    public VerificarContactoRegistroService(RepositorioDeDesafioPorCodigoPort repositorioDesafio,
                                             RepositorioDeMetodoSegundoFactorPort repositorioMetodos,
                                             Clock reloj,
                                             @Autowired(required = false) GeneradorDeTotpPort generadorTotp,
                                             @Autowired(required = false) RepositorioDeSegundoFactorPort segundosFactores,
                                             @Autowired(required = false) RepositorioDeSolicitudesPort solicitudes) {
        this.repositorioDesafio = repositorioDesafio;
        this.repositorioMetodos = repositorioMetodos;
        this.reloj = reloj;
        this.generadorTotp = generadorTotp;
        this.segundosFactores = segundosFactores;
        this.solicitudes = solicitudes;
    }

    /** El intento fallido se guarda aunque despues se lance la excepcion: si no, el codigo admite intentos ilimitados. */
    @Override
    @Transactional(noRollbackFor = {CodigoDesafioInvalidoException.class,
            MaximoIntentosDesafioExcedidoException.class})
    public boolean verificarContacto(SolicitudVerificacionContacto solicitud) {
        UUID usuarioId = solicitudes != null
                ? solicitudes.titularDe(solicitud.usuarioId()).orElse(solicitud.usuarioId())
                : solicitud.usuarioId();
        Instant ahora = reloj.instant();

        if (solicitud.tipoContacto() == TipoDeSegundoFactor.APP_AUTENTICADORA) {
            return verificarTotpEnRegistro(usuarioId, solicitud.codigo(), ahora);
        }

        DesafioPorCodigo desafio = repositorioDesafio.buscarUltimoPendiente(usuarioId, solicitud.tipoContacto())
                .filter(d -> !d.estaVerificado())
                .orElseThrow(CodigoDesafioInvalidoException::new);

        if (desafio.estaExpirado(ahora)) {
            throw new DesafioExpiradoException();
        }

        if (desafio.alcanzoMaximoIntentos()) {
            throw new MaximoIntentosDesafioExcedidoException();
        }

        String hashIngresado = GenerarDesafioCodigoService.calcularHashSha256(usuarioId, solicitud.codigo());

        boolean coincide = MessageDigest.isEqual(
                hashIngresado.getBytes(StandardCharsets.UTF_8),
                desafio.hashCodigo().getBytes(StandardCharsets.UTF_8));

        if (!coincide) {
            DesafioPorCodigo fallido = desafio.registrarIntentoFallido();
            repositorioDesafio.guardar(fallido);
            if (fallido.alcanzoMaximoIntentos()) {
                throw new MaximoIntentosDesafioExcedidoException();
            }
            throw new CodigoDesafioInvalidoException();
        }

        repositorioDesafio.guardar(desafio.marcarVerificado(ahora));

        MetodoDeSegundoFactor metodo = repositorioMetodos.buscarPorUsuarioYTipo(usuarioId, solicitud.tipoContacto())
                .orElseGet(() -> MetodoDeSegundoFactor.inscribir(
                        UUID.randomUUID(), usuarioId, solicitud.tipoContacto(), null, ahora));

        repositorioMetodos.guardar(metodo.confirmar(ahora));
        return true;
    }

    private boolean verificarTotpEnRegistro(UUID usuarioId, String codigo, Instant ahora) {
        if (generadorTotp == null || codigo == null || !codigo.matches("^\\d{6}$")) {
            throw new CodigoDesafioInvalidoException();
        }

        MetodoDeSegundoFactor metodo = repositorioMetodos
                .buscarPorUsuarioYTipo(usuarioId, TipoDeSegundoFactor.APP_AUTENTICADORA)
                .filter(m -> m.secreto() != null)
                .orElseThrow(CodigoDesafioInvalidoException::new);

        SecretoTotp secreto = new SecretoTotp(metodo.secreto());
        if (!generadorTotp.verificar(secreto, new CodigoTotp(codigo), ahora)) {
            throw new CodigoDesafioInvalidoException();
        }

        repositorioMetodos.guardar(metodo.confirmar(ahora));
        if (segundosFactores != null) {
            SegundoFactor sf = segundosFactores.buscarPorUsuario(usuarioId)
                    .orElseGet(() -> SegundoFactor.inscribir(usuarioId, secreto, metodo.creadoEn()));
            segundosFactores.guardar(sf.confirmar(ahora));
        }
        return true;
    }
}

