package pe.ayni.bank.identity.application.usecase;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.identity.domain.model.ComandoDeIngreso;
import pe.ayni.bank.identity.domain.model.ComandoDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.ControlDeAcceso;
import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.model.CredencialesInvalidasException;
import pe.ayni.bank.identity.domain.model.CuentaBloqueadaException;
import pe.ayni.bank.identity.domain.model.CuentaInhabilitadaException;
import pe.ayni.bank.identity.domain.model.DesafioAbierto;
import pe.ayni.bank.identity.domain.model.DesafioDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.EstadoUsuario;
import pe.ayni.bank.identity.domain.model.HuellaDeCliente;
import pe.ayni.bank.identity.domain.model.RefreshToken;
import pe.ayni.bank.identity.domain.model.ReutilizacionDeRefreshTokenException;
import pe.ayni.bank.identity.domain.model.SecretoTotp;
import pe.ayni.bank.identity.domain.model.SegundoFactor;
import pe.ayni.bank.identity.domain.model.SegundoFactorInvalidoException;
import pe.ayni.bank.identity.domain.model.SesionExpiradaException;
import pe.ayni.bank.identity.domain.model.SesionIniciada;
import pe.ayni.bank.identity.domain.model.TipoDeEventoDeAcceso;
import pe.ayni.bank.identity.domain.model.Usuario;
import pe.ayni.bank.identity.domain.port.in.IniciarSesionUseCase;
import pe.ayni.bank.identity.domain.port.out.CifradorDeContrasenasPort;
import pe.ayni.bank.identity.domain.port.out.EmisorDeTokensDeAccesoPort;
import pe.ayni.bank.identity.domain.port.out.GeneradorDeTotpPort;
import pe.ayni.bank.identity.domain.port.out.NotificadorDeSeguridadPort;
import pe.ayni.bank.identity.domain.port.out.PistaDeAuditoriaPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeControlDeAccesoPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSegundoFactorPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSesionesPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeUsuariosPort;

/**
 * Orquesta HU-04. Las reglas viven en el dominio; aqui se decide el orden, la frontera
 * transaccional y que se escribe en la pista de auditoria.
 *
 * <p><strong>Un criterio recorre todo el fichero: nada de lo que se responde permite
 * averiguar si un correo pertenece a un cliente de Ayni.</strong> Es la misma regla que
 * sostiene ADR-0008 en el registro, aplicada al ingreso, y explica varias decisiones que de
 * otro modo pareceria trabajo de mas —derivar una contrasena que no se va a comparar con
 * nada, por ejemplo.
 */
@Service
public class IniciarSesionService implements IniciarSesionUseCase {

    private static final Logger log = LoggerFactory.getLogger(IniciarSesionService.class);

    private final RepositorioDeUsuariosPort usuarios;
    private final RepositorioDeSegundoFactorPort segundosFactores;
    private final RepositorioDeControlDeAccesoPort controles;
    private final RepositorioDeSesionesPort sesiones;
    private final CifradorDeContrasenasPort cifrador;
    private final GeneradorDeTotpPort totp;
    private final EmisorDeTokensDeAccesoPort emisor;
    private final PistaDeAuditoriaPort auditoria;
    private final NotificadorDeSeguridadPort notificador;
    private final Clock reloj;

    @SuppressWarnings("java:S107") // Diez colaboradores son los diez puertos que HU-04 necesita.
    public IniciarSesionService(RepositorioDeUsuariosPort usuarios,
                                RepositorioDeSegundoFactorPort segundosFactores,
                                RepositorioDeControlDeAccesoPort controles,
                                RepositorioDeSesionesPort sesiones,
                                CifradorDeContrasenasPort cifrador,
                                GeneradorDeTotpPort totp,
                                EmisorDeTokensDeAccesoPort emisor,
                                PistaDeAuditoriaPort auditoria,
                                NotificadorDeSeguridadPort notificador,
                                Clock reloj) {
        this.usuarios = usuarios;
        this.segundosFactores = segundosFactores;
        this.controles = controles;
        this.sesiones = sesiones;
        this.cifrador = cifrador;
        this.totp = totp;
        this.emisor = emisor;
        this.auditoria = auditoria;
        this.notificador = notificador;
        this.reloj = reloj;
    }

