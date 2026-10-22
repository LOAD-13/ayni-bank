package pe.ayni.bank.identity.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.ayni.bank.identity.aceptacion.DoblesEnMemoria;
import pe.ayni.bank.identity.domain.model.Celular;
import pe.ayni.bank.identity.domain.model.Consentimiento;
import pe.ayni.bank.identity.domain.model.ContrasenaCifrada;
import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.model.EstadoUsuario;
import pe.ayni.bank.identity.domain.model.MotivoDeRechazoDeSolicitud;
import pe.ayni.bank.identity.domain.model.SolicitudNoRechazableException;
import pe.ayni.bank.identity.domain.model.Usuario;

/** HU-05 escenario 3 · AYNI-327: el operador rechaza y el solicitante se entera del motivo. */
class RechazarSolicitudServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-21T10:15:30Z");

    private DoblesEnMemoria.Usuarios usuarios;
    private DoblesEnMemoria.Solicitudes solicitudes;
    private DoblesEnMemoria.Publicador publicador;
    private RechazarSolicitudService servicio;
    private Usuario ana;
    private UUID solicitudDeAna;

    @BeforeEach
    void prepararEscenario() {
        usuarios = new DoblesEnMemoria.Usuarios();
        solicitudes = new DoblesEnMemoria.Solicitudes();
        publicador = new DoblesEnMemoria.Publicador();
        servicio = new RechazarSolicitudService(solicitudes, usuarios, publicador);

        ana = Usuario.registrar(UUID.randomUUID(),
                new CorreoElectronico("ana.quispe@example.pe"),
                new Celular("987654321"),
                new ContrasenaCifrada("$argon2id$loquesea"),
                Consentimiento.otorgar(true, AHORA, "v1"), AHORA);
        usuarios.guardar(ana);
        solicitudDeAna = solicitudes.abrirPara(ana.id(), null);
        solicitudes.enRevisionManual.add(solicitudDeAna);
    }

    @Test
    @DisplayName("la solicitud queda RECHAZADA con su motivo")
    void rechazaConSuMotivo() {
        servicio.rechazar(solicitudDeAna, MotivoDeRechazoDeSolicitud.SELFIE_NO_COINCIDE);

        assertThat(solicitudes.rechazadas).containsEntry(solicitudDeAna, MotivoDeRechazoDeSolicitud.SELFIE_NO_COINCIDE);
        assertThat(solicitudes.estadoDe(solicitudDeAna)).contains("RECHAZADA");
    }

    @Test
    @DisplayName("anuncia el rechazo con el correo, el nombre de pila y el motivo: notification no conoce a Ana")
    void anunciaElRechazo() {
        servicio.rechazar(solicitudDeAna, MotivoDeRechazoDeSolicitud.DATOS_NO_COINCIDEN);

        assertThat(publicador.rechazos).containsExactly(new DoblesEnMemoria.Publicador.Rechazo(
                solicitudDeAna, ana.id(), "ana.quispe@example.pe", "Ana",
                MotivoDeRechazoDeSolicitud.DATOS_NO_COINCIDEN));
    }

    @Test
    @DisplayName("el usuario no se activa ni se bloquea: sigue pendiente de verificación")
    void elUsuarioNoCambia() {
        servicio.rechazar(solicitudDeAna, MotivoDeRechazoDeSolicitud.DOCUMENTO_NO_VALIDO);

        assertThat(usuarios.buscarPorId(ana.id())).get()
                .extracting(Usuario::estado).isEqualTo(EstadoUsuario.PENDIENTE_VERIFICACION);
        assertThat(publicador.aprobaciones).isEmpty();
    }

    @Test
    @DisplayName("una solicitud aprobada ya abrió una cuenta: no se puede rechazar")
    void unaAprobadaNoSeRechaza() {
        solicitudes.aprobadas.add(solicitudDeAna);

        assertThatThrownBy(() -> servicio.rechazar(solicitudDeAna, MotivoDeRechazoDeSolicitud.SELFIE_NO_COINCIDE))
                .isInstanceOfSatisfying(SolicitudNoRechazableException.class, e -> assertThat(e.existe()).isTrue());

        assertThat(solicitudes.rechazadas).isEmpty();
        assertThat(publicador.rechazos).isEmpty();
    }

    @Test
    @DisplayName("rechazarla dos veces no manda dos correos")
    void unaRechazadaNoSeVuelveARechazar() {
        servicio.rechazar(solicitudDeAna, MotivoDeRechazoDeSolicitud.SELFIE_NO_COINCIDE);

        assertThatThrownBy(() -> servicio.rechazar(solicitudDeAna, MotivoDeRechazoDeSolicitud.DATOS_NO_COINCIDEN))
                .isInstanceOf(SolicitudNoRechazableException.class);

        assertThat(publicador.rechazos).hasSize(1);
        assertThat(solicitudes.rechazadas).containsEntry(solicitudDeAna, MotivoDeRechazoDeSolicitud.SELFIE_NO_COINCIDE);
    }

    @Test
    @DisplayName("un señuelo, una solicitud inexistente o una sin usuario se tratan como inexistentes")
    void senuelosEInexistentes() {
        UUID senuelo = solicitudes.abrirSenuelo();
        UUID huerfana = solicitudes.abrirPara(UUID.randomUUID(), null);

        for (UUID solicitud : new UUID[] {senuelo, UUID.randomUUID(), huerfana}) {
            assertThatThrownBy(() -> servicio.rechazar(solicitud, MotivoDeRechazoDeSolicitud.DOCUMENTO_NO_VALIDO))
                    .isInstanceOfSatisfying(SolicitudNoRechazableException.class,
                            e -> assertThat(e.existe()).isFalse());
        }
        assertThat(publicador.rechazos).isEmpty();
    }
}
