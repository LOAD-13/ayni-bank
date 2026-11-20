package pe.ayni.bank.core.infrastructure.reclamaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import pe.ayni.bank.core.infrastructure.reclamaciones.LibroDeReclamaciones.Constancia;
import pe.ayni.bank.core.infrastructure.reclamaciones.LibroDeReclamaciones.Hoja;
import pe.ayni.bank.core.infrastructure.reclamaciones.LibroDeReclamaciones.Tipo;

/** El libro de reclamaciones: el plazo de respuesta, el codigo y el contrato HTTP publico. */
class LibroDeReclamacionesControllerTest {

    private static final String HOJA = """
            {"tipo":"RECLAMO","nombre":"Ana Quispe","tipoDocumento":"DNI","numeroDocumento":"45678912",
             "correo":"ana.quispe@ejemplo.pe","telefono":"","bien":"SERVICIO",
             "descripcionDelBien":"Transferencia interbancaria","montoReclamado":"150.50",
             "detalle":"La transferencia figura como enviada pero el otro banco no la recibio.",
             "pedido":"Que me devuelvan el importe."}""";

    private final LibroDeReclamaciones libro = mock(LibroDeReclamaciones.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new LibroDeReclamacionesController(libro)).build();

    @Test
    @DisplayName("15 dias habiles saltan sabados y domingos")
    void plazo() {
        // Viernes 20-nov-2026: tres semanas habiles completas terminan el viernes 11-dic.
        assertThat(LibroDeReclamaciones.sumarDiasHabiles(LocalDate.of(2026, 11, 20), 15))
                .isEqualTo(LocalDate.of(2026, 12, 11));
        // Un sabado empieza a contar el lunes.
        assertThat(LibroDeReclamaciones.sumarDiasHabiles(LocalDate.of(2026, 11, 21), 1))
                .isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(LibroDeReclamaciones.codigo(2026, 42)).isEqualTo("LR-2026-000042");
    }

    @Test
    @DisplayName("POST /reclamaciones: 201 con la constancia, sin pedir sesion y con el monto exacto")
    void registra() throws Exception {
        when(libro.registrar(any())).thenReturn(new Constancia("LR-2026-000001", Tipo.RECLAMO,
                Instant.parse("2026-11-19T20:00:00Z"), LocalDate.of(2026, 12, 10)));

        mvc.perform(post("/api/v1/reclamaciones").contentType(MediaType.APPLICATION_JSON).content(HOJA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value("LR-2026-000001"))
                .andExpect(jsonPath("$.responderAntesDe").value("2026-12-10"));

        ArgumentCaptor<Hoja> hoja = ArgumentCaptor.forClass(Hoja.class);
        verify(libro).registrar(hoja.capture());
        assertThat(hoja.getValue().montoReclamado()).isEqualByComparingTo("150.50");
        assertThat(hoja.getValue().telefono()).isNull();
    }

    @Test
    @DisplayName("Un detalle de menos de 20 caracteres o un correo invalido no se registran")
    void valida() throws Exception {
        mvc.perform(post("/api/v1/reclamaciones").contentType(MediaType.APPLICATION_JSON)
                        .content(HOJA.replace("La transferencia figura como enviada pero el otro banco no la recibio.", "Mal")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/reclamaciones").contentType(MediaType.APPLICATION_JSON)
                        .content(HOJA.replace("ana.quispe@ejemplo.pe", "no-es-un-correo")))
                .andExpect(status().isBadRequest());
        verify(libro, never()).registrar(any());
    }
}