    // ─── Paso 1 · credenciales ─────────────────────────────────────────────

    @Override
    @Transactional
    public DesafioAbierto presentarCredenciales(ComandoDeIngreso comando) {
        Instant momento = reloj.instant();
        CorreoElectronico correo = new CorreoElectronico(comando.correo());

        Optional<Usuario> encontrado = usuarios.buscarPorCorreo(correo);
        if (encontrado.isEmpty()) {
            return rechazarSinDelatarQueLaCuentaNoExiste(comando);
        }

        Usuario usuario = encontrado.get();
        ControlDeAcceso control = controles.cargar(usuario.id());

        if (control.estaBloqueado(momento)) {
            auditoria.registrar(TipoDeEventoDeAcceso.INGRESO_BLOQUEADO,
                    usuario.id(), comando.cliente());
            throw new CuentaBloqueadaException(control.esperaRestante(momento));
        }

        if (!cifrador.coincide(comando.contrasena(), usuario.contrasena())) {
            anotarFallo(usuario, control, correo, comando.cliente(), momento,
                    TipoDeEventoDeAcceso.CREDENCIALES_INVALIDAS);
            throw new CredencialesInvalidasException();
        }

        // Una cuenta inhabilitada no entra, y se comprueba aqui y no antes: hacerlo antes
        // de la contrasena diria que correos corresponden a cuentas bloqueadas sin
        // necesidad de acertar ninguna, que es otra vez un oraculo de enumeracion.
        //
        // PENDIENTE_VERIFICACION y EN_REVISION si entran, y es deliberado: quien se
        // registro y cerro el navegador tiene que poder volver para terminar su alta, y
        // quien esta en revision manual necesita ver en que quedo su solicitud. Lo que no
        // pueden es operar, y de eso se encarga `puedeOperar()`, no el ingreso.
        if (usuario.estado() == EstadoUsuario.BLOQUEADO) {
            auditoria.registrar(TipoDeEventoDeAcceso.INGRESO_BLOQUEADO,
                    usuario.id(), comando.cliente());
            throw new CuentaInhabilitadaException();
        }

        // La contrasena es correcta, pero el contador NO se limpia todavia. Si se limpiara
        // aqui, quien conoce la contrasena y no el segundo factor podria probar codigos
        // indefinidamente sin llegar a bloquearse nunca. Se limpia al completar el ingreso.
        return abrirDesafio(usuario, correo, momento);
    }

    /**
     * El correo no corresponde a ninguna cuenta.
     *
     * <p>Se deriva la contrasena igualmente. Sin esa derivacion la respuesta llegaria en
     * milisegundos, frente a las decenas que cuesta Argon2id cuando el usuario si existe, y
     * quien cronometre distingue las dos situaciones sin necesidad de leer el mensaje. Es
     * el mismo razonamiento del registro, y el motivo de que ambos endpoints tarden lo
     * mismo respondan lo que respondan.
     */
    private DesafioAbierto rechazarSinDelatarQueLaCuentaNoExiste(ComandoDeIngreso comando) {
        cifrador.cifrar(comando.contrasena());
        auditoria.registrar(TipoDeEventoDeAcceso.CREDENCIALES_INVALIDAS, null,
                comando.cliente());
        log.info("Intento de ingreso sobre un correo no registrado. ip={}",
                comando.cliente().ip());
        throw new CredencialesInvalidasException();
    }

