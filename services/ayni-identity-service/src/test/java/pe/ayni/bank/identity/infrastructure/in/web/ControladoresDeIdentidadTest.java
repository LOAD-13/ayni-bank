package pe.ayni.bank.identity.infrastructure.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import pe.ayni.bank.identity.domain.model.Celular;
import pe.ayni.bank.identity.domain.model.ComandoDeRegistro;
import pe.ayni.bank.identity.domain.model.Consentimiento;
import pe.ayni.bank.identity.domain.model.ContrasenaCifrada;
import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.model.IdentidadDeclarada;
import pe.ayni.bank.identity.domain.model.ResultadoDeRegistro;
import pe.ayni.bank.identity.domain.model.Usuario;
import pe.ayni.bank.identity.domain.port.in.RegistrarVisitanteUseCase;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSolicitudesPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeUsuariosPort;

/** El borde HTTP del registro y del resumen del titular. */
class ControladoresDeIdentidadTest {

    private static final Instant AHORA = Instant.parse("2026-08-30T10:15:30Z");

    private static Usuario unUsuario() {
        return Usuario.registrar(UUID.randomUUID(),
                new CorreoElectronico("ana.quispe@example.pe"),
                new Celular("987654321"),
                new ContrasenaCifrada("$argon2id$loquesea"),
                Consentimiento.otorgar(true, AHORA, "v1"), AHORA);
    }

    @Nested
    @DisplayName("Registro · HU-01")
    class Registro {

        private final RegistrarVisitanteFalso casoDeUso = new RegistrarVisitanteFalso();
        private final RegistroController controlador = new RegistroController(casoDeUso);

        private final SolicitudDeRegistroDto solicitud = new SolicitudDeRegistroDto(
                "Ana Lucía", "Quispe Mendoza", "DNI", "45678912",
                LocalDate.of(1998, 3, 14), "ana.quispe@example.pe", "987654321",
                "Cont!rasena2026#", true);

        @Test
        @DisplayName("responde 202 y no 201: el alta no termina aquí")
        void respondeAceptado() {
            // Un 201 afirmaría que el recurso está completo y listo, y no lo está: el
            // usuario existe pero no puede operar hasta superar la verificación.
            var respuesta = controlador.registrar(solicitud);

            assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            assertThat(respuesta.getBody().solicitudId()).isNotNull();
            assertThat(respuesta.getBody().mensaje())
                    .isEqualTo(ResultadoDeRegistro.MENSAJE_NEUTRO);
        }

        @Test
        @DisplayName("traslada los nueve campos al dominio, sin perder los de identidad")
        void trasladaLaIdentidadDeclarada() {
            // Durante un tiempo el formulario pedía los datos de identidad y el servidor
            // los descartaba en silencio. Esta prueba impide que vuelva a pasar.
            controlador.registrar(solicitud);

            ComandoDeRegistro comando = casoDeUso.recibidos.get(0);
            assertThat(comando.nombres()).isEqualTo("Ana Lucía");
            assertThat(comando.apellidos()).isEqualTo("Quispe Mendoza");
            assertThat(comando.tipoDocumento()).isEqualTo("DNI");
            assertThat(comando.numeroDocumento()).isEqualTo("45678912");
            assertThat(comando.fechaNacimiento()).isEqualTo(LocalDate.of(1998, 3, 14));
            assertThat(comando.aceptaTerminos()).isTrue();
        }

        @Test
        @DisplayName("el DTO no imprime ni un dato personal")
        void elDtoNoDejaRastro() {
            assertThat(solicitud.toString())
                    .doesNotContain("Quispe")
                    .doesNotContain("45678912")
                    .doesNotContain("ana.quispe")
                    .doesNotContain("Cont!rasena2026#");
        }

