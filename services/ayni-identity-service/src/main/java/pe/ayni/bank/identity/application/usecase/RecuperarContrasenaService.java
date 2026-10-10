package pe.ayni.bank.identity.application.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.identity.domain.model.ContrasenaInvalidaException;
import pe.ayni.bank.identity.domain.model.ControlDeAcceso;
import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.model.EnlaceDeRecuperacionInvalidoException;
import pe.ayni.bank.identity.domain.model.EstadoUsuario;
import pe.ayni.bank.identity.domain.model.HuellaDeCliente;
import pe.ayni.bank.identity.domain.model.RequisitoDeContrasena;
import pe.ayni.bank.identity.domain.model.TipoDeEventoDeAcceso;
import pe.ayni.bank.identity.domain.model.TokenDeRecuperacion;
import pe.ayni.bank.identity.domain.model.TokenDeRenovacion;
import pe.ayni.bank.identity.domain.model.Usuario;
import pe.ayni.bank.identity.domain.port.in.RecuperarContrasenaUseCase;
import pe.ayni.bank.identity.domain.port.out.CifradorDeContrasenasPort;
import pe.ayni.bank.identity.domain.port.out.EmisorDeTokensDeAccesoPort;
import pe.ayni.bank.identity.domain.port.out.NotificadorDeRecuperacionPort;
import pe.ayni.bank.identity.domain.port.out.PistaDeAuditoriaPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeControlDeAccesoPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSesionesPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeTokensDeRecuperacionPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeUsuariosPort;
import pe.ayni.bank.identity.domain.service.PoliticaDeContrasena;

/**
 * Orquesta HU-21. Ver ADR-0030.
 *
 * <p>Dos reglas guian todo el caso de uso. La primera, que pedir un enlace responde igual
 * exista o no la cuenta (ADR-0008). La segunda, que recuperar la contrasena
 * <strong>no es recuperar la cuenta</strong>: el ingreso sigue pidiendo el segundo factor,
 * asi que quien solo controle el correo no entra.
 */
@Service
public class RecuperarContrasenaService implements RecuperarContrasenaUseCase {

    private static final Logger log = LoggerFactory.getLogger(RecuperarContrasenaService.class);

    /**
     * Enlaces por cuenta y por hora. Sin freno, cualquiera que conozca un correo podria
     * llenarle la bandeja de enlaces; con el, el titular legitimo que no encuentra el correo
     * aun puede pedirlo dos veces mas.
     */
    static final int MAXIMO_POR_HORA = 3;

    private final RepositorioDeUsuariosPort usuarios;
    private final RepositorioDeTokensDeRecuperacionPort tokens;
    private final RepositorioDeSesionesPort sesiones;
    private final RepositorioDeControlDeAccesoPort controles;
    private final CifradorDeContrasenasPort cifrador;
    private final EmisorDeTokensDeAccesoPort generador;
    private final NotificadorDeRecuperacionPort notificador;
    private final PistaDeAuditoriaPort auditoria;
    private final Clock reloj;

    @SuppressWarnings("java:S107") // Un puerto por colaborador: es lo que pide la arquitectura hexagonal.
    public RecuperarContrasenaService(RepositorioDeUsuariosPort usuarios,
                                      RepositorioDeTokensDeRecuperacionPort tokens,
                                      RepositorioDeSesionesPort sesiones,
                                      RepositorioDeControlDeAccesoPort controles,
                                      CifradorDeContrasenasPort cifrador,
                                      EmisorDeTokensDeAccesoPort generador,
                                      NotificadorDeRecuperacionPort notificador,
                                      PistaDeAuditoriaPort auditoria,
                                      Clock reloj) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.sesiones = sesiones;
        this.controles = controles;
        this.cifrador = cifrador;
        this.generador = generador;
        this.notificador = notificador;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public void solicitar(String correo, HuellaDeCliente cliente) {
        CorreoElectronico direccion = new CorreoElectronico(correo);
        Optional<Usuario> usuario = usuarios.buscarPorCorreo(direccion);

        // Se anota en los dos casos, y no solo cuando la cuenta existe: asi el trabajo que
        // hace la peticion se parece en ambos, y una rafaga contra correos inventados
        // tambien deja rastro.
        auditoria.registrar(TipoDeEventoDeAcceso.RECUPERACION_SOLICITADA,
                usuario.map(Usuario::id).orElse(null), cliente);

        usuario.filter(this::puedeRecibirEnlace).ifPresent(this::emitirEnlace);
    }

