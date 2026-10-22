package pe.ayni.bank.identity.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import io.cucumber.java.Before;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;
import pe.ayni.bank.identity.application.usecase.RechazarSolicitudService;
import pe.ayni.bank.identity.domain.model.Celular;
import pe.ayni.bank.identity.domain.model.Consentimiento;
import pe.ayni.bank.identity.domain.model.ContrasenaCifrada;
import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.model.EstadoUsuario;
import pe.ayni.bank.identity.domain.model.MotivoDeRechazoDeSolicitud;
import pe.ayni.bank.identity.domain.model.SolicitudNoRechazableException;
import pe.ayni.bank.identity.domain.model.Usuario;

/** Pasos de {@code rechazo-de-solicitud.feature} · HU-05 escenario 3 · AYNI-327. */
public class PasosDeRechazoDeSolicitud {

    private static final Instant AHORA = Instant.parse("2026-10-21T15:00:00Z");

    private DoblesEnMemoria.Usuarios usuarios;
    private DoblesEnMemoria.Solicitudes solicitudes;
    private DoblesEnMemoria.Publicador publicador;
    private RechazarSolicitudService servicio;

    private final Map<String, Usuario> personas = new HashMap<>();
    private final Map<String, UUID> solicitudDe = new HashMap<>();
    private RuntimeException fallo;

    @Before
    public void prepararEscenario() {
        usuarios = new DoblesEnMemoria.Usuarios();
        solicitudes = new DoblesEnMemoria.Solicitudes();
        publicador = new DoblesEnMemoria.Publicador();
        servicio = new RechazarSolicitudService(solicitudes, usuarios, publicador);
        personas.clear();
        solicitudDe.clear();
        fallo = null;
    }

    private static String correoDe(String nombre) {
        return nombre.toLowerCase(Locale.ROOT) + "@example.pe";
    }

    // ─── Dado ────────────────────────────────────────────────────────────────

    @Dado("que {word} se registró y su solicitud quedó en revisión manual")
    public void enRevisionManual(String nombre) {
        Usuario usuario = Usuario.registrar(UUID.randomUUID(), new CorreoElectronico(correoDe(nombre)),
                new Celular("987654321"), new ContrasenaCifrada("$argon2id$loquesea"),
                Consentimiento.otorgar(true, AHORA, "v1"), AHORA);
        usuarios.guardar(usuario);
        personas.put(nombre, usuario);
        UUID solicitud = solicitudes.abrirPara(usuario.id(), null);
        solicitudes.enRevisionManual.add(solicitud);
        solicitudDe.put(nombre, solicitud);
    }

    @Dado("que la solicitud de {word} ya fue aprobada")
    public void yaAprobada(String nombre) {
        solicitudes.aprobadas.add(solicitudDe.get(nombre));
    }

    // ─── Cuando ──────────────────────────────────────────────────────────────

    @Cuando("un operador rechaza la solicitud de {word} con el motivo {word}")
    public void rechaza(String nombre, String motivo) {
        try {
            servicio.rechazar(solicitudDe.get(nombre), MotivoDeRechazoDeSolicitud.valueOf(motivo));
        } catch (SolicitudNoRechazableException excepcion) {
            fallo = excepcion;
        }
    }

    // ─── Entonces ────────────────────────────────────────────────────────────

    @Entonces("la solicitud de {word} queda RECHAZADA con el motivo {word}")
    public void quedaRechazada(String nombre, String motivo) {
        UUID solicitud = solicitudDe.get(nombre);
        assertThat(solicitudes.estadoDe(solicitud)).contains("RECHAZADA");
        assertThat(solicitudes.rechazadas.get(solicitud)).isEqualTo(MotivoDeRechazoDeSolicitud.valueOf(motivo));
    }

    @Y("no se anuncia ninguna aprobación, así que no se abre ninguna cuenta")
    public void sinAprobacion() {
        assertThat(publicador.aprobaciones).isEmpty();
    }

    @Y("se anuncia el rechazo a {word} con el texto {string}")
    public void anunciaElRechazo(String nombre, String texto) {
        assertThat(publicador.rechazos).singleElement().satisfies(rechazo -> {
            assertThat(rechazo.usuarioId()).isEqualTo(personas.get(nombre).id());
            assertThat(rechazo.correo()).isEqualTo(correoDe(nombre));
            assertThat(rechazo.nombrePila()).isEqualTo(nombre);
            assertThat(rechazo.motivo().detalle()).isEqualTo(texto);
        });
    }

    @Y("{word} sigue pendiente de verificación")
    public void sigueSinActivar(String nombre) {
        assertThat(usuarios.buscarPorId(personas.get(nombre).id())).get()
                .extracting(Usuario::estado).isEqualTo(EstadoUsuario.PENDIENTE_VERIFICACION);
    }

    @Entonces("el rechazo no se admite porque la solicitud ya está cerrada")
    public void noSeAdmite() {
        assertThat(fallo).isInstanceOfSatisfying(SolicitudNoRechazableException.class,
                excepcion -> assertThat(excepcion.existe()).isTrue());
    }

    @Y("no se anuncia ningún rechazo")
    public void sinRechazo() {
        assertThat(publicador.rechazos).isEmpty();
    }
}
