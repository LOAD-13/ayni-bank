package pe.ayni.bank.identity.infrastructure.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import pe.ayni.bank.identity.domain.model.DesafioPorCodigo;
import pe.ayni.bank.identity.domain.model.ResultadoGeneracionDesafio;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;
import pe.ayni.bank.identity.domain.port.in.GenerarDesafioCodigoUseCase;
import pe.ayni.bank.identity.domain.port.in.VerificarContactoRegistroUseCase;

@WebMvcTest(VerificacionContactoController.class)
@Import(ManejadorDeErrores.class)
@AutoConfigureMockMvc(addFilters = false)
class VerificacionContactoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GenerarDesafioCodigoUseCase generarDesafioUseCase;

    @MockitoBean
    private VerificarContactoRegistroUseCase verificarContactoUseCase;

    @Test
    @DisplayName("POST /api/v1/registro/{usuarioId}/codigo/reenviar reenvía un nuevo código OTP")
    void reenviarCodigo_Exito() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        DesafioPorCodigo desafio = DesafioPorCodigo.generar(
                UUID.randomUUID(), usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, "hash456", Instant.now());

        when(generarDesafioUseCase.generar(eq(usuarioId), eq(TipoDeSegundoFactor.CORREO_ELECTRONICO)))
                .thenReturn(new ResultadoGeneracionDesafio(desafio, "654321"));

        SolicitudReenvioCodigoDto dto = new SolicitudReenvioCodigoDto(TipoDeSegundoFactor.CORREO_ELECTRONICO);

        mockMvc.perform(post("/api/v1/registro/" + usuarioId + "/codigo/reenviar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(usuarioId.toString()))
                .andExpect(jsonPath("$.tipoFactor").value("CORREO_ELECTRONICO"));
    }

    @Test
    @DisplayName("POST /api/v1/registro/{usuarioId}/codigo/verificar verifica el código enviado")
    void verificarCodigo_Exito() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        when(verificarContactoUseCase.verificarContacto(any())).thenReturn(true);

        SolicitudVerificarCodigoRegistroDto dto = new SolicitudVerificarCodigoRegistroDto(
                TipoDeSegundoFactor.CORREO_ELECTRONICO, "654321");

        mockMvc.perform(post("/api/v1/registro/" + usuarioId + "/codigo/verificar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }
}
