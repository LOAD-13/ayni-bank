package pe.ayni.bank.identity.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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

import pe.ayni.bank.identity.domain.model.ComandoDeRegistro;
import pe.ayni.bank.identity.domain.model.ConsentimientoNoOtorgadoException;
import pe.ayni.bank.identity.domain.model.ContrasenaCifrada;
import pe.ayni.bank.identity.domain.model.ContrasenaInvalidaException;
import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.model.EstadoUsuario;
import pe.ayni.bank.identity.domain.model.IdentidadDeclarada;
import pe.ayni.bank.identity.domain.model.RequisitoDeContrasena;
import pe.ayni.bank.identity.domain.model.ResultadoDeRegistro;
import pe.ayni.bank.identity.domain.model.Usuario;
import pe.ayni.bank.identity.domain.port.out.CifradorDeContrasenasPort;
import pe.ayni.bank.identity.domain.port.out.NotificadorDeRegistroPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSolicitudesPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeUsuariosPort;

/**
 * Los cuatro escenarios de aceptacion de HU-01, sin Spring y sin base de datos.
 *
 * <p>Se usan dobles escritos a mano y no una biblioteca de simulacion. Con dobles reales
 * se puede afirmar sobre lo que de verdad importa aqui —cuantas veces se derivo la
 * contrasena, que correos se avisaron, cuantos usuarios se guardaron— sin encadenar
 * verificaciones que describen la implementacion en lugar del comportamiento.
 */
class RegistrarVisitanteServiceTest {

    private static final Instant AHORA = Instant.parse("2026-08-30T10:15:30Z");
    private static final String CONTRASENA_VALIDA = "Cont!rasena2026#";
    private static final String CORREO = "ana.quispe@example.pe";
    private static final String CELULAR = "987654321";
    private static final String NOMBRES = "Ana Lucia";
    private static final String APELLIDOS = "Quispe Mendoza";
    private static final String DNI = "45678912";
    private static final LocalDate NACIMIENTO = LocalDate.of(1998, 3, 14);

    private RepositorioDeUsuariosFalso usuarios;
    private RepositorioDeSolicitudesFalso solicitudes;
    private CifradorFalso cifrador;
    private NotificadorFalso notificador;
    private RegistrarVisitanteService servicio;

