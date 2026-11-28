package pe.ayni.bank.core.infrastructure.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import pe.ayni.bank.core.domain.model.CuatroOjosException;
import pe.ayni.bank.core.domain.model.SolicitudDeBloqueo;
import pe.ayni.bank.core.domain.model.SolicitudDeBloqueoNoValidaException;
import pe.ayni.bank.core.domain.port.in.BloquearCuentaUseCase;

class BloqueosControllerTest {

    private static final String RUTA = "/api/v1/backoffice/bloqueos";
    private static final UUID OPERADOR = UUID.randomUUID();
    private static final UUID SUPERVISOR = UUID.randomUUID();
    private static final Instant AHORA = Instant.parse("2026-11-26T15:00:00Z");
    private static final String CUERPO = "{\"numeroDeCuenta\":\"00110000000101\",\"motivo\":\"Fraude\"}";

    private final BloquearCuentaUseCase bloqueos = mock(BloquearCuentaUseCase.class);
    private final pe.ayni.bank.core.domain.port.out.PistaDeAuditoriaPort auditoria =
            mock(pe.ayni.bank.core.domain.port.out.PistaDeAuditoriaPort.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new BloqueosController(bloqueos, auditoria)).build();
    private final SolicitudDeBloqueo pedida =
            SolicitudDeBloqueo.iniciar(UUID.randomUUID(), UUID.randomUUID(), "Fraude", OPERADOR, AHORA);

    @Test
    void el_operador_inicia_el_bloqueo() throws Exception {
        when(bloqueos.iniciar(OPERADOR, "00110000000101", "Fraude")).thenReturn(pedida);

        mvc.perform(post(RUTA).header("X-Ayni-Usuario", OPERADOR).header("X-Ayni-Permisos", "cliente:bloquear_iniciar")
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    void sin_el_permiso_no_llega_al_caso_de_uso() throws Exception {
        mvc.perform(post(RUTA).header("X-Ayni-Usuario", OPERADOR).header("X-Ayni-Permisos", "cliente:bloquear_aprobar")
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PERMISO_INSUFICIENTE"));
        mvc.perform(post(RUTA + "/" + pedida.id() + "/aprobacion").header("X-Ayni-Usuario", SUPERVISOR))
                .andExpect(status().isForbidden());
        verifyNoInteractions(bloqueos);
        // HU-15, escenario 3: cada intento denegado queda en la pista con el permiso que faltaba.
        org.mockito.Mockito.verify(auditoria).registrarEscrituraDenegada(OPERADOR, "cliente:bloquear_iniciar");
        org.mockito.Mockito.verify(auditoria).registrarEscrituraDenegada(SUPERVISOR, "cliente:bloquear_aprobar");
    }

    @Test
    void el_supervisor_lista_y_aprueba() throws Exception {
        when(bloqueos.pendientes()).thenReturn(List.of(pedida));
        when(bloqueos.aprobar(SUPERVISOR, pedida.id())).thenReturn(pedida.aprobar(SUPERVISOR, AHORA));

        mvc.perform(get(RUTA).header("X-Ayni-Usuario", SUPERVISOR).header("X-Ayni-Permisos", "cliente:bloquear_aprobar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(pedida.id().toString()));
        mvc.perform(post(RUTA + "/" + pedida.id() + "/aprobacion").header("X-Ayni-Usuario", SUPERVISOR)
                        .header("X-Ayni-Permisos", "cliente:leer,cliente:bloquear_aprobar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADA"));
    }

    @Test
    void aprobar_lo_propio_da_403_y_los_demas_errores_su_codigo() throws Exception {
        when(bloqueos.aprobar(eq(OPERADOR), any())).thenThrow(new CuatroOjosException());
        when(bloqueos.aprobar(eq(SUPERVISOR), any())).thenThrow(new SolicitudDeBloqueoNoValidaException("ya"));
        when(bloqueos.iniciar(any(), any(), any())).thenThrow(new NoSuchElementException("no existe"));

        mvc.perform(post(RUTA + "/" + pedida.id() + "/aprobacion").header("X-Ayni-Usuario", OPERADOR)
                        .header("X-Ayni-Permisos", "cliente:bloquear_aprobar"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CUATRO_OJOS"));
        mvc.perform(post(RUTA + "/" + pedida.id() + "/aprobacion").header("X-Ayni-Usuario", SUPERVISOR)
                        .header("X-Ayni-Permisos", "cliente:bloquear_aprobar"))
                .andExpect(status().isConflict());
        mvc.perform(post(RUTA).header("X-Ayni-Usuario", OPERADOR).header("X-Ayni-Permisos", "cliente:bloquear_iniciar")
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isNotFound());
    }
}
