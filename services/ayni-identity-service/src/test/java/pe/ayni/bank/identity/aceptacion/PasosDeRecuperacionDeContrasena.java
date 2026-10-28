package pe.ayni.bank.identity.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
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
import pe.ayni.bank.identity.application.usecase.RecuperarContrasenaService;
import pe.ayni.bank.identity.domain.model.Celular;
import pe.ayni.bank.identity.domain.model.ComandoDeIngreso;
import pe.ayni.bank.identity.domain.model.Consentimiento;
import pe.ayni.bank.identity.domain.model.ContrasenaInvalidaException;
import pe.ayni.bank.identity.domain.model.ControlDeAcceso;
import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.model.DesafioAbierto;
import pe.ayni.bank.identity.domain.model.DesafioDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.EnlaceDeRecuperacionInvalidoException;
import pe.ayni.bank.identity.domain.model.HuellaDeCliente;
import pe.ayni.bank.identity.domain.model.RefreshToken;
import pe.ayni.bank.identity.domain.model.SecretoTotp;
import pe.ayni.bank.identity.domain.model.SegundoFactor;
import pe.ayni.bank.identity.domain.model.TipoDeEventoDeAcceso;
import pe.ayni.bank.identity.domain.model.TokenDeRecuperacion;
import pe.ayni.bank.identity.domain.model.TokenDeRenovacion;
import pe.ayni.bank.identity.domain.model.Usuario;
import pe.ayni.bank.identity.domain.port.in.GenerarDesafioCodigoUseCase;
import pe.ayni.bank.identity.domain.port.out.EmisorDeTokensDeAccesoPort;
import pe.ayni.bank.identity.domain.port.out.GeneradorDeTotpPort;
import pe.ayni.bank.identity.domain.port.out.NotificadorDeRecuperacionPort;
import pe.ayni.bank.identity.domain.port.out.NotificadorDeSeguridadPort;
import pe.ayni.bank.identity.domain.port.out.PistaDeAuditoriaPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeControlDeAccesoPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeMetodoSegundoFactorPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSegundoFactorPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSesionesPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeTokensDeRecuperacionPort;

/** Pasos de {@code recuperacion-de-contrasena.feature} · HU-21. */
public class PasosDeRecuperacionDeContrasena {

    private static final HuellaDeCliente CLIENTE = new HuellaDeCliente("190.12.4.7", "Mozilla/5.0");

    private RelojMovil reloj;
    private DoblesEnMemoria.Usuarios usuarios;
    private DoblesEnMemoria.Cifrador cifrador;
    private Tokens tokens;
    private Sesiones sesiones;
    private Controles controles;
    private Notificador notificador;
    private Auditoria auditoria;
    private Emisor emisor;
    private RepositorioDeSegundoFactorPort segundosFactores;
    private RecuperarContrasenaService servicio;

    private Usuario ana;
    private final List<Throwable> fallos = new ArrayList<>();
    private Throwable ultimoFallo;
    private DesafioAbierto desafio;

    @Before
    public void prepararEscenario() {
        usuarios = new DoblesEnMemoria.Usuarios();
        cifrador = new DoblesEnMemoria.Cifrador();
        tokens = new Tokens();
        sesiones = new Sesiones();
        controles = new Controles();
        notificador = new Notificador();
        auditoria = new Auditoria();
        emisor = new Emisor();
        segundosFactores = mock(RepositorioDeSegundoFactorPort.class);
        fallos.clear();
        ultimoFallo = null;
        desafio = null;
    }

    // ─── Antecedentes ──────────────────────────────────────────────────────

    @Dado("que el reloj marca el {int} de octubre de {int} a las {int}:{int}")
    public void queElRelojMarca(int dia, int anio, int hora, int minuto) {
        reloj = new RelojMovil(Instant.parse("%d-10-%02dT%02d:%02d:00Z".formatted(anio, dia, hora, minuto)));
        servicio = new RecuperarContrasenaService(usuarios, tokens, sesiones, controles, cifrador,
                emisor, notificador, auditoria, reloj);
    }

    @Dado("que existe la cuenta activa {string} con segundo factor confirmado")
    public void queExisteLaCuentaActiva(String correo) {
        ana = Usuario.registrar(UUID.randomUUID(), new CorreoElectronico(correo), new Celular("987654321"),
                cifrador.cifrar("Vieja!Clave2026#"), Consentimiento.otorgar(true, reloj.instant(), "v1"),
                reloj.instant()).activar();
        usuarios.guardar(ana);
        SegundoFactor confirmado = SegundoFactor.inscribir(ana.id(),
                new SecretoTotp("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"), reloj.instant()).confirmar(reloj.instant());
        when(segundosFactores.buscarPorUsuario(ana.id())).thenReturn(Optional.of(confirmado));
    }

