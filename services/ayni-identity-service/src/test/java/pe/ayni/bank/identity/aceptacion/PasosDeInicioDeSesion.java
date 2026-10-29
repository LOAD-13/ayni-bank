package pe.ayni.bank.identity.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.cucumber.java.Before;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;
import pe.ayni.bank.identity.application.usecase.IniciarSesionService;
import pe.ayni.bank.identity.domain.model.Celular;
import pe.ayni.bank.identity.domain.model.CodigoTotp;
import pe.ayni.bank.identity.domain.model.ComandoDeIngreso;
import pe.ayni.bank.identity.domain.model.ComandoDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.Consentimiento;
import pe.ayni.bank.identity.domain.model.ControlDeAcceso;
import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.model.CredencialesInvalidasException;
import pe.ayni.bank.identity.domain.model.CuentaBloqueadaException;
import pe.ayni.bank.identity.domain.model.DesafioAbierto;
import pe.ayni.bank.identity.domain.model.DesafioDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.HuellaDeCliente;
import pe.ayni.bank.identity.domain.model.RefreshToken;
import pe.ayni.bank.identity.domain.model.ReutilizacionDeRefreshTokenException;
import pe.ayni.bank.identity.domain.model.SecretoTotp;
import pe.ayni.bank.identity.domain.model.SegundoFactor;
import pe.ayni.bank.identity.domain.model.SesionIniciada;
import pe.ayni.bank.identity.domain.model.TipoDeEventoDeAcceso;
import pe.ayni.bank.identity.domain.model.TokenDeRenovacion;
import pe.ayni.bank.identity.domain.model.Usuario;
import pe.ayni.bank.identity.domain.port.in.GenerarDesafioCodigoUseCase;
import pe.ayni.bank.identity.domain.port.out.EmisorDeTokensDeAccesoPort;
import pe.ayni.bank.identity.domain.port.out.GeneradorDeTotpPort;
import pe.ayni.bank.identity.domain.port.out.NotificadorDeSeguridadPort;
import pe.ayni.bank.identity.domain.port.out.PistaDeAuditoriaPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeControlDeAccesoPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeMetodoSegundoFactorPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSegundoFactorPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSesionesPort;

/** Pasos de {@code inicio-de-sesion.feature} · HU-04 · AYNI-229. */
public class PasosDeInicioDeSesion {

    private static final Instant AHORA = Instant.parse("2026-10-28T14:00:00Z");
    private static final HuellaDeCliente CLIENTE = new HuellaDeCliente("190.12.4.9", "Mozilla/5.0");
    private static final SecretoTotp SECRETO = new SecretoTotp("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");

    private DoblesEnMemoria.Usuarios usuarios;
    private DoblesEnMemoria.Cifrador cifrador;
    private RepositorioDeSegundoFactorPort segundosFactores;
    private GeneradorDeTotpPort totp;
    private ControlesEnMemoria controles;
    private SesionesEnMemoria sesiones;
    private EmisorEnMemoria emisor;
    private AuditoriaEnMemoria auditoria;
    private NotificadorSeguridadEnMemoria notificador;
    private IniciarSesionService servicio;

    private Usuario elena;
    private String contrasenaCorrecta;
    private DesafioAbierto desafio;
    private SesionIniciada sesion;
    private String refreshOriginal;
    private final List<Throwable> errores = new ArrayList<>();
    private Throwable ultimoError;

