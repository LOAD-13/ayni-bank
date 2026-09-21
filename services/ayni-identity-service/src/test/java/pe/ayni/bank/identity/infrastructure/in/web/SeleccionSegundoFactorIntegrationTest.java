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

import pe.ayni.bank.identity.domain.model.DesafioPorCodigo;
import pe.ayni.bank.identity.domain.model.MetodoDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.ResultadoGeneracionDesafio;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;
import pe.ayni.bank.identity.domain.port.in.GenerarDesafioCodigoUseCase;
import pe.ayni.bank.identity.domain.port.in.SeleccionarSegundoFactorUseCase;
import pe.ayni.bank.identity.domain.port.in.VerificarDesafioCodigoUseCase;

@WebMvcTest({MetodoSegundoFactorController.class, DesafioCodigoController.class})
@Import(ManejadorDeErrores.class)
@AutoConfigureMockMvc(addFilters = false)
class SeleccionSegundoFactorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SeleccionarSegundoFactorUseCase seleccionarSegundoFactorUseCase;

    @MockitoBean
    private GenerarDesafioCodigoUseCase generarDesafioUseCase;

    @MockitoBean
    private VerificarDesafioCodigoUseCase verificarDesafioUseCase;

    @Test
    @DisplayName("Flujo de integración de selección de 2FA por correo electrónico")
    void flujoSeleccionYDesafioCorreo() throws Exception {
        UUID usuarioId = UUID.randomUUID();

        // 1. Seleccionar método de segundo factor (CORREO_ELECTRONICO)
        MetodoDeSegundoFactor metodo = MetodoDeSegundoFactor.inscribir(
                UUID.randomUUID(), usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, null, Instant.now());
        when(seleccionarSegundoFactorUseCase.seleccionarMetodo(eq(usuarioId), eq(TipoDeSegundoFactor.CORREO_ELECTRONICO)))
                .thenReturn(metodo);

        SolicitudSeleccionMetodoDto cuerpoMetodo = new SolicitudSeleccionMetodoDto(TipoDeSegundoFactor.CORREO_ELECTRONICO);

        mockMvc.perform(post("/api/v1/usuarios/" + usuarioId + "/segundo-factor/metodo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cuerpoMetodo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(usuarioId.toString()))
                .andExpect(jsonPath("$.tipo").value("CORREO_ELECTRONICO"));

        // 2. Generar desafío OTP
        DesafioPorCodigo desafio = DesafioPorCodigo.generar(
                UUID.randomUUID(), usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, "hash123", Instant.now());

        when(generarDesafioUseCase.generar(eq(usuarioId), eq(TipoDeSegundoFactor.CORREO_ELECTRONICO)))
                .thenReturn(new ResultadoGeneracionDesafio(desafio, "123456"));

        SolicitudGenerarDesafioDto cuerpoDesafio = new SolicitudGenerarDesafioDto(TipoDeSegundoFactor.CORREO_ELECTRONICO);

        mockMvc.perform(post("/api/v1/segundo-factor/desafio/usuario/" + usuarioId + "/generar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cuerpoDesafio)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(usuarioId.toString()))
                .andExpect(jsonPath("$.tipoFactor").value("CORREO_ELECTRONICO"))
                .andExpect(jsonPath("$.verificado").value(false));
    }
}
