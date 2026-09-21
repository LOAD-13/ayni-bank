package pe.ayni.bank.identity.infrastructure.in.web;

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

import pe.ayni.bank.identity.domain.model.CodigoDesafioInvalidoException;
import pe.ayni.bank.identity.domain.model.DesafioPorCodigo;
import pe.ayni.bank.identity.domain.model.ResultadoGeneracionDesafio;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;
import pe.ayni.bank.identity.domain.port.in.GenerarDesafioCodigoUseCase;
import pe.ayni.bank.identity.domain.port.in.VerificarDesafioCodigoUseCase;

@WebMvcTest(DesafioCodigoController.class)
@Import(ManejadorDeErrores.class)
@AutoConfigureMockMvc(addFilters = false)
class DesafioCodigoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GenerarDesafioCodigoUseCase generarDesafioUseCase;

    @MockitoBean
    private VerificarDesafioCodigoUseCase verificarDesafioUseCase;

    @Test
    @DisplayName("POST /api/v1/segundo-factor/desafio/usuario/{usuarioId}/generar genera un nuevo desafío")
    void generarDesafio_Exito() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        DesafioPorCodigo desafio = DesafioPorCodigo.generar(
                UUID.randomUUID(), usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, "hash123", Instant.now());

        when(generarDesafioUseCase.generar(eq(usuarioId), eq(TipoDeSegundoFactor.CORREO_ELECTRONICO)))
                .thenReturn(new ResultadoGeneracionDesafio(desafio, "123456"));

        SolicitudGenerarDesafioDto dto = new SolicitudGenerarDesafioDto(TipoDeSegundoFactor.CORREO_ELECTRONICO);

        mockMvc.perform(post("/api/v1/segundo-factor/desafio/usuario/" + usuarioId + "/generar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(usuarioId.toString()))
                .andExpect(jsonPath("$.tipoFactor").value("CORREO_ELECTRONICO"));
    }

    @Test
    @DisplayName("POST /api/v1/segundo-factor/desafio/{desafioId}/verificar verifica código exitosamente")
    void verificarDesafio_Exito() throws Exception {
        UUID desafioId = UUID.randomUUID();
        when(verificarDesafioUseCase.verificar(eq(desafioId), eq("123456"))).thenReturn(true);

        SolicitudVerificarDesafioDto dto = new SolicitudVerificarDesafioDto("123456");

        mockMvc.perform(post("/api/v1/segundo-factor/desafio/" + desafioId + "/verificar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/segundo-factor/desafio/{desafioId}/verificar retorna 400 cuando código es inválido")
    void verificarDesafio_CodigoInvalido() throws Exception {
        UUID desafioId = UUID.randomUUID();
        when(verificarDesafioUseCase.verificar(eq(desafioId), eq("000000")))
                .thenThrow(new CodigoDesafioInvalidoException());

        SolicitudVerificarDesafioDto dto = new SolicitudVerificarDesafioDto("000000");

        mockMvc.perform(post("/api/v1/segundo-factor/desafio/" + desafioId + "/verificar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }
}