    /**
     * Abre el desafio del segundo factor, inscribiendolo si el usuario aun no lo tiene.
     *
     * <p>La inscripcion ocurre aqui y no en el registro porque el secreto solo tiene sentido
     * cuando alguien va a usarlo: generarlo en HU-01 dejaria un secreto activo en la cuenta
     * de todo el que se registro y nunca volvio.
     */
    private DesafioAbierto abrirDesafio(Usuario usuario, CorreoElectronico correo,
                                        Instant momento) {
        DesafioDeSegundoFactor desafio = DesafioDeSegundoFactor.abrir(usuario.id(), momento);
        sesiones.guardarDesafio(desafio);

        Optional<SegundoFactor> existente = segundosFactores.buscarPorUsuario(usuario.id());

        if (existente.isPresent() && existente.get().estaConfirmado()) {
            return DesafioAbierto.paraQuienYaTieneSegundoFactor(desafio.id());
        }

        // Sin confirmar se reutiliza el secreto ya generado en lugar de crear otro: si se
        // regenerara en cada intento, quien escaneo el QR y cerro la pantalla antes de
        // teclear el codigo se encontraria con que su aplicacion guarda un secreto que ya
        // no vale.
        SegundoFactor segundoFactor = existente.orElseGet(() -> {
            SecretoTotp secreto = totp.generarSecreto();
            return segundosFactores.guardar(
                    SegundoFactor.inscribir(usuario.id(), secreto, momento));
        });

        return DesafioAbierto.conInscripcion(desafio.id(),
                totp.uriDeAprovisionamiento(segundoFactor.secreto(), correo));
    }

    // ─── Paso 2 · segundo factor ───────────────────────────────────────────

    @Override
    @Transactional
    public SesionIniciada verificarSegundoFactor(ComandoDeSegundoFactor comando) {
        Instant momento = reloj.instant();

        DesafioDeSegundoFactor desafio = sesiones.buscarDesafio(comando.desafioId())
                .filter(d -> !d.haCaducado(momento))
                .orElseThrow(SegundoFactorInvalidoException::new);

        Usuario usuario = usuarios.buscarPorId(desafio.usuarioId())
                .orElseThrow(SegundoFactorInvalidoException::new);
        ControlDeAcceso control = controles.cargar(usuario.id());

        if (control.estaBloqueado(momento)) {
            auditoria.registrar(TipoDeEventoDeAcceso.INGRESO_BLOQUEADO,
                    usuario.id(), comando.cliente());
            throw new CuentaBloqueadaException(control.esperaRestante(momento));
        }

        SegundoFactor segundoFactor = segundosFactores.buscarPorUsuario(usuario.id())
                .orElseThrow(SegundoFactorInvalidoException::new);

        if (!totp.verificar(segundoFactor.secreto(), comando.codigo(), momento)) {
            anotarFallo(usuario, control, usuario.correo(), comando.cliente(), momento,
                    TipoDeEventoDeAcceso.SEGUNDO_FACTOR_INVALIDO);
            throw new SegundoFactorInvalidoException();
        }

        // Un codigo valido demuestra que la aplicacion guardo bien el secreto. Es lo que
        // convierte la inscripcion en definitiva.
        if (!segundoFactor.estaConfirmado()) {
            segundosFactores.guardar(segundoFactor.confirmar(momento));
            auditoria.registrar(TipoDeEventoDeAcceso.SEGUNDO_FACTOR_INSCRITO,
                    usuario.id(), comando.cliente());
        }

        sesiones.consumirDesafio(desafio.id());
        controles.guardar(control.registrarAcierto());

        SesionIniciada sesion = abrirSesion(usuario, momento);
        auditoria.registrar(TipoDeEventoDeAcceso.INGRESO_EXITOSO, usuario.id(),
                comando.cliente());
        log.info("Ingreso completado. usuarioId={} ip={}",
                usuario.id(), comando.cliente().ip());

        return sesion;
    }

    // ─── Renovacion ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public SesionIniciada renovar(String tokenDeRenovacion, HuellaDeCliente cliente) {
        Instant momento = reloj.instant();

        RefreshToken token = sesiones.buscarTokenPorHuella(emisor.huellaDe(tokenDeRenovacion))
                .orElseThrow(SesionExpiradaException::new);