        @Test
        @DisplayName("la mayoría de edad se comprueba también en el DTO")
        void elDtoComprubaLaMayoriaDeEdad() {
            var menor = new SolicitudDeRegistroDto("Luis", "Rojas", "DNI", "11223344",
                    LocalDate.now().minusYears(10), "luis@example.pe", "987654321",
                    "Cont!rasena2026#", true);

            assertThat(menor.isMayorDeEdad()).isFalse();
            assertThat(solicitud.isMayorDeEdad()).isTrue();
        }

        @Test
        @DisplayName("una fecha ausente no duplica el mensaje de error")
        void sinFechaNoSeQuejaDosVeces() {
            // De ese hueco ya se queja @NotNull. Encadenar dos mensajes sobre el mismo
            // campo vacío solo confunde.
            var sinFecha = new SolicitudDeRegistroDto("Luis", "Rojas", "DNI", "11223344",
                    null, "luis@example.pe", "987654321", "Cont!rasena2026#", true);

            assertThat(sinFecha.isMayorDeEdad()).isTrue();
        }
    }

    @Nested
    @DisplayName("Resumen del titular")
    class Resumen {

        private final UsuariosFalsos usuarios = new UsuariosFalsos();
        private final SolicitudesFalsas solicitudes = new SolicitudesFalsas();
        private final UsuarioController controlador =
                new UsuarioController(usuarios, solicitudes);

        @Test
        @DisplayName("devuelve el nombre de pila y el correo enmascarado, y nada más")
        void devuelveLoMinimo() {
            Usuario ana = unUsuario();
            usuarios.guardar(ana);
            solicitudes.nombres.put(ana.id(), "Ana");

            var resumen = controlador.resumen(ana.id());

            assertThat(resumen.nombreDePila()).isEqualTo("Ana");
            assertThat(resumen.estado()).isEqualTo("PENDIENTE_VERIFICACION");
            // El correo va enmascarado en origen: la pantalla solo tiene que recordar a
            // dónde se envió el aviso.
            assertThat(resumen.correo())
                    .doesNotContain("ana.quispe")
                    .contains("@example.pe")
                    .contains("*");
        }

        @Test
        @DisplayName("sin identidad declarada se saluda sin nombre, no se falla")
        void toleraQueNoHayaNombre() {
            Usuario ana = unUsuario();
            usuarios.guardar(ana);

            assertThat(controlador.resumen(ana.id()).nombreDePila()).isNull();
        }

        @Test
        void unUsuarioDesconocidoEs404() {
            assertThatThrownBy(() -> controlador.resumen(UUID.randomUUID()))
                    .isInstanceOf(UsuarioController.UsuarioDesconocidoException.class);

            assertThat(controlador.alNoExistir().getStatus())
                    .isEqualTo(HttpStatus.NOT_FOUND.value());
        }
    }

    // ─── Dobles ────────────────────────────────────────────────────────────

    private static final class RegistrarVisitanteFalso implements RegistrarVisitanteUseCase {
        private final List<ComandoDeRegistro> recibidos = new ArrayList<>();

        @Override
        public ResultadoDeRegistro registrar(ComandoDeRegistro comando) {
            recibidos.add(comando);
            return ResultadoDeRegistro.aceptado(UUID.randomUUID());
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
            guardados.add(usuario);
            return usuario;
        }
    }

    private static final class SolicitudesFalsas implements RepositorioDeSolicitudesPort {
        private final Map<UUID, String> nombres = new HashMap<>();

        @Override
        public UUID abrirPara(UUID usuarioId, IdentidadDeclarada identidad) {
            return UUID.randomUUID();
        }

        @Override
        public UUID abrirSenuelo() {
            return UUID.randomUUID();
        }

        @Override
        public Optional<UUID> titularDe(UUID solicitudId) {
            return Optional.empty();
        }

        @Override
        public void marcarAprobada(UUID solicitudId) {
            // Sin efecto: esta prueba no aprueba nada.
        }

        @Override
        public Optional<String> nombreDePilaDe(UUID usuarioId) {
            return Optional.ofNullable(nombres.get(usuarioId));
        }
    }
}