    private boolean puedeRecibirEnlace(Usuario usuario) {
        // Un bloqueo del banco no lo levanta un correo.
        if (usuario.estado() == EstadoUsuario.BLOQUEADO) {
            return false;
        }
        Instant haceUnaHora = reloj.instant().minus(Duration.ofHours(1));
        if (tokens.contarEmitidosDesde(usuario.id(), haceUnaHora) >= MAXIMO_POR_HORA) {
            log.info("Recuperacion ignorada por el freno horario. usuarioId={}", usuario.id());
            return false;
        }
        return true;
    }

    private void emitirEnlace(Usuario usuario) {
        Instant momento = reloj.instant();
        // Solo vale el ultimo enlace que llego al correo.
        tokens.anularPendientesDe(usuario.id(), momento);

        // Mismas propiedades que el token de renovacion: 256 bits aleatorios, alfabeto
        // seguro para URL y huella SHA-256. Reutilizar el generador evita una segunda
        // fuente de aleatoriedad que revisar.
        TokenDeRenovacion nuevo = generador.generarTokenDeRenovacion();
        tokens.guardar(TokenDeRecuperacion.emitir(UUID.randomUUID(), usuario.id(), nuevo.huella(), momento));
        notificador.enviarEnlaceDeRecuperacion(usuario.correo(), nuevo.enClaro());

        log.info("Enlace de recuperacion emitido. usuarioId={}", usuario.id());
    }

    @Override
    @Transactional(readOnly = true)
    public void validar(String tokenEnClaro) {
        buscarVigente(tokenEnClaro);
    }

    @Override
    @Transactional
    public void restablecer(String tokenEnClaro, String contrasenaNueva, HuellaDeCliente cliente) {
        TokenDeRecuperacion token = buscarVigente(tokenEnClaro);

        // La politica va antes de gastar el enlace: equivocarse con un simbolo no deberia
        // obligar a pedir otro correo.
        List<RequisitoDeContrasena> incumplidos = PoliticaDeContrasena.evaluar(contrasenaNueva);
        if (!incumplidos.isEmpty()) {
            throw new ContrasenaInvalidaException(incumplidos);
        }

        Instant momento = reloj.instant();
        if (!tokens.marcarUsado(token.id(), momento)) {
            throw new EnlaceDeRecuperacionInvalidoException();
        }
        Usuario usuario = usuarios.buscarPorId(token.usuarioId())
                .orElseThrow(EnlaceDeRecuperacionInvalidoException::new);

        usuarios.guardar(usuario.cambiarContrasena(cifrador.cifrar(contrasenaNueva)));
        tokens.anularPendientesDe(usuario.id(), momento);
        // Quien tuviera una sesion abierta con la contrasena vieja la pierde. El token de
        // acceso ya emitido vive como mucho quince minutos mas: ver ADR-0030.
        sesiones.invalidarSesionesDe(usuario.id());
        controles.guardar(ControlDeAcceso.limpio(usuario.id()));
        auditoria.registrar(TipoDeEventoDeAcceso.CONTRASENA_RESTABLECIDA, usuario.id(), cliente);
        notificador.avisarContrasenaCambiada(usuario.correo());

        log.info("Contrasena restablecida. usuarioId={}", usuario.id());
    }

    private TokenDeRecuperacion buscarVigente(String tokenEnClaro) {
        if (tokenEnClaro == null || tokenEnClaro.isBlank()) {
            throw new EnlaceDeRecuperacionInvalidoException();
        }
        return tokens.buscarPorHuella(generador.huellaDe(tokenEnClaro))
                .filter(token -> token.estaVigente(reloj.instant()))
                .orElseThrow(EnlaceDeRecuperacionInvalidoException::new);
    }
}