    // ─── Acciones ──────────────────────────────────────────────────────────

    @Cuando("pido recuperar la contraseña de {string}")
    @Dado("que pedí recuperar la contraseña de {string}")
    public void pidoRecuperar(String correo) {
        intentar(() -> servicio.solicitar(correo, CLIENTE));
    }

    @Cuando("pasan {int} minutos")
    public void pasanMinutos(int minutos) {
        reloj.avanzar(Duration.ofMinutes(minutos));
    }

    @Cuando("fijo la contraseña nueva {string} con el enlace recibido")
    @Dado("que fijé la contraseña nueva {string} con el enlace recibido")
    public void fijoLaContrasena(String nueva) {
        intentar(() -> servicio.restablecer(notificador.ultimoEnlace, nueva, CLIENTE));
    }

    @Cuando("abro el enlace recibido")
    public void abroElEnlace() {
        intentar(() -> servicio.validar(notificador.ultimoEnlace));
    }

    @Dado("que tengo dos sesiones abiertas")
    public void queTengoDosSesiones() {
        sesiones.abiertas.put(UUID.randomUUID(), ana.id());
        sesiones.abiertas.put(UUID.randomUUID(), ana.id());
    }

    @Cuando("ingreso con {string} y la contraseña {string}")
    public void ingresoCon(String correo, String contrasena) {
        IniciarSesionService ingreso = new IniciarSesionService(usuarios, segundosFactores, controles, sesiones,
                cifrador, mock(GeneradorDeTotpPort.class), emisor, auditoria,
                mock(NotificadorDeSeguridadPort.class), reloj,
                mock(RepositorioDeMetodoSegundoFactorPort.class), mock(GenerarDesafioCodigoUseCase.class));
        desafio = ingreso.presentarCredenciales(new ComandoDeIngreso(correo, contrasena, CLIENTE));
    }

    private void intentar(Runnable accion) {
        try {
            accion.run();
            ultimoFallo = null;
        } catch (RuntimeException e) {
            ultimoFallo = e;
            fallos.add(e);
        }
    }

    // ─── Resultados ────────────────────────────────────────────────────────

    @Entonces("las dos peticiones terminan igual, sin error")
    public void terminanIgual() {
        assertThat(fallos).isEmpty();
    }

    @Y("solo {string} recibe un enlace de recuperación")
    public void soloRecibeEnlace(String correo) {
        assertThat(notificador.enlaces).containsExactly(correo);
    }

    @Y("las dos peticiones quedan en la pista de auditoría")
    public void quedanEnLaAuditoria() {
        assertThat(auditoria.eventos).containsExactly(
                TipoDeEventoDeAcceso.RECUPERACION_SOLICITADA, TipoDeEventoDeAcceso.RECUPERACION_SOLICITADA);
    }

    @Entonces("se rechaza por no cumplir la política de contraseñas")
    public void seRechazaPorPolitica() {
        assertThat(ultimoFallo).isInstanceOf(ContrasenaInvalidaException.class);
    }

    @Entonces("mi contraseña queda cambiada")
    public void quedaCambiada() {
        assertThat(ultimoFallo).isNull();
        Usuario actual = usuarios.buscarPorId(ana.id()).orElseThrow();
        assertThat(cifrador.coincide("Nueva!Clave2026#", actual.contrasena())).isTrue();
    }

    @Entonces("se explica que el enlace ya no es válido")
    public void enlaceNoValido() {
        assertThat(ultimoFallo).isInstanceOf(EnlaceDeRecuperacionInvalidoException.class)
                .hasMessageNotContaining("ana");
    }

    @Entonces("todas mis sesiones quedan invalidadas")
    public void sesionesInvalidadas() {
        assertThat(sesiones.abiertas).isEmpty();
    }

    @Y("el cambio queda en la pista de auditoría")
    public void cambioAuditado() {
        assertThat(auditoria.eventos).contains(TipoDeEventoDeAcceso.CONTRASENA_RESTABLECIDA);
    }

    @Y("recibo un aviso de que mi contraseña cambió")
    public void avisoDeCambio() {
        assertThat(notificador.avisos).containsExactly(ana.correo().valor());
    }

    @Entonces("el ingreso me pide el segundo factor antes de abrir la sesión")
    public void pideSegundoFactor() {
        assertThat(desafio).isNotNull();
        assertThat(desafio.desafioId()).isNotNull();
        assertThat(desafio.requiereInscripcion()).isFalse();
        assertThat(emisor.accesosEmitidos).isZero();
    }