    @BeforeEach
    void prepararEscenario() {
        usuarios = new RepositorioDeUsuariosFalso();
        solicitudes = new RepositorioDeSolicitudesFalso();
        cifrador = new CifradorFalso();
        notificador = new NotificadorFalso();
        servicio = new RegistrarVisitanteService(usuarios, solicitudes, cifrador, notificador,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static ComandoDeRegistro comandoValido() {
        return comando(CORREO, CELULAR, CONTRASENA_VALIDA, true);
    }

    /** Un comando valido con los cuatro campos que cada prueba necesita variar. */
    private static ComandoDeRegistro comando(String correo, String celular,
                                             String contrasena, boolean aceptaTerminos) {
        return new ComandoDeRegistro(NOMBRES, APELLIDOS, "DNI", DNI, NACIMIENTO,
                correo, celular, contrasena, aceptaTerminos);
    }

    @Nested
    @DisplayName("Escenario 1 · Registro exitoso con datos validos")
    class RegistroExitoso {

        @Test
        void creaElUsuarioEnEstadoPendienteDeVerificacion() {
            servicio.registrar(comandoValido());

            assertThat(usuarios.guardados).hasSize(1);
            assertThat(usuarios.guardados.get(0).estado())
                    .isEqualTo(EstadoUsuario.PENDIENTE_VERIFICACION);
        }

        @Test
        @DisplayName("guarda la contrasena derivada, nunca en claro")
        void guardaLaContrasenaDerivada() {
            servicio.registrar(comandoValido());

            assertThat(usuarios.guardados.get(0).contrasena().valor())
                    .doesNotContain(CONTRASENA_VALIDA)
                    .startsWith("$argon2id$");
        }

        @Test
        void normalizaElCorreoAntesDeGuardarlo() {
            servicio.registrar(
                    comando("  Ana.Quispe@EXAMPLE.pe ", CELULAR, CONTRASENA_VALIDA, true));

            assertThat(usuarios.guardados.get(0).correo().valor()).isEqualTo(CORREO);
        }

        @Test
        void envaElCorreoDeBienvenida() {
            servicio.registrar(comandoValido());

            assertThat(notificador.bienvenidas).containsExactly(CORREO);
            assertThat(notificador.avisosDeIntento).isEmpty();
        }

        @Test
        @DisplayName("abre una solicitud real ligada al usuario y la devuelve")
        void abreLaSolicitudDeOnboarding() {
            ResultadoDeRegistro resultado = servicio.registrar(comandoValido());

            assertThat(solicitudes.reales).hasSize(1);
            assertThat(solicitudes.senuelos).isEmpty();
            assertThat(solicitudes.reales).containsKey(resultado.solicitudId());
            assertThat(solicitudes.reales.get(resultado.solicitudId()))
                    .isEqualTo(usuarios.guardados.get(0).id());
        }

        @Test
        @DisplayName("registra el consentimiento con su momento y su version")
        void registraElConsentimiento() {
            servicio.registrar(comandoValido());

            var consentimiento = usuarios.guardados.get(0).consentimiento();
            assertThat(consentimiento.otorgadoEn()).isEqualTo(AHORA);
            assertThat(consentimiento.versionDeLosTerminos()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("Escenario 2 · Correo ya registrado")
    class CorreoYaRegistrado {

        @BeforeEach
        void yaExisteEseCorreo() {
            usuarios.correosExistentes.add(CORREO);
        }

        @Test
        void noCreaUnUsuarioDuplicado() {
            servicio.registrar(comandoValido());

            assertThat(usuarios.guardados).isEmpty();
        }

        @Test
        @DisplayName("la respuesta es indistinguible de un registro correcto")
        void laRespuestaEsIndistinguible() {
            ResultadoDeRegistro conCorreoExistente = servicio.registrar(comandoValido());

            prepararEscenario();
            ResultadoDeRegistro conCorreoNuevo = servicio.registrar(comandoValido());

            // Mismo estado y mismo texto. Lo unico que cambia es el identificador, que es
            // aleatorio en ambos casos y no se deriva del correo.
            assertThat(conCorreoExistente.estado()).isEqualTo(conCorreoNuevo.estado());
            assertThat(conCorreoExistente.mensaje()).isEqualTo(conCorreoNuevo.mensaje());
            assertThat(conCorreoExistente.solicitudId()).isNotNull();
        }

        @Test
        @DisplayName("el mensaje no afirma ni niega que la cuenta exista")
        void elMensajeEsNeutro() {
            assertThat(servicio.registrar(comandoValido()).mensaje())
                    .isEqualTo(ResultadoDeRegistro.MENSAJE_NEUTRO)
                    .doesNotContain("ya existe")
                    .doesNotContain("registrado");
        }

        @Test
        @DisplayName("deriva la contrasena igualmente, o el cronometro delataria la cuenta")
        void derivaLaContrasenaAunqueNoVayaAGuardarla() {
            // Sin esta derivacion, la respuesta llegaria en milisegundos frente a las
            // decenas que cuesta Argon2id en el camino normal. Esa diferencia es un
            // oraculo tan util como devolver un 409: basta cronometrar la peticion.
            servicio.registrar(comandoValido());

            assertThat(cifrador.vecesQueSeCifro).isEqualTo(1);
        }

        @Test
        @DisplayName("avisa al titular legitimo, que es el unico con derecho a saberlo")
        void avisaAlTitularLegitimo() {
            servicio.registrar(comandoValido());

            assertThat(notificador.avisosDeIntento).containsExactly(CORREO);
            assertThat(notificador.bienvenidas).isEmpty();
        }

        @Test
        @DisplayName("persiste el senuelo: un identificador inventado se delataria despues")
        void persisteElSenuelo() {
            ResultadoDeRegistro resultado = servicio.registrar(comandoValido());

            assertThat(solicitudes.senuelos).containsExactly(resultado.solicitudId());
        }
    }

    @Nested
    @DisplayName("Escenario 3 · Contrasena que no cumple la politica")
    class ContrasenaInvalida {

        @Test
        void noCreaElUsuario() {
            assertThatThrownBy(() -> servicio.registrar(
                    comando(CORREO, CELULAR, "corta", true)))
                    .isInstanceOf(ContrasenaInvalidaException.class);

            assertThat(usuarios.guardados).isEmpty();
            assertThat(notificador.bienvenidas).isEmpty();
        }

        @Test
        @DisplayName("indica que requisito concreto incumple")
        void indicaElRequisitoIncumplido() {
            assertThatThrownBy(() -> servicio.registrar(
                    comando(CORREO, CELULAR, "contrasenalarga1", true)))
                    .isInstanceOfSatisfying(ContrasenaInvalidaException.class, e ->
                            assertThat(e.incumplidos()).containsExactlyInAnyOrder(
                                    RequisitoDeContrasena.MAYUSCULA,
                                    RequisitoDeContrasena.SIMBOLO));
        }

        @Test
        @DisplayName("falla antes de tocar el repositorio: no gasta una consulta")
        void fallaAntesDeConsultarElRepositorio() {
            assertThatThrownBy(() -> servicio.registrar(
                    comando(CORREO, CELULAR, "corta", true)))
                    .isInstanceOf(ContrasenaInvalidaException.class);

            assertThat(usuarios.vecesQueSeConsultoExistencia).isZero();
        }
    }

    @Nested
    @DisplayName("Escenario 4 · Terminos no aceptados")
    class TerminosNoAceptados {

        @Test
        void noCreaElUsuario() {
            assertThatThrownBy(() -> servicio.registrar(
                    comando(CORREO, CELULAR, CONTRASENA_VALIDA, false)))
                    .isInstanceOf(ConsentimientoNoOtorgadoException.class)
                    .hasMessageContaining("aceptar los terminos");

            assertThat(usuarios.guardados).isEmpty();
            assertThat(solicitudes.reales).isEmpty();
            assertThat(solicitudes.senuelos).isEmpty();
        }
    }

    @Nested
    @DisplayName("Validacion de los datos de contacto")
    class DatosDeContacto {

        @Test
        void rechazaUnCorreoMalFormado() {
            assertThatThrownBy(() -> servicio.registrar(
                    comando("sin-arroba", CELULAR, CONTRASENA_VALIDA, true)))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThat(usuarios.guardados).isEmpty();
        }

        @Test
        void rechazaUnCelularQueNoEsMovilPeruano() {
            assertThatThrownBy(() -> servicio.registrar(
                    comando(CORREO, "12345678", CONTRASENA_VALIDA, true)))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThat(usuarios.guardados).isEmpty();
        }
    }

    @Nested
    @DisplayName("Identidad declarada · el termino de comparacion del OCR en HU-02")
    class Identidad {

        @Test
        @DisplayName("se guarda en la solicitud, ya normalizada")
        void seGuardaConLaSolicitud() {
            servicio.registrar(comandoValido());

            assertThat(solicitudes.identidades).hasSize(1);
            var identidad = solicitudes.identidades.get(0);
            assertThat(identidad.nombres()).isEqualTo(NOMBRES);
            assertThat(identidad.apellidos()).isEqualTo(APELLIDOS);
            assertThat(identidad.documento().numero()).isEqualTo(DNI);
            assertThat(identidad.fechaNacimiento().valor()).isEqualTo(NACIMIENTO);
        }

        @Test
        @DisplayName("el senuelo no guarda datos personales de una cuenta que no se creo")
        void elSenueloNoConservaNada() {
            usuarios.correosExistentes.add(CORREO);

            servicio.registrar(comandoValido());

            assertThat(solicitudes.senuelos).hasSize(1);
            assertThat(solicitudes.identidades).isEmpty();
        }

        @Test
        @DisplayName("un menor de edad no puede abrir una cuenta a nombre propio")
        void rechazaAlMenorDeEdad() {
            LocalDate ayerCumplioDiecisiete = LocalDate.ofInstant(AHORA, ZoneOffset.UTC)
                    .minusYears(17);

            assertThatThrownBy(() -> servicio.registrar(new ComandoDeRegistro(
                    NOMBRES, APELLIDOS, "DNI", DNI, ayerCumplioDiecisiete,
                    CORREO, CELULAR, CONTRASENA_VALIDA, true)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("18");

            assertThat(usuarios.guardados).isEmpty();
        }

        @Test
        @DisplayName("un documento invalido falla igual exista o no el correo")
        void fallaIgualConElCorreoTomado() {
            // Si la identidad se compusiera dentro de la rama del registro nuevo, este
            // mismo comando daria 400 con el correo libre y 202 con el correo tomado, y
            // esa diferencia volveria a delatar que cuentas existen. Ver ADR-0008.
            usuarios.correosExistentes.add(CORREO);

            assertThatThrownBy(() -> servicio.registrar(new ComandoDeRegistro(
                    NOMBRES, APELLIDOS, "DNI", "123", NACIMIENTO,
                    CORREO, CELULAR, CONTRASENA_VALIDA, true)))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThat(solicitudes.senuelos).isEmpty();
        }
    }

    // ─── Dobles ────────────────────────────────────────────────────────────

    private static final class RepositorioDeUsuariosFalso implements RepositorioDeUsuariosPort {
        private final List<Usuario> guardados = new ArrayList<>();
        private final List<String> correosExistentes = new ArrayList<>();
        private int vecesQueSeConsultoExistencia;

        @Override
        public boolean existeCorreo(CorreoElectronico correo) {
            vecesQueSeConsultoExistencia++;
            return correosExistentes.contains(correo.valor());
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
    }

    private static final class RepositorioDeSolicitudesFalso
            implements RepositorioDeSolicitudesPort {
        private final Map<UUID, UUID> reales = new HashMap<>();
        private final List<UUID> senuelos = new ArrayList<>();
        private final List<UUID> aprobadas = new ArrayList<>();

        private final List<IdentidadDeclarada> identidades = new ArrayList<>();

        @Override
        public UUID abrirPara(UUID usuarioId, IdentidadDeclarada identidad) {
            UUID id = UUID.randomUUID();
            reales.put(id, usuarioId);
            identidades.add(identidad);
            return id;
        }

        @Override
        public UUID abrirSenuelo() {
            UUID id = UUID.randomUUID();
            senuelos.add(id);
            return id;
        }

        @Override
        public java.util.Optional<UUID> titularDe(UUID solicitudId) {
            return java.util.Optional.ofNullable(reales.get(solicitudId));
        }

        @Override
        public void marcarAprobada(UUID solicitudId) {
            aprobadas.add(solicitudId);
        }

        @Override
        public java.util.Optional<String> nombreDePilaDe(UUID usuarioId) {
            return java.util.Optional.of("Ana");
        }
    }

    private static final class CifradorFalso implements CifradorDeContrasenasPort {
        private int vecesQueSeCifro;

        @Override
        public ContrasenaCifrada cifrar(String contrasenaEnClaro) {
            vecesQueSeCifro++;
            return new ContrasenaCifrada(
                    "$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$" + contrasenaEnClaro.hashCode());
        }

        @Override
        public boolean coincide(String contrasenaEnClaro, ContrasenaCifrada cifrada) {
            return cifrar(contrasenaEnClaro).valor().equals(cifrada.valor());
        }
    }

    private static final class NotificadorFalso implements NotificadorDeRegistroPort {
        private final List<String> bienvenidas = new ArrayList<>();
        private final List<String> avisosDeIntento = new ArrayList<>();

        @Override
        public void enviarBienvenida(CorreoElectronico correo) {
            bienvenidas.add(correo.valor());
        }

        @Override
        public void avisarIntentoDeRegistroSobreCuentaExistente(CorreoElectronico correo) {
            avisosDeIntento.add(correo.valor());
        }
    }
}