        var nuevo = emisor.generarTokenDeRenovacion();
        RefreshToken.Rotacion rotacion;
        try {
            rotacion = token.rotar(UUID.randomUUID(), nuevo.huella(), momento);
        } catch (ReutilizacionDeRefreshTokenException reutilizacion) {
            responderALaReutilizacion(token, cliente, reutilizacion);
            throw reutilizacion;
        }

        sesiones.guardarToken(rotacion.consumido());
        sesiones.guardarToken(rotacion.sucesor());

        Usuario usuario = usuarios.buscarPorId(token.usuarioId())
                .orElseThrow(SesionExpiradaException::new);

        auditoria.registrar(TipoDeEventoDeAcceso.SESION_RENOVADA, usuario.id(), cliente);

        Instant expiraElAcceso = momento.plus(SesionIniciada.VIGENCIA_DEL_ACCESO);
        return new SesionIniciada(
                emisor.emitir(usuario, momento, expiraElAcceso), expiraElAcceso,
                nuevo.enClaro(), rotacion.sucesor().expiraEn());
    }

    /**
     * Escenario 4: se presento un token ya consumido.
     *
     * <p>En un uso normal esto no puede pasar, porque cada token se usa exactamente una
     * vez. Que pase significa que hay dos copias en circulacion, y no se puede saber cual
     * de las dos es la del titular. Por eso no se rechaza solo esta peticion: se invalida
     * la familia entera y ambas partes tienen que volver a autenticarse. El titular puede;
     * quien robo la cookie, no, porque le falta el segundo factor.
     */
    private void responderALaReutilizacion(RefreshToken token, HuellaDeCliente cliente,
                                           ReutilizacionDeRefreshTokenException causa) {
        sesiones.invalidarFamilia(causa.familiaId());
        auditoria.registrar(TipoDeEventoDeAcceso.REUTILIZACION_DE_TOKEN,
                token.usuarioId(), cliente);

        usuarios.buscarPorId(token.usuarioId())
                .ifPresent(u -> notificador.avisarSesionCerradaPorSeguridad(u.correo()));

        log.warn("Reutilizacion de token de renovacion. usuarioId={} familia={} ip={}",
                token.usuarioId(), causa.familiaId(), cliente.ip());
    }

    // ─── Auxiliares ────────────────────────────────────────────────────────

    private SesionIniciada abrirSesion(Usuario usuario, Instant momento) {
        var nuevo = emisor.generarTokenDeRenovacion();
        RefreshToken token = sesiones.guardarToken(RefreshToken.abrirFamilia(
                UUID.randomUUID(), usuario.id(), nuevo.huella(), momento));

        Instant expiraElAcceso = momento.plus(SesionIniciada.VIGENCIA_DEL_ACCESO);
        return new SesionIniciada(
                emisor.emitir(usuario, momento, expiraElAcceso), expiraElAcceso,
                nuevo.enClaro(), token.expiraEn());
    }

    /**
     * Anota un intento fallido y, si es el que agota la tolerancia, avisa al titular.
     *
     * <p>El aviso se manda una sola vez, en el fallo que provoca el bloqueo. Mandarlo en
     * cada intento posterior convertiria la proteccion en el ataque: bastaria seguir
     * fallando para inundar de correos a la victima.
     */
    private void anotarFallo(Usuario usuario, ControlDeAcceso control,
                             CorreoElectronico correo, HuellaDeCliente cliente,
                             Instant momento, TipoDeEventoDeAcceso tipo) {
        ControlDeAcceso actualizado = control.registrarFallo(momento);
        controles.guardar(actualizado);
        auditoria.registrar(tipo, usuario.id(), cliente);

        if (actualizado.acabaDeBloquearse()) {
            notificador.avisarIngresoPausado(correo);
            auditoria.registrar(TipoDeEventoDeAcceso.INGRESO_BLOQUEADO, usuario.id(), cliente);
            log.info("Ingreso pausado por intentos fallidos. usuarioId={} ip={}",
                    usuario.id(), cliente.ip());
        }
    }
}