    // ─── Dobles de HU-21 ───────────────────────────────────────────────────

    private static final class RelojMovil extends Clock {
        private Instant ahora;

        RelojMovil(Instant inicio) {
            ahora = inicio;
        }

        void avanzar(Duration cuanto) {
            ahora = ahora.plus(cuanto);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    private static final class Tokens implements RepositorioDeTokensDeRecuperacionPort {
        private final Map<UUID, TokenDeRecuperacion> guardados = new HashMap<>();
        private final List<UUID> anulados = new ArrayList<>();

        @Override
        public void guardar(TokenDeRecuperacion token) {
            guardados.put(token.id(), token);
        }

        @Override
        public Optional<TokenDeRecuperacion> buscarPorHuella(String huella) {
            return guardados.values().stream()
                    .filter(t -> !anulados.contains(t.id()) && t.huella().equals(huella)).findFirst();
        }

        @Override
        public long contarEmitidosDesde(UUID usuarioId, Instant desde) {
            return guardados.values().stream()
                    .filter(t -> t.usuarioId().equals(usuarioId) && !t.emitidoEn().isBefore(desde)).count();
        }

        @Override
        public void anularPendientesDe(UUID usuarioId, Instant momento) {
            guardados.values().stream()
                    .filter(t -> t.usuarioId().equals(usuarioId) && t.usadoEn() == null)
                    .forEach(t -> anulados.add(t.id()));
        }

        @Override
        public boolean marcarUsado(UUID tokenId, Instant momento) {
            TokenDeRecuperacion token = guardados.get(tokenId);
            if (token == null || token.usadoEn() != null || anulados.contains(tokenId)) {
                return false;
            }
            guardados.put(tokenId, token.usar(momento));
            return true;
        }
    }

    /** Sesiones abiertas por familia; cerrar las del usuario las borra todas. */
    private static final class Sesiones implements RepositorioDeSesionesPort {
        private final Map<UUID, UUID> abiertas = new HashMap<>();
        private final Map<UUID, DesafioDeSegundoFactor> desafios = new HashMap<>();

        @Override
        public void guardarDesafio(DesafioDeSegundoFactor desafio) {
            desafios.put(desafio.id(), desafio);
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
            abiertas.put(token.familiaId(), token.usuarioId());
            return token;
        }

        @Override
        public Optional<RefreshToken> buscarTokenPorHuella(String huella) {
            return Optional.empty();
        }

        @Override
        public void invalidarFamilia(UUID familiaId) {
            abiertas.remove(familiaId);
        }

        @Override
        public void invalidarSesionesDe(UUID usuarioId) {
            abiertas.values().removeIf(usuarioId::equals);
        }
    }

    private static final class Controles implements RepositorioDeControlDeAccesoPort {
        private final Map<UUID, ControlDeAcceso> guardados = new HashMap<>();

        @Override
        public ControlDeAcceso cargar(UUID usuarioId) {
            return guardados.getOrDefault(usuarioId, ControlDeAcceso.limpio(usuarioId));
        }

        @Override
        public void guardar(ControlDeAcceso control) {
            guardados.put(control.usuarioId(), control);
        }
    }

    private static final class Notificador implements NotificadorDeRecuperacionPort {
        private final List<String> enlaces = new ArrayList<>();
        private final List<String> avisos = new ArrayList<>();
        private String ultimoEnlace;

        @Override
        public void enviarEnlaceDeRecuperacion(CorreoElectronico correo, String tokenEnClaro) {
            enlaces.add(correo.valor());
            ultimoEnlace = tokenEnClaro;
        }

        @Override
        public void avisarContrasenaCambiada(CorreoElectronico correo) {
            avisos.add(correo.valor());
        }
    }

    private static final class Auditoria implements PistaDeAuditoriaPort {
        private final List<TipoDeEventoDeAcceso> eventos = new ArrayList<>();

        @Override
        public void registrar(TipoDeEventoDeAcceso tipo, UUID usuarioId, HuellaDeCliente cliente) {
            eventos.add(tipo);
        }
    }

    private static final class Emisor implements EmisorDeTokensDeAccesoPort {
        private int generados;
        private int accesosEmitidos;

        @Override
        public String emitir(Usuario usuario, Instant momento, Instant expiraEn) {
            accesosEmitidos++;
            return "jwt";
        }

        @Override
        public TokenDeRenovacion generarTokenDeRenovacion() {
            String claro = "enlace-" + (++generados);
            return new TokenDeRenovacion(claro, huellaDe(claro));
        }

        @Override
        public String huellaDe(String tokenEnClaro) {
            return "sha256:" + tokenEnClaro;
        }
    }
}