    @Before
    public void preparar() {
        usuarios = new DoblesEnMemoria.Usuarios();
        cifrador = new DoblesEnMemoria.Cifrador();
        segundosFactores = mock(RepositorioDeSegundoFactorPort.class);
        totp = mock(GeneradorDeTotpPort.class);
        controles = new ControlesEnMemoria();
        sesiones = new SesionesEnMemoria();
        emisor = new EmisorEnMemoria();
        auditoria = new AuditoriaEnMemoria();
        notificador = new NotificadorSeguridadEnMemoria();

        RepositorioDeMetodoSegundoFactorPort metodos = mock(RepositorioDeMetodoSegundoFactorPort.class);
        when(metodos.listarPorUsuario(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        servicio = new IniciarSesionService(
                usuarios, segundosFactores, controles, sesiones, cifrador, totp,
                emisor, auditoria, notificador, Clock.fixed(AHORA, ZoneOffset.UTC),
                metodos, mock(GenerarDesafioCodigoUseCase.class));

        errores.clear();
        ultimoError = null;
        desafio = null;
        sesion = null;
        refreshOriginal = null;
    }

    @Dado("que la cuenta activa de {string} tiene la contraseña {string} y segundo factor TOTP confirmado")
    public void cuentaActivaConTotp(String correo, String clave) {
        contrasenaCorrecta = clave;
        elena = Usuario.registrar(
                UUID.randomUUID(),
                new CorreoElectronico(correo),
                new Celular("987654321"),
                cifrador.cifrar(clave),
                Consentimiento.otorgar(true, AHORA, "v1"),
                AHORA).activar();
        usuarios.guardar(elena);
        cifrador.vecesQueSeCifro = 0;

        SegundoFactor confirmado = SegundoFactor.inscribir(elena.id(), SECRETO, AHORA).confirmar(AHORA);
        when(segundosFactores.buscarPorUsuario(elena.id())).thenReturn(Optional.of(confirmado));
        when(totp.verificar(SECRETO, new CodigoTotp("123456"), AHORA)).thenReturn(true);
    }

    @Cuando("Elena presenta sus credenciales con el correo {string} y la contraseña {string}")
    public void presentaCredenciales(String correo, String clave) {
        desafio = servicio.presentarCredenciales(new ComandoDeIngreso(correo, clave, CLIENTE));
    }

    @Entonces("se abre un desafío de segundo factor sin emitir aún tokens de sesión")
    public void desafioAbiertoSinTokens() {
        assertThat(desafio).isNotNull();
        assertThat(desafio.desafioId()).isNotNull();
        assertThat(desafio.requiereInscripcion()).isFalse();
        assertThat(emisor.accesosEmitidos).isZero();
    }

    @Cuando("Elena completa el desafío con el código TOTP válido {string}")
    public void completaDesafio(String codigo) {
        sesion = servicio.verificarSegundoFactor(
                new ComandoDeSegundoFactor(desafio.desafioId(), new CodigoTotp(codigo), CLIENTE));
    }

    @Entonces("el sistema emite un token de acceso JWT y un token de renovación rotativo")
    public void emiteTokens() {
        assertThat(sesion).isNotNull();
        assertThat(sesion.tokenDeAcceso()).isNotBlank();
        assertThat(sesion.tokenDeRenovacion()).isNotBlank();
    }

    @Y("la pista de auditoría registra el evento INGRESO_EXITOSO")
    public void auditaIngresoExitoso() {
        assertThat(auditoria.eventos).contains(TipoDeEventoDeAcceso.INGRESO_EXITOSO);
    }

    @Cuando("alguien intenta ingresar con el correo {string} y la contraseña incorrecta {string}")
    @Y("alguien intenta ingresar con el correo no registrado {string} y la contraseña {string}")
    public void intentoFallido(String correo, String clave) {
        Throwable error = catchThrowable(() ->
                servicio.presentarCredenciales(new ComandoDeIngreso(correo, clave, CLIENTE)));
        errores.add(error);
    }

    @Entonces("ambos intentos se rechazan con CredencialesInvalidasException sin revelar si el correo existe")
    public void ambosRechazadosIgual() {
        assertThat(errores).hasSize(2)
                .allSatisfy(e -> assertThat(e).isInstanceOf(CredencialesInvalidasException.class));
        assertThat(errores.get(0).getMessage()).isEqualTo(errores.get(1).getMessage());
    }

    @Y("en ambos casos se ejecuta la derivación Argon2id y se registra CREDENCIALES_INVALIDAS en la auditoría")
    public void derivacionYAuditoriaEnAmbos() {
        assertThat(cifrador.vecesQueSeCifro).isEqualTo(2);
        assertThat(auditoria.eventos).containsExactly(
                TipoDeEventoDeAcceso.CREDENCIALES_INVALIDAS,
                TipoDeEventoDeAcceso.CREDENCIALES_INVALIDAS);
    }

    @Cuando("se realizan {int} intentos fallidos consecutivos sobre la cuenta {string}")
    public void intentosFallidosConsecutivos(int cantidad, String correo) {
        for (int i = 0; i < cantidad; i++) {
            Throwable e = catchThrowable(() ->
                    servicio.presentarCredenciales(new ComandoDeIngreso(correo, "Incorrecta!123#", CLIENTE)));
            assertThat(e).isInstanceOf(CredencialesInvalidasException.class);
        }
    }

    @Entonces("la cuenta de {string} queda bloqueada temporalmente y recibe un único aviso de seguridad por correo")
    public void quedaBloqueadaYRecibeUnicoAviso(String correo) {
        assertThat(controles.cargar(elena.id()).estaBloqueado(AHORA)).isTrue();
        assertThat(notificador.ingresosPausados).containsExactly(correo);
    }

    @Cuando("Elena intenta ingresar con su contraseña correcta mientras dura el bloqueo")
    public void intentaDuranteBloqueo() {
        ultimoError = catchThrowable(() ->
                servicio.presentarCredenciales(new ComandoDeIngreso(elena.correo().valor(), contrasenaCorrecta, CLIENTE)));
    }

    @Entonces("el ingreso se rechaza con CuentaBloqueadaException y se registra INGRESO_BLOQUEADO")
    public void rechazadoPorBloqueo() {
        assertThat(ultimoError).isInstanceOf(CuentaBloqueadaException.class);
        assertThat(auditoria.eventos).contains(TipoDeEventoDeAcceso.INGRESO_BLOQUEADO);
    }

    @Dado("que Elena inició sesión y renovó su sesión una vez rotando su Refresh Token")
    public void inicioYRenovoUnaVez() {
        presentaCredenciales(elena.correo().valor(), contrasenaCorrecta);
        completaDesafio("123456");
        refreshOriginal = sesion.tokenDeRenovacion();
        SesionIniciada renovada = servicio.renovar(refreshOriginal, CLIENTE);
        assertThat(renovada.tokenDeRenovacion()).isNotEqualTo(refreshOriginal);
    }

    @Cuando("un atacante presenta nuevamente el Refresh Token original ya consumido")
    public void atacanteReutilizaRefreshToken() {
        ultimoError = catchThrowable(() -> servicio.renovar(refreshOriginal, CLIENTE));
    }

    @Entonces("el sistema detecta la reutilización con ReutilizacionDeRefreshTokenException")
    public void detectaReutilizacion() {
        assertThat(ultimoError).isInstanceOf(ReutilizacionDeRefreshTokenException.class);
    }

    @Y("toda la familia de sesiones de Elena queda invalidada inmediatamente")
    public void familiaInvalidada() {
        assertThat(sesiones.familiasInvalidadas).isNotEmpty();
    }

    @Y("Elena recibe un aviso de cierre de sesión por seguridad y se registra REUTILIZACION_DE_TOKEN")
    public void avisoDeCierreYAuditoria() {
        assertThat(notificador.sesionesCerradas).containsExactly(elena.correo().valor());
        assertThat(auditoria.eventos).contains(TipoDeEventoDeAcceso.REUTILIZACION_DE_TOKEN);
    }

    // ─── Dobles en memoria ──────────────────────────────────────────────────

    private static final class ControlesEnMemoria implements RepositorioDeControlDeAccesoPort {
        private final Map<UUID, ControlDeAcceso> porUsuario = new HashMap<>();

        @Override
        public ControlDeAcceso cargar(UUID usuarioId) {
            return porUsuario.getOrDefault(usuarioId, ControlDeAcceso.limpio(usuarioId));
        }

        @Override
        public void guardar(ControlDeAcceso control) {
            porUsuario.put(control.usuarioId(), control);
        }
    }

    private static final class SesionesEnMemoria implements RepositorioDeSesionesPort {
        private final Map<UUID, DesafioDeSegundoFactor> desafios = new HashMap<>();
        private final Map<String, RefreshToken> tokensPorHuella = new HashMap<>();
        private final List<UUID> familiasInvalidadas = new ArrayList<>();

        @Override
        public void guardarDesafio(DesafioDeSegundoFactor d) {
            desafios.put(d.id(), d);
        }

        @Override
        public Optional<DesafioDeSegundoFactor> buscarDesafio(UUID desafioId) {
            return Optional.ofNullable(desafios.get(desafioId));
        }

        @Override
        public void consumirDesafio(UUID desafioId) {
            desafios.remove(desafioId);
        }

        @Override
        public RefreshToken guardarToken(RefreshToken token) {
            tokensPorHuella.put(token.huella(), token);
            return token;
        }

        @Override
        public Optional<RefreshToken> buscarTokenPorHuella(String huella) {
            return Optional.ofNullable(tokensPorHuella.get(huella));
        }

        @Override
        public void invalidarFamilia(UUID familiaId) {
            familiasInvalidadas.add(familiaId);
        }

        @Override
        public void invalidarSesionesDe(UUID usuarioId) {
            tokensPorHuella.values().removeIf(t -> t.usuarioId().equals(usuarioId));
        }
    }

    private static final class EmisorEnMemoria implements EmisorDeTokensDeAccesoPort {
        private int secuencia;
        private int accesosEmitidos;

        @Override
        public String emitir(Usuario usuario, Instant momento, Instant expiraEn) {
            accesosEmitidos++;
            return "jwt-" + (++secuencia);
        }

        @Override
        public TokenDeRenovacion generarTokenDeRenovacion() {
            String claro = "rt-" + (++secuencia);
            return new TokenDeRenovacion(claro, huellaDe(claro));
        }

        @Override
        public String huellaDe(String tokenEnClaro) {
            return "sha256:" + tokenEnClaro;
        }
    }

    private static final class AuditoriaEnMemoria implements PistaDeAuditoriaPort {
        private final List<TipoDeEventoDeAcceso> eventos = new ArrayList<>();

        @Override
        public void registrar(TipoDeEventoDeAcceso tipo, UUID usuarioId, HuellaDeCliente cliente) {
            eventos.add(tipo);
        }
    }

    private static final class NotificadorSeguridadEnMemoria implements NotificadorDeSeguridadPort {
        private final List<String> ingresosPausados = new ArrayList<>();
        private final List<String> sesionesCerradas = new ArrayList<>();

        @Override
        public void avisarIngresoPausado(CorreoElectronico correo) {
            ingresosPausados.add(correo.valor());
        }

        @Override
        public void avisarSesionCerradaPorSeguridad(CorreoElectronico correo) {
            sesionesCerradas.add(correo.valor());
        }
    }
}
