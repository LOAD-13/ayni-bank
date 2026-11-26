package pe.ayni.bank.core.infrastructure.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import pe.ayni.bank.core.domain.model.EventoDeAuditoria;
import pe.ayni.bank.core.domain.model.PaginaDeAuditoria;
import pe.ayni.bank.core.domain.port.in.ConsultarAuditoriaUseCase;

/** El contrato HTTP de la lectura de la pista · AYNI-284. */
class AuditoriaControllerTest {

    private static final String USUARIO = OperacionesController.CABECERA_USUARIO;
    private static final String PERMISOS = AuditoriaController.CABECERA_PERMISOS;

    private final ConsultarAuditoriaUseCase consultar = mock(ConsultarAuditoriaUseCase.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new AuditoriaController(consultar)).build();
    private final UUID auditor = UUID.randomUUID();
    private final UUID cliente = UUID.randomUUID();

    private PaginaDeAuditoria pagina() {
        return new PaginaDeAuditoria(List.of(new EventoDeAuditoria(7, "TRANSFERENCIA_REALIZADA", cliente,
                UUID.randomUUID(), null, "EXITO", "POST /api/v1/transferencias", "203.0.113.5", "Firefox",
                Instant.parse("2026-11-24T15:00:00Z"))), 0, 20, 1);
    }

    private void nadaSeLee() {
        verify(consultar, never()).consultar(any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Con auditoria:leer devuelve la pagina con actor, accion, recurso, IP, resultado y momento")
    void conPermiso() throws Exception {
        when(consultar.consultar(any(), any(), any(), any(), eq(0), eq(20))).thenReturn(pagina());

        mvc.perform(get("/api/v1/auditoria/eventos").header(USUARIO, auditor)
                        .header(PERMISOS, "cliente:leer,auditoria:leer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.eventos[0].tipo").value("TRANSFERENCIA_REALIZADA"))
                .andExpect(jsonPath("$.eventos[0].usuarioId").value(cliente.toString()))
                .andExpect(jsonPath("$.eventos[0].recurso").value("POST /api/v1/transferencias"))
                .andExpect(jsonPath("$.eventos[0].ip").value("203.0.113.5"))
                .andExpect(jsonPath("$.eventos[0].resultado").value("EXITO"))
                .andExpect(jsonPath("$.eventos[0].ocurridoEn").value("2026-11-24T15:00:00Z"));
    }

    @Test
    @DisplayName("Sin el permiso, aunque la sesion sea valida: 403 y no se lee nada")
    void sinPermiso() throws Exception {
        mvc.perform(get("/api/v1/auditoria/eventos").header(USUARIO, auditor).header(PERMISOS, "cuenta:leer"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PERMISO_INSUFICIENTE"));
        mvc.perform(get("/api/v1/auditoria/eventos").header(USUARIO, auditor))
                .andExpect(status().isForbidden());
        nadaSeLee();
    }

    @Test
    @DisplayName("Un permiso con nombre parecido no vale: se compara el codigo entero")
    void permisoParecido() throws Exception {
        mvc.perform(get("/api/v1/auditoria/eventos").header(USUARIO, auditor)
                        .header(PERMISOS, "xauditoria:leer,auditoria:leer_todo"))
                .andExpect(status().isForbidden());
        nadaSeLee();
    }

    @Test
    @DisplayName("Sin la cabecera del gateway: 401")
    void sinIdentidad() throws Exception {
        mvc.perform(get("/api/v1/auditoria/eventos").header(PERMISOS, "auditoria:leer"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Los filtros llegan al caso de uso, y el tamano de pagina se acota a 100")
    void filtros() throws Exception {
        when(consultar.consultar(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(pagina());

        mvc.perform(get("/api/v1/auditoria/eventos").header(USUARIO, auditor).header(PERMISOS, "auditoria:leer")
                        .param("usuarioId", cliente.toString()).param("tipo", "PAN_REVELADO")
                        .param("desde", "2026-11-01").param("hasta", "2026-11-24")
                        .param("pagina", "2").param("tamano", "5000"))
                .andExpect(status().isOk());

        verify(consultar).consultar(cliente, "PAN_REVELADO", LocalDate.parse("2026-11-01"),
                LocalDate.parse("2026-11-24"), 2, 100);
    }

    @Test
    @DisplayName("Un tipo con caracteres raros no llega al caso de uso: 400")
    void tipoNoValido() throws Exception {
        mvc.perform(get("/api/v1/auditoria/eventos").header(USUARIO, auditor).header(PERMISOS, "auditoria:leer")
                        .param("tipo", "x'; DROP TABLE evento_auditoria;--"))
                .andExpect(status().isBadRequest());
        nadaSeLee();
    }

    @Test
    @DisplayName("Un rango de fechas al reves es un 400 con explicacion")
    void rangoAlReves() throws Exception {
        when(consultar.consultar(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new IllegalArgumentException("El rango esta al reves"));

        mvc.perform(get("/api/v1/auditoria/eventos").header(USUARIO, auditor).header(PERMISOS, "auditoria:leer")
                        .param("desde", "2026-11-24").param("hasta", "2026-11-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Solo se expone GET: POST, PUT y DELETE no existen sobre la pista")
    void soloLectura() throws Exception {
        mvc.perform(post("/api/v1/auditoria/eventos").header(USUARIO, auditor).header(PERMISOS, "auditoria:leer"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(put("/api/v1/auditoria/eventos").header(USUARIO, auditor).header(PERMISOS, "auditoria:leer"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/api/v1/auditoria/eventos").header(USUARIO, auditor).header(PERMISOS, "auditoria:leer"))
                .andExpect(status().isMethodNotAllowed());
    }
}
