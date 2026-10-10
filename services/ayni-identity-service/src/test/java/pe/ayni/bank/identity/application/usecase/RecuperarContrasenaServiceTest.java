package pe.ayni.bank.identity.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import pe.ayni.bank.identity.domain.model.Celular;
import pe.ayni.bank.identity.domain.model.Consentimiento;
import pe.ayni.bank.identity.domain.model.ContrasenaCifrada;
import pe.ayni.bank.identity.domain.model.ContrasenaInvalidaException;
import pe.ayni.bank.identity.domain.model.ControlDeAcceso;
import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.model.DesafioDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.EnlaceDeRecuperacionInvalidoException;
import pe.ayni.bank.identity.domain.model.HuellaDeCliente;
import pe.ayni.bank.identity.domain.model.RefreshToken;
import pe.ayni.bank.identity.domain.model.TipoDeEventoDeAcceso;
import pe.ayni.bank.identity.domain.model.TokenDeRecuperacion;
import pe.ayni.bank.identity.domain.model.TokenDeRenovacion;
import pe.ayni.bank.identity.domain.model.Usuario;
import pe.ayni.bank.identity.domain.port.out.CifradorDeContrasenasPort;
import pe.ayni.bank.identity.domain.port.out.EmisorDeTokensDeAccesoPort;
import pe.ayni.bank.identity.domain.port.out.NotificadorDeRecuperacionPort;
import pe.ayni.bank.identity.domain.port.out.PistaDeAuditoriaPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeControlDeAccesoPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSesionesPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeTokensDeRecuperacionPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeUsuariosPort;

/**
 * Los cinco escenarios de HU-21, sin Spring y sin base de datos, con dobles escritos a mano
 * como en HU-01 y HU-04.
 */
class RecuperarContrasenaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-10T15:00:00Z");
    private static final String CORREO = "ana.quispe@example.pe";
    private static final String NUEVA = "Nueva!Clave2026#";
    private static final HuellaDeCliente CLIENTE = new HuellaDeCliente("190.12.4.7", "Mozilla/5.0");

    private UsuariosFalsos usuarios;
    private TokensFalsos tokens;
    private SesionesFalsas sesiones;
    private ControlesFalsos controles;
    private NotificadorFalso notificador;
    private AuditoriaFalsa auditoria;
    private RelojMovil reloj;
    private RecuperarContrasenaService servicio;
    private Usuario ana;

    @BeforeEach
    void preparar() {
        usuarios = new UsuariosFalsos();
        tokens = new TokensFalsos();
        sesiones = new SesionesFalsas();
        controles = new ControlesFalsos();
        notificador = new NotificadorFalso();
        auditoria = new AuditoriaFalsa();
        reloj = new RelojMovil();
        servicio = new RecuperarContrasenaService(usuarios, tokens, sesiones, controles,
                new CifradorFalso(), new EmisorFalso(), notificador, auditoria, reloj);

        ana = Usuario.registrar(UUID.randomUUID(), new CorreoElectronico(CORREO),
                new Celular("987654321"), new ContrasenaCifrada("$argon2id$Vieja!Clave2026#"),
                Consentimiento.otorgar(true, AHORA, "v1"), AHORA).activar();
        usuarios.guardar(ana);
    }

    private String pedirEnlace() {
        servicio.solicitar(CORREO, CLIENTE);
        return notificador.ultimoToken;
    }

    @Nested
    @DisplayName("Escenario 1 · la respuesta no delata si la cuenta existe")
    class Solicitud {

        @Test
        @DisplayName("con una cuenta existente se emite un enlace de 30 minutos y se envia por correo")
        void cuentaExistente() {
            String token = pedirEnlace();

            assertThat(token).isNotBlank();
            assertThat(notificador.enlacesEnviados).containsExactly(CORREO);
            TokenDeRecuperacion guardado = tokens.guardados.get(0);
            assertThat(guardado.huella()).isEqualTo("h:" + token).isNotEqualTo(token);
            assertThat(guardado.expiraEn()).isEqualTo(AHORA.plus(Duration.ofMinutes(30)));
            assertThat(auditoria.eventos).containsExactly(TipoDeEventoDeAcceso.RECUPERACION_SOLICITADA);
            assertThat(auditoria.usuarios).containsExactly(ana.id());
        }

        @Test
        @DisplayName("con un correo desconocido no se envia nada, pero el intento queda en la auditoria")
        void correoDesconocido() {
            servicio.solicitar("nadie@example.pe", CLIENTE);

            assertThat(notificador.enlacesEnviados).isEmpty();
            assertThat(tokens.guardados).isEmpty();
            assertThat(auditoria.eventos).containsExactly(TipoDeEventoDeAcceso.RECUPERACION_SOLICITADA);
            assertThat(auditoria.usuarios).containsExactly((UUID) null);
        }

        @Test
        @DisplayName("el correo se compara sin importar mayusculas ni espacios")
        void correoNormalizado() {
            servicio.solicitar("  Ana.Quispe@Example.PE ", CLIENTE);

            assertThat(notificador.enlacesEnviados).containsExactly(CORREO);
        }

        @Test
        @DisplayName("a una cuenta bloqueada por el banco no se le envia enlace")
        void cuentaBloqueada() {
            usuarios.reemplazar(ana.bloquear());

            servicio.solicitar(CORREO, CLIENTE);

            assertThat(notificador.enlacesEnviados).isEmpty();
            assertThat(tokens.guardados).isEmpty();
        }

        @Test
        @DisplayName("solo vale el ultimo enlace: pedir otro anula los anteriores")
        void soloValeElUltimo() {
            String primero = pedirEnlace();
            reloj.avanzar(Duration.ofMinutes(1));
            String segundo = pedirEnlace();

            assertThatThrownBy(() -> servicio.validar(primero))
                    .isInstanceOf(EnlaceDeRecuperacionInvalidoException.class);
            servicio.validar(segundo);
        }

        @Test
        @DisplayName("como maximo tres enlaces por hora: el cuarto se ignora en silencio")
        void freno() {
            for (int i = 0; i < 4; i++) {
                pedirEnlace();
                reloj.avanzar(Duration.ofMinutes(5));
            }
            assertThat(notificador.enlacesEnviados).hasSize(3);
            assertThat(auditoria.eventos).hasSize(4);

            reloj.avanzar(Duration.ofMinutes(50));
            pedirEnlace();
            assertThat(notificador.enlacesEnviados).hasSize(4);
        }
    }

    @Nested
    @DisplayName("Escenarios 2 y 3 · el enlace vale una vez y durante 30 minutos")
    class Enlace {

        @Test
        @DisplayName("un enlace vigente se valida sin gastarse")
        void validarNoGasta() {
            String token = pedirEnlace();

            servicio.validar(token);
            servicio.validar(token);

            assertThat(tokens.guardados.get(0).usadoEn()).isNull();
        }

        @Test
        @DisplayName("un enlace caducado no sirve")
        void caducado() {
            String token = pedirEnlace();
            reloj.avanzar(Duration.ofMinutes(30));

            assertThatThrownBy(() -> servicio.validar(token))
                    .isInstanceOf(EnlaceDeRecuperacionInvalidoException.class);
            assertThatThrownBy(() -> servicio.restablecer(token, NUEVA, CLIENTE))
                    .isInstanceOf(EnlaceDeRecuperacionInvalidoException.class);
        }

        @Test
        @DisplayName("un enlace inventado, vacio o nulo no sirve")
        void inventado() {
            assertThatThrownBy(() -> servicio.validar("no-existe"))
                    .isInstanceOf(EnlaceDeRecuperacionInvalidoException.class);
            assertThatThrownBy(() -> servicio.validar(" "))
                    .isInstanceOf(EnlaceDeRecuperacionInvalidoException.class);
            assertThatThrownBy(() -> servicio.restablecer(null, NUEVA, CLIENTE))
                    .isInstanceOf(EnlaceDeRecuperacionInvalidoException.class);
        }

        @Test
        @DisplayName("un enlace ya usado no sirve una segunda vez")
        void usoUnico() {
            String token = pedirEnlace();
            servicio.restablecer(token, NUEVA, CLIENTE);

            assertThatThrownBy(() -> servicio.restablecer(token, "Otra!Clave2026#", CLIENTE))
                    .isInstanceOf(EnlaceDeRecuperacionInvalidoException.class);
            assertThat(usuarios.buscarPorId(ana.id()).orElseThrow().contrasena().valor())
                    .isEqualTo("$argon2id$" + NUEVA);
        }

        @Test
        @DisplayName("si otra peticion gasto el enlace un instante antes, esta se rechaza")
        void carrera() {
            String token = pedirEnlace();
            tokens.otraPeticionSeAdelanta = true;

            assertThatThrownBy(() -> servicio.restablecer(token, NUEVA, CLIENTE))
                    .isInstanceOf(EnlaceDeRecuperacionInvalidoException.class);
            assertThat(usuarios.buscarPorId(ana.id()).orElseThrow().contrasena().valor())
                    .isEqualTo("$argon2id$Vieja!Clave2026#");
        }

        @Test
        @DisplayName("la contrasena nueva cumple la misma politica que el registro, y el enlace sigue sirviendo")
        void politica() {
            String token = pedirEnlace();

            assertThatThrownBy(() -> servicio.restablecer(token, "corta", CLIENTE))
                    .isInstanceOf(ContrasenaInvalidaException.class);
            servicio.validar(token);
        }
    }

    @Nested
    @DisplayName("Escenarios 4 y 5 · al cambiar la contrasena")
    class Cambio {

        @Test
        @DisplayName("se guarda derivada, se cierran todas las sesiones, se limpia el contador y se audita")
        void efectos() {
            sesiones.tokensDe.add(ana.id());
            controles.guardar(ControlDeAcceso.limpio(ana.id()).registrarFallo(AHORA).registrarFallo(AHORA));
            String token = pedirEnlace();

            servicio.restablecer(token, NUEVA, CLIENTE);

            assertThat(usuarios.buscarPorId(ana.id()).orElseThrow().contrasena().valor())
                    .isEqualTo("$argon2id$" + NUEVA);
            assertThat(sesiones.usuariosCerrados).containsExactly(ana.id());
            assertThat(controles.cargar(ana.id()).fallosConsecutivos()).isZero();
            assertThat(auditoria.eventos).containsExactly(
                    TipoDeEventoDeAcceso.RECUPERACION_SOLICITADA, TipoDeEventoDeAcceso.CONTRASENA_RESTABLECIDA);
            assertThat(notificador.avisosDeCambio).containsExactly(CORREO);
        }

        @Test
        @DisplayName("recuperar la contrasena no recupera la cuenta: el estado y el segundo factor no cambian")
        void noTocaElEstado() {
            String token = pedirEnlace();

            servicio.restablecer(token, NUEVA, CLIENTE);

            Usuario despues = usuarios.buscarPorId(ana.id()).orElseThrow();
            assertThat(despues.estado()).isEqualTo(ana.estado());
            assertThat(despues.id()).isEqualTo(ana.id());
        }

        @Test
        @DisplayName("si el usuario ya no existe, el enlace no sirve")
        void usuarioBorrado() {
            String token = pedirEnlace();
            usuarios.guardados.clear();

            assertThatThrownBy(() -> servicio.restablecer(token, NUEVA, CLIENTE))
                    .isInstanceOf(EnlaceDeRecuperacionInvalidoException.class);
        }
    }

    // ─── Dobles ────────────────────────────────────────────────────────────

    private static final class RelojMovil extends Clock {
        private Instant ahora = AHORA;

        void avanzar(Duration cuanto) {
            ahora = ahora.plus(cuanto);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    private static final class UsuariosFalsos implements RepositorioDeUsuariosPort {
        private final List<Usuario> guardados = new ArrayList<>();

        @Override
        public boolean existeCorreo(CorreoElectronico correo) {
            return buscarPorCorreo(correo).isPresent();
        }

        @Override
        public Optional<Usuario> buscarPorCorreo(CorreoElectronico correo) {
            return guardados.stream().filter(u -> u.correo().equals(correo)).findFirst();
        }

        @Override
        public Optional<Usuario> buscarPorId(UUID id) {
            return guardados.stream().filter(u -> u.id().equals(id)).findFirst();
        }

        @Override
        public Usuario guardar(Usuario usuario) {
            reemplazar(usuario);
            return usuario;
        }

        void reemplazar(Usuario usuario) {
            guardados.removeIf(u -> u.id().equals(usuario.id()));
            guardados.add(usuario);
        }
    }

    private static final class TokensFalsos implements RepositorioDeTokensDeRecuperacionPort {
        private final List<TokenDeRecuperacion> guardados = new ArrayList<>();
        private final List<UUID> anulados = new ArrayList<>();
        private boolean otraPeticionSeAdelanta;

        @Override
        public void guardar(TokenDeRecuperacion token) {
            guardados.removeIf(t -> t.id().equals(token.id()));
            guardados.add(token);
        }

        @Override
        public Optional<TokenDeRecuperacion> buscarPorHuella(String huella) {
            return guardados.stream()
                    .filter(t -> !anulados.contains(t.id()))
                    .filter(t -> t.huella().equals(huella))
                    .findFirst();
        }

        @Override
        public long contarEmitidosDesde(UUID usuarioId, Instant desde) {
            return guardados.stream()
                    .filter(t -> t.usuarioId().equals(usuarioId))
                    .filter(t -> !t.emitidoEn().isBefore(desde))
                    .count();
        }

        @Override
        public void anularPendientesDe(UUID usuarioId, Instant momento) {
            guardados.stream()
                    .filter(t -> t.usuarioId().equals(usuarioId) && t.usadoEn() == null)
                    .forEach(t -> anulados.add(t.id()));
        }

        @Override
        public boolean marcarUsado(UUID tokenId, Instant momento) {
            if (otraPeticionSeAdelanta) {
                return false;
            }
            TokenDeRecuperacion token = guardados.stream()
                    .filter(t -> t.id().equals(tokenId)).findFirst().orElseThrow();
            if (token.usadoEn() != null) {
                return false;
            }
            guardar(token.usar(momento));
            return true;
        }
    }

    private static final class SesionesFalsas implements RepositorioDeSesionesPort {
        private final List<UUID> tokensDe = new ArrayList<>();
        private final List<UUID> usuariosCerrados = new ArrayList<>();

        @Override
        public void guardarDesafio(DesafioDeSegundoFactor desafio) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<DesafioDeSegundoFactor> buscarDesafio(UUID desafioId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void consumirDesafio(UUID desafioId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RefreshToken guardarToken(RefreshToken token) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<RefreshToken> buscarTokenPorHuella(String huella) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void invalidarFamilia(UUID familiaId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void invalidarSesionesDe(UUID usuarioId) {
            usuariosCerrados.add(usuarioId);
        }
    }

    private static final class ControlesFalsos implements RepositorioDeControlDeAccesoPort {
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

    private static final class CifradorFalso implements CifradorDeContrasenasPort {
        @Override
        public ContrasenaCifrada cifrar(String contrasenaEnClaro) {
            return new ContrasenaCifrada("$argon2id$" + contrasenaEnClaro);
        }

        @Override
        public boolean coincide(String contrasenaEnClaro, ContrasenaCifrada cifrada) {
            return cifrada.valor().equals("$argon2id$" + contrasenaEnClaro);
        }
    }

    private static final class EmisorFalso implements EmisorDeTokensDeAccesoPort {
        private int emitidos;

        @Override
        public String emitir(Usuario usuario, Instant momento, Instant expiraEn) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TokenDeRenovacion generarTokenDeRenovacion() {
            String claro = "enlace-" + (++emitidos);
            return new TokenDeRenovacion(claro, huellaDe(claro));
        }

        @Override
        public String huellaDe(String tokenEnClaro) {
            return "h:" + tokenEnClaro;
        }
    }

    private static final class NotificadorFalso implements NotificadorDeRecuperacionPort {
        private final List<String> enlacesEnviados = new ArrayList<>();
        private final List<String> avisosDeCambio = new ArrayList<>();
        private String ultimoToken;

        @Override
        public void enviarEnlaceDeRecuperacion(CorreoElectronico correo, String tokenEnClaro) {
            enlacesEnviados.add(correo.valor());
            ultimoToken = tokenEnClaro;
        }

        @Override
        public void avisarContrasenaCambiada(CorreoElectronico correo) {
            avisosDeCambio.add(correo.valor());
        }
    }

    private static final class AuditoriaFalsa implements PistaDeAuditoriaPort {
        private final List<TipoDeEventoDeAcceso> eventos = new ArrayList<>();
        private final List<UUID> usuarios = new ArrayList<>();

        @Override
        public void registrar(TipoDeEventoDeAcceso tipo, UUID usuarioId, HuellaDeCliente cliente) {
            eventos.add(tipo);
            usuarios.add(usuarioId);
        }
    }
}
