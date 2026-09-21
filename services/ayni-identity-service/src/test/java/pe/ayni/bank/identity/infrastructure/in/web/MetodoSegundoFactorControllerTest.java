package pe.ayni.bank.identity.infrastructure.in.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import pe.ayni.bank.identity.domain.model.MetodoDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;
import pe.ayni.bank.identity.domain.port.in.SeleccionarSegundoFactorUseCase;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MetodoSegundoFactorController.class)
class MetodoSegundoFactorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SeleccionarSegundoFactorUseCase seleccionarSegundoFactor;

    @Test
    @DisplayName("POST /api/v1/usuarios/{usuarioId}/segundo-factor/metodo devuelve 200 con el metodo seleccionado")
    void testSeleccionarMetodo() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        UUID metodoId = UUID.randomUUID();

        MetodoDeSegundoFactor metodo = MetodoDeSegundoFactor.inscribir(
                metodoId, usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, null, Instant.now()
        );

        given(seleccionarSegundoFactor.seleccionarMetodo(eq(usuarioId), eq(TipoDeSegundoFactor.CORREO_ELECTRONICO)))
                .willReturn(metodo);

        mockMvc.perform(post("/api/v1/usuarios/{usuarioId}/segundo-factor/metodo", usuarioId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tipo": "CORREO_ELECTRONICO"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(metodoId.toString()))
                .andExpect(jsonPath("$.usuarioId").value(usuarioId.toString()))
                .andExpect(jsonPath("$.tipo").value("CORREO_ELECTRONICO"))
                .andExpect(jsonPath("$.confirmado").value(false));
    }
}
