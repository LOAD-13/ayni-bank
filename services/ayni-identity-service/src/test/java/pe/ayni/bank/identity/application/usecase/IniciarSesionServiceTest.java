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
import pe.ayni.bank.identity.domain.model.CodigoTotp;
import pe.ayni.bank.identity.domain.model.ComandoDeIngreso;
import pe.ayni.bank.identity.domain.model.ComandoDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.Consentimiento;
import pe.ayni.bank.identity.domain.model.ContrasenaCifrada;
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
import pe.ayni.bank.identity.domain.model.TokenDeRenovacion;
import pe.ayni.bank.identity.domain.model.Usuario;
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
 * Los cuatro escenarios de aceptación de HU-04, sin Spring y sin base de datos.
 *
 * <p>Igual que en HU-01, con dobles escritos a mano: aquí importa poder afirmar cuántas
 * veces se derivó una contraseña, qué quedó en la pista de auditoría y si se llegó a avisar
 * al titular, y eso con una biblioteca de simulación se convierte en una cadena de
 * verificaciones que describen la implementación en vez del comportamiento.
 */
class IniciarSesionServiceTest {

    private static final Instant AHORA = Instant.parse("2026-08-30T10:15:30Z");
    private static final String CORREO = "ana.quispe@example.pe";
    private static final String CONTRASENA = "Cont!rasena2026#";
    private static final String CODIGO_BUENO = "123456";
    private static final SecretoTotp SECRETO =
            new SecretoTotp("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
    private static final HuellaDeCliente CLIENTE =
            new HuellaDeCliente("190.12.4.7", "Mozilla/5.0");

    private UsuariosFalsos usuarios;
    private SegundosFactoresFalsos segundosFactores;
    private ControlesFalsos controles;
    private SesionesFalsas sesiones;
    private CifradorFalso cifrador;
    private TotpFalso totp;
    private EmisorFalso emisor;
    private AuditoriaFalsa auditoria;
    private NotificadorFalso notificador;
    private IniciarSesionService servicio;
    private Usuario ana;

    @BeforeEach
    void prepararEscenario() {
        usuarios = new UsuariosFalsos();
        segundosFactores = new SegundosFactoresFalsos();
        controles = new ControlesFalsos();
        sesiones = new SesionesFalsas();
        cifrador = new CifradorFalso();
        totp = new TotpFalso();
        emisor = new EmisorFalso();
        auditoria = new AuditoriaFalsa();
        notificador = new NotificadorFalso();

        servicio = new IniciarSesionService(usuarios, segundosFactores, controles, sesiones,
                cifrador, totp, emisor, auditoria, notificador,
                Clock.fixed(AHORA, ZoneOffset.UTC));

        ana = Usuario.registrar(UUID.randomUUID(), new CorreoElectronico(CORREO),
                new Celular("987654321"), new ContrasenaCifrada("$argon2id$" + CONTRASENA),
                Consentimiento.otorgar(true, AHORA, "v1"), AHORA);
        usuarios.guardar(ana);
    }

    private DesafioAbierto ingresar() {
        return servicio.presentarCredenciales(
                new ComandoDeIngreso(CORREO, CONTRASENA, CLIENTE));
    }

    private SesionIniciada completarIngreso() {
        DesafioAbierto desafio = ingresar();
        return servicio.verificarSegundoFactor(new ComandoDeSegundoFactor(
                desafio.desafioId(), new CodigoTotp(CODIGO_BUENO), CLIENTE));
    }

    @Nested
    @DisplayName("Escenario 1 · Inicio de sesión exitoso")
    class IngresoExitoso {

        @BeforeEach
        void yaTieneSegundoFactor() {
            segundosFactores.guardar(SegundoFactor
                    .inscribir(ana.id(), SECRETO, AHORA).confirmar(AHORA));
        }

        @Test
        @DisplayName("el primer paso no entrega ningún token: solo el desafío")
        void elPrimerPasoNoAbreSesion() {
            DesafioAbierto desafio = ingresar();

            assertThat(desafio.desafioId()).isNotNull();
            assertThat(desafio.requiereInscripcion()).isFalse();
            assertThat(sesiones.tokens).isEmpty();
        }

        @Test
        void emiteUnTokenDeQuinceMinutosYUnoDeRenovacionDeSieteDias() {
            SesionIniciada sesion = completarIngreso();

            assertThat(sesion.elAccesoExpiraEn())
                    .isEqualTo(AHORA.plus(Duration.ofMinutes(15)));
            assertThat(sesion.laRenovacionExpiraEn())
                    .isEqualTo(AHORA.plus(Duration.ofDays(7)));
        }

        @Test
        @DisplayName("del token de renovación solo se guarda su huella")
        void noPersisteElTokenEnClaro() {
            SesionIniciada sesion = completarIngreso();

            assertThat(sesiones.tokens).hasSize(1);
            assertThat(sesiones.tokens.get(0).huella())
                    .isNotEqualTo(sesion.tokenDeRenovacion());
        }

        @Test
        void registraElAccesoEnLaPistaDeAuditoria() {
            completarIngreso();

            assertThat(auditoria.eventos).contains(TipoDeEventoDeAcceso.INGRESO_EXITOSO);
            assertThat(auditoria.ips).contains("190.12.4.7");
        }

        @Test
        @DisplayName("el desafío se canjea una sola vez")
        void elDesafioNoSeReutiliza() {
            DesafioAbierto desafio = ingresar();
            servicio.verificarSegundoFactor(new ComandoDeSegundoFactor(
                    desafio.desafioId(), new CodigoTotp(CODIGO_BUENO), CLIENTE));

            assertThatThrownBy(() -> servicio.verificarSegundoFactor(
                    new ComandoDeSegundoFactor(desafio.desafioId(),
                            new CodigoTotp(CODIGO_BUENO), CLIENTE)))
                    .isInstanceOf(SegundoFactorInvalidoException.class);
        }

        @Test
        void unDesafioCaducadoNoSirve() {
            DesafioAbierto desafio = ingresar();
            sesiones.envejecerDesafios();

            assertThatThrownBy(() -> servicio.verificarSegundoFactor(
                    new ComandoDeSegundoFactor(desafio.desafioId(),
                            new CodigoTotp(CODIGO_BUENO), CLIENTE)))
                    .isInstanceOf(SegundoFactorInvalidoException.class);
        }
    }

    @Nested
    @DisplayName("Escenario 2 · Credenciales incorrectas")
    class CredencialesIncorrectas {

        @Test
        @DisplayName("el correo desconocido y la contraseña mala dan el mismo error")
        void laRespuestaEsIndistinguible() {
            // Si se distinguieran, el formulario de ingreso sería un comprobador de cuentas.
            Throwable porCorreo = org.assertj.core.api.Assertions.catchThrowable(() ->
                    servicio.presentarCredenciales(
                            new ComandoDeIngreso("nadie@example.pe", CONTRASENA, CLIENTE)));
            Throwable porContrasena = org.assertj.core.api.Assertions.catchThrowable(() ->
                    servicio.presentarCredenciales(
                            new ComandoDeIngreso(CORREO, "otra-cosa", CLIENTE)));

            assertThat(porCorreo).isInstanceOf(CredencialesInvalidasException.class);
            assertThat(porContrasena).isInstanceOf(CredencialesInvalidasException.class);
            assertThat(porCorreo).hasMessage(porContrasena.getMessage());
        }

        @Test
        @DisplayName("con un correo desconocido también se deriva la contraseña")
        void derivaLaContrasenaAunqueElCorreoNoExista() {
            // Sin esta derivación la respuesta llegaría en milisegundos frente a las decenas
            // que cuesta Argon2id, y quien cronometre distingue las dos situaciones sin
            // necesidad de leer el mensaje.
            assertThatThrownBy(() -> servicio.presentarCredenciales(
                    new ComandoDeIngreso("nadie@example.pe", CONTRASENA, CLIENTE)))
                    .isInstanceOf(CredencialesInvalidasException.class);

            assertThat(cifrador.vecesQueSeCifro).isEqualTo(1);
        }

        @Test
        void noEmiteNingunToken() {
            assertThatThrownBy(() -> servicio.presentarCredenciales(
                    new ComandoDeIngreso(CORREO, "otra-cosa", CLIENTE)))
                    .isInstanceOf(CredencialesInvalidasException.class);

            assertThat(sesiones.tokens).isEmpty();
        }

        @Test
        void incrementaElContadorDeFallos() {
            assertThatThrownBy(() -> servicio.presentarCredenciales(
                    new ComandoDeIngreso(CORREO, "otra-cosa", CLIENTE)))
                    .isInstanceOf(CredencialesInvalidasException.class);

            assertThat(controles.cargar(ana.id()).fallosConsecutivos()).isEqualTo(1);
        }

        @Test
        @DisplayName("el intento sobre un correo inexistente también se audita")
        void auditaTambienLoQueNoExiste() {
            assertThatThrownBy(() -> servicio.presentarCredenciales(
                    new ComandoDeIngreso("nadie@example.pe", CONTRASENA, CLIENTE)))
                    .isInstanceOf(CredencialesInvalidasException.class);

            assertThat(auditoria.eventos)
                    .containsExactly(TipoDeEventoDeAcceso.CREDENCIALES_INVALIDAS);
        }
    }

    @Nested
    @DisplayName("Escenario 3 · Bloqueo por intentos repetidos")
    class BloqueoPorIntentos {

        /**
         * Falla a propósito unas cuantas veces.
         *
         * <p>Se atrapan las dos excepciones porque a partir del sexto intento el servicio ya
         * no llega a comprobar la contraseña: rechaza antes por bloqueo. Eso también
         * significa que los intentos hechos <em>durante</em> el bloqueo no suman al contador,
         * que es lo correcto: si sumaran, insistir a ciegas alargaría la espera sola.
         */
        private void fallar(int veces) {
            for (int i = 0; i < veces; i++) {
                try {
                    servicio.presentarCredenciales(
                            new ComandoDeIngreso(CORREO, "otra-cosa", CLIENTE));
                } catch (CredencialesInvalidasException | CuentaBloqueadaException esperado) {
                    // Son los dos resultados que se buscan en este bucle.
                }
            }
        }

        @Test
        void alSextoIntentoSePausaElIngreso() {
            fallar(6);

            assertThatThrownBy(IniciarSesionServiceTest.this::ingresar)
                    .isInstanceOf(CuentaBloqueadaException.class);
        }

        @Test
        @DisplayName("con la contraseña correcta tampoco se entra estando bloqueado")
        void elBloqueoManda() {
            fallar(6);

            assertThatThrownBy(IniciarSesionServiceTest.this::ingresar)
                    .isInstanceOfSatisfying(CuentaBloqueadaException.class, e ->
                            assertThat(e.esperaRestante()).isPositive());
        }

        @Test
        void avisaAlTitularUnaSolaVez() {
            fallar(8);

            assertThat(notificador.ingresosPausados).containsExactly(CORREO);
        }

        @Test
        void registraElBloqueoComoEventoDeSeguridad() {
            fallar(6);

            assertThat(auditoria.eventos).contains(TipoDeEventoDeAcceso.INGRESO_BLOQUEADO);
        }

        @Test
        @DisplayName("un código erróneo también cuenta como intento fallido")
        void elSegundoFactorTambienSuma() {
            // Si solo contaran los fallos de contraseña, quien la conoce podría probar
            // códigos de seis dígitos indefinidamente sin bloquearse nunca.
            segundosFactores.guardar(SegundoFactor
                    .inscribir(ana.id(), SECRETO, AHORA).confirmar(AHORA));
            DesafioAbierto desafio = ingresar();

            assertThatThrownBy(() -> servicio.verificarSegundoFactor(
                    new ComandoDeSegundoFactor(desafio.desafioId(),
                            new CodigoTotp("000000"), CLIENTE)))
                    .isInstanceOf(SegundoFactorInvalidoException.class);

            assertThat(controles.cargar(ana.id()).fallosConsecutivos()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Escenario 4 · Reutilización de un token robado")
    class ReutilizacionDeToken {

        @BeforeEach
        void yaTieneSegundoFactor() {
            segundosFactores.guardar(SegundoFactor
                    .inscribir(ana.id(), SECRETO, AHORA).confirmar(AHORA));
        }

        @Test
        void larenovacionRotaElToken() {
            SesionIniciada primera = completarIngreso();

            SesionIniciada segunda = servicio.renovar(primera.tokenDeRenovacion(), CLIENTE);

            assertThat(segunda.tokenDeRenovacion()).isNotEqualTo(primera.tokenDeRenovacion());
            assertThat(auditoria.eventos).contains(TipoDeEventoDeAcceso.SESION_RENOVADA);
        }

        @Test
        @DisplayName("usar un token ya rotado invalida la familia entera")
        void invalidaLaFamilia() {
            SesionIniciada primera = completarIngreso();
            SesionIniciada segunda = servicio.renovar(primera.tokenDeRenovacion(), CLIENTE);

            assertThatThrownBy(() -> servicio.renovar(primera.tokenDeRenovacion(), CLIENTE))
                    .isInstanceOf(ReutilizacionDeRefreshTokenException.class);

            // Y el token que sí era legítimo tampoco vale ya: se cayó la sesión entera.
            assertThatThrownBy(() -> servicio.renovar(segunda.tokenDeRenovacion(), CLIENTE))
                    .isInstanceOf(SesionExpiradaException.class);
        }

        @Test
        void avisaAlTitularDelIncidente() {
            SesionIniciada primera = completarIngreso();
            servicio.renovar(primera.tokenDeRenovacion(), CLIENTE);

            assertThatThrownBy(() -> servicio.renovar(primera.tokenDeRenovacion(), CLIENTE))
                    .isInstanceOf(ReutilizacionDeRefreshTokenException.class);

            assertThat(notificador.sesionesCerradas).containsExactly(CORREO);
            assertThat(auditoria.eventos)
                    .contains(TipoDeEventoDeAcceso.REUTILIZACION_DE_TOKEN);
        }

        @Test
        void unTokenDesconocidoNoAbreNada() {
            assertThatThrownBy(() -> servicio.renovar("inventado", CLIENTE))
                    .isInstanceOf(SesionExpiradaException.class);
        }
    }

    @Nested
    @DisplayName("Estado del usuario")
    class EstadoDelUsuario {

        @Test
        @DisplayName("quien está pendiente de verificación sí puede entrar")
        void elPendienteDeVerificacionEntra() {
            // Si no pudiera, quien se registra y cierra el navegador quedaría encerrado
            // fuera para siempre: no hay forma de retomar un alta sin iniciar sesión.
            // Lo que no puede es operar, y de eso se encarga puedeOperar().
            assertThat(ana.estado()).isEqualTo(EstadoUsuario.PENDIENTE_VERIFICACION);
            assertThat(ana.puedeOperar()).isFalse();

            assertThat(ingresar().desafioId()).isNotNull();
        }

        @Test
        @DisplayName("quien está en revisión manual también, para poder ver en qué quedó")
        void elQueEstaEnRevisionEntra() {
            usuarios.reemplazar(ana.derivarARevision());

            assertThat(ingresar().desafioId()).isNotNull();
        }

        @Test
        @DisplayName("una cuenta bloqueada no entra aunque la contraseña sea correcta")
        void elBloqueadoNoEntra() {
            usuarios.reemplazar(ana.bloquear());

            assertThatThrownBy(IniciarSesionServiceTest.this::ingresar)
                    .isInstanceOf(CuentaInhabilitadaException.class);
            assertThat(sesiones.tokens).isEmpty();
            assertThat(auditoria.eventos).contains(TipoDeEventoDeAcceso.INGRESO_BLOQUEADO);
        }

        @Test
        @DisplayName("el bloqueo se comprueba después de la contraseña, no antes")
        void elBloqueoNoDelataLaCuenta() {
            // Si se comprobara antes, probar correos revelaría cuáles corresponden a
            // cuentas bloqueadas sin necesidad de acertar ni una contraseña.
            usuarios.reemplazar(ana.bloquear());

            assertThatThrownBy(() -> servicio.presentarCredenciales(
                    new ComandoDeIngreso(CORREO, "otra-cosa", CLIENTE)))
                    .isInstanceOf(CredencialesInvalidasException.class);
        }
    }

    @Nested
    @DisplayName("Inscripción del segundo factor")
    class Inscripcion {

        @Test
        @DisplayName("quien no lo tiene recibe el URI para escanear el QR")
        void entregaElUriDeAprovisionamiento() {
            DesafioAbierto desafio = ingresar();

            assertThat(desafio.requiereInscripcion()).isTrue();
            assertThat(desafio.uriDeAprovisionamiento()).startsWith("otpauth://");
        }

        @Test
        @DisplayName("no queda confirmado hasta que se teclea un código válido")
        void seConfirmaConElPrimerCodigo() {
            // Si se diera por confirmado al generar el secreto, quien cierra la pantalla
            // antes de escanear el QR se quedaría sin acceso y sin forma de recuperarlo.
            DesafioAbierto desafio = ingresar();
            assertThat(segundosFactores.guardados.get(ana.id()).estaConfirmado()).isFalse();

            servicio.verificarSegundoFactor(new ComandoDeSegundoFactor(
                    desafio.desafioId(), new CodigoTotp(CODIGO_BUENO), CLIENTE));

            assertThat(segundosFactores.guardados.get(ana.id()).estaConfirmado()).isTrue();
            assertThat(auditoria.eventos)
                    .contains(TipoDeEventoDeAcceso.SEGUNDO_FACTOR_INSCRITO);
        }

        @Test
        @DisplayName("reintentar reutiliza el secreto en vez de generar otro")
        void noRegeneraElSecretoEnCadaIntento() {
            String primero = ingresar().uriDeAprovisionamiento();
            String segundo = ingresar().uriDeAprovisionamiento();

            assertThat(primero).isEqualTo(segundo);
            assertThat(totp.secretosGenerados).isEqualTo(1);
        }

        @Test
        @DisplayName("una vez confirmado deja de entregarse el URI")
        void noVuelveAEntregarElSecreto() {
            DesafioAbierto primero = ingresar();
            servicio.verificarSegundoFactor(new ComandoDeSegundoFactor(
                    primero.desafioId(), new CodigoTotp(CODIGO_BUENO), CLIENTE));

            DesafioAbierto siguiente = ingresar();

            assertThat(siguiente.requiereInscripcion()).isFalse();
            assertThat(siguiente.uriDeAprovisionamiento()).isNull();
        }
    }

    // ─── Dobles ────────────────────────────────────────────────────────────

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
            guardados.add(usuario);
            return usuario;
        }

        /** Sustituye al usuario por una version suya con otro estado. */
        void reemplazar(Usuario usuario) {
            guardados.removeIf(u -> u.id().equals(usuario.id()));
            guardados.add(usuario);
        }
    }

    private static final class SegundosFactoresFalsos
            implements RepositorioDeSegundoFactorPort {
        private final Map<UUID, SegundoFactor> guardados = new HashMap<>();

        @Override
        public Optional<SegundoFactor> buscarPorUsuario(UUID usuarioId) {
            return Optional.ofNullable(guardados.get(usuarioId));
        }

        @Override
        public SegundoFactor guardar(SegundoFactor segundoFactor) {
            guardados.put(segundoFactor.usuarioId(), segundoFactor);
            return segundoFactor;
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

    private static final class SesionesFalsas implements RepositorioDeSesionesPort {
        private final Map<UUID, DesafioDeSegundoFactor> desafios = new HashMap<>();
        private final List<RefreshToken> tokens = new ArrayList<>();
        private final List<UUID> familiasInvalidadas = new ArrayList<>();

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
            tokens.removeIf(guardado -> guardado.id().equals(token.id()));
            tokens.add(token);
            return token;
        }

        @Override
        public Optional<RefreshToken> buscarTokenPorHuella(String huella) {
            return tokens.stream()
                    .filter(t -> !familiasInvalidadas.contains(t.familiaId()))
                    .filter(t -> t.huella().equals(huella))
                    .findFirst();
        }

        @Override
        public void invalidarFamilia(UUID familiaId) {
            familiasInvalidadas.add(familiaId);
        }

        /** Caduca todos los desafíos abiertos, para la prueba de la ventana de dos minutos. */
        void envejecerDesafios() {
            desafios.replaceAll((id, desafio) -> new DesafioDeSegundoFactor(
                    desafio.id(), desafio.usuarioId(), AHORA.minusSeconds(1)));
        }
    }

    private static final class CifradorFalso implements CifradorDeContrasenasPort {
        private int vecesQueSeCifro;

        @Override
        public ContrasenaCifrada cifrar(String contrasenaEnClaro) {
            vecesQueSeCifro++;
            return new ContrasenaCifrada("$argon2id$" + contrasenaEnClaro);
        }

        @Override
        public boolean coincide(String contrasenaEnClaro, ContrasenaCifrada cifrada) {
            return cifrada.valor().equals("$argon2id$" + contrasenaEnClaro);
        }
    }

    private static final class TotpFalso implements GeneradorDeTotpPort {
        private int secretosGenerados;

        @Override
        public SecretoTotp generarSecreto() {
            secretosGenerados++;
            return SECRETO;
        }

        @Override
        public boolean verificar(SecretoTotp secreto, CodigoTotp codigo, Instant momento) {
            return CODIGO_BUENO.equals(codigo.valor());
        }

        @Override
        public String uriDeAprovisionamiento(SecretoTotp secreto, CorreoElectronico correo) {
            return "otpauth://totp/Ayni?secret=" + secreto.valor();
        }
    }

    private static final class EmisorFalso implements EmisorDeTokensDeAccesoPort {
        private int emitidos;

        @Override
        public String emitir(Usuario usuario, Instant momento, Instant expiraEn) {
            return "jwt-" + usuario.id();
        }

        @Override
        public TokenDeRenovacion generarTokenDeRenovacion() {
            String claro = "token-" + (++emitidos);
            return new TokenDeRenovacion(claro, huellaDe(claro));
        }

        @Override
        public String huellaDe(String tokenEnClaro) {
            return "huella:" + tokenEnClaro;
        }
    }

    private static final class AuditoriaFalsa implements PistaDeAuditoriaPort {
        private final List<TipoDeEventoDeAcceso> eventos = new ArrayList<>();
        private final List<String> ips = new ArrayList<>();

        @Override
        public void registrar(TipoDeEventoDeAcceso tipo, UUID usuarioId,
                              HuellaDeCliente cliente) {
            eventos.add(tipo);
            ips.add(cliente.ip());
        }
    }

    private static final class NotificadorFalso implements NotificadorDeSeguridadPort {
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
