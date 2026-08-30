package pe.ayni.bank.identity.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.ayni.bank.identity.domain.model.Celular;
import pe.ayni.bank.identity.domain.model.Consentimiento;
import pe.ayni.bank.identity.domain.model.ContrasenaCifrada;
import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.model.EstadoUsuario;
import pe.ayni.bank.identity.domain.model.IdentidadDeclarada;
import pe.ayni.bank.identity.domain.model.SolicitudNoAprobableException;
import pe.ayni.bank.identity.domain.model.Usuario;
import pe.ayni.bank.identity.domain.port.out.PublicadorDeSolicitudesPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSolicitudesPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeUsuariosPort;

/**
 * La bisagra del esqueleto ambulante: aprobar la solicitud es lo que dispara la apertura de
 * la cuenta en otro servicio.
 */
class AprobarSolicitudServiceTest {

    private static final Instant AHORA = Instant.parse("2026-08-30T10:15:30Z");

    private UsuariosFalsos usuarios;
    private SolicitudesFalsas solicitudes;
    private PublicadorFalso publicador;
    private AprobarSolicitudService servicio;
    private Usuario ana;
    private UUID solicitudDeAna;

    @BeforeEach
    void prepararEscenario() {
        usuarios = new UsuariosFalsos();
        solicitudes = new SolicitudesFalsas();
        publicador = new PublicadorFalso();
        servicio = new AprobarSolicitudService(solicitudes, usuarios, publicador);

        ana = Usuario.registrar(UUID.randomUUID(),
                new CorreoElectronico("ana.quispe@example.pe"),
                new Celular("987654321"),
                new ContrasenaCifrada("$argon2id$loquesea"),
                Consentimiento.otorgar(true, AHORA, "v1"), AHORA);
        usuarios.guardar(ana);

        solicitudDeAna = UUID.randomUUID();
        solicitudes.titulares.put(solicitudDeAna, ana.id());
    }

    @Test
    @DisplayName("la solicitud queda aprobada y el usuario pasa a ACTIVO")
    void apruebaYActiva() {
        servicio.aprobar(solicitudDeAna);

        assertThat(solicitudes.aprobadas).containsExactly(solicitudDeAna);
        assertThat(usuarios.ultimoGuardado().estado()).isEqualTo(EstadoUsuario.ACTIVO);
    }

    @Test
    @DisplayName("anuncia la aprobación: es lo que abre la cuenta en core-banking")
    void anunciaLaAprobacion() {
        servicio.aprobar(solicitudDeAna);

        assertThat(publicador.anuncios).containsExactly(Map.entry(solicitudDeAna, ana.id()));
    }

    @Test
    @DisplayName("una solicitud señuelo no se puede aprobar")
    void noApruebaUnSenuelo() {
        // Los señuelos que devuelve el registro ante un correo ya existente no tienen
        // titular. Aprobar uno abriría una cuenta que no pertenece a nadie. Ver ADR-0008.
        UUID senuelo = UUID.randomUUID();
        solicitudes.titulares.put(senuelo, null);

        assertThatThrownBy(() -> servicio.aprobar(senuelo))
                .isInstanceOf(SolicitudNoAprobableException.class);

        assertThat(publicador.anuncios).isEmpty();
        assertThat(solicitudes.aprobadas).isEmpty();
    }

    @Test
    void unaSolicitudInexistenteTampoco() {
        assertThatThrownBy(() -> servicio.aprobar(UUID.randomUUID()))
                .isInstanceOf(SolicitudNoAprobableException.class);

        assertThat(publicador.anuncios).isEmpty();
    }

    @Test
    @DisplayName("si el usuario ya no existe, no se anuncia nada")
    void noAnunciaSiElUsuarioDesaparecio() {
        UUID huerfana = UUID.randomUUID();
        solicitudes.titulares.put(huerfana, UUID.randomUUID());

        assertThatThrownBy(() -> servicio.aprobar(huerfana))
                .isInstanceOf(SolicitudNoAprobableException.class);

        assertThat(publicador.anuncios).isEmpty();
    }

    // ─── Dobles ────────────────────────────────────────────────────────────

    private static final class UsuariosFalsos implements RepositorioDeUsuariosPort {
        private final List<Usuario> guardados = new ArrayList<>();

        Usuario ultimoGuardado() {
            return guardados.get(guardados.size() - 1);
        }

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
        private final Map<UUID, UUID> titulares = new HashMap<>();
        private final List<UUID> aprobadas = new ArrayList<>();

        @Override
        public UUID abrirPara(UUID usuarioId, IdentidadDeclarada identidad) {
            UUID id = UUID.randomUUID();
            titulares.put(id, usuarioId);
            return id;
        }

        @Override
        public UUID abrirSenuelo() {
            UUID id = UUID.randomUUID();
            titulares.put(id, null);
            return id;
        }

        @Override
        public Optional<UUID> titularDe(UUID solicitudId) {
            return Optional.ofNullable(titulares.get(solicitudId));
        }

        @Override
        public void marcarAprobada(UUID solicitudId) {
            aprobadas.add(solicitudId);
        }

        @Override
        public Optional<String> nombreDePilaDe(UUID usuarioId) {
            return Optional.of("Ana");
        }
    }

    private static final class PublicadorFalso implements PublicadorDeSolicitudesPort {
        private final List<Map.Entry<UUID, UUID>> anuncios = new ArrayList<>();

        @Override
        public void anunciarAprobacion(UUID solicitudId, UUID usuarioId) {
            anuncios.add(Map.entry(solicitudId, usuarioId));
        }
    }
}
