package pe.ayni.bank.identity.infrastructure.in.web;

import static org.mockito.ArgumentMatchers.any;
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
import pe.ayni.bank.identity.domain.port.in.VerificarContactoRegistroUseCase;

@WebMvcTest(VerificacionContactoController.class)
@Import(ManejadorDeErrores.class)
@AutoConfigureMockMvc(addFilters = false)
class VerificacionContactoRegistroIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GenerarDesafioCodigoUseCase generarDesafioUseCase;

    @MockitoBean
    private VerificarContactoRegistroUseCase verificarContactoUseCase;

    @Test
    @DisplayName("Flujo de integración: solicita reenvío de código y lo verifica exitosamente")
    void flujoCompletoReenvioYVerificacionContacto() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        DesafioPorCodigo desafio = DesafioPorCodigo.generar(
                UUID.randomUUID(), usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, "hash999", Instant.now());

        // 1. Reenviar código OTP de contacto
        when(generarDesafioUseCase.generar(usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO))
                .thenReturn(new ResultadoGeneracionDesafio(desafio, "987654"));

        SolicitudReenvioCodigoDto cuerpoReenvio = new SolicitudReenvioCodigoDto(TipoDeSegundoFactor.CORREO_ELECTRONICO);

        mockMvc.perform(post("/api/v1/registro/" + usuarioId + "/codigo/reenviar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cuerpoReenvio)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(usuarioId.toString()))
                .andExpect(jsonPath("$.tipoFactor").value("CORREO_ELECTRONICO"));

        // 2. Verificar código OTP de contacto correcto
        when(verificarContactoUseCase.verificarContacto(any())).thenReturn(true);

        SolicitudVerificarCodigoRegistroDto cuerpoVerificacion = new SolicitudVerificarCodigoRegistroDto(
                TipoDeSegundoFactor.CORREO_ELECTRONICO, "987654");

        mockMvc.perform(post("/api/v1/registro/" + usuarioId + "/codigo/verificar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cuerpoVerificacion)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Flujo de integración: falla la verificación con un código inválido respondiendo 400")
    void flujoVerificacionContactoConCodigoInvalido() throws Exception {
        UUID usuarioId = UUID.randomUUID();

        when(verificarContactoUseCase.verificarContacto(any()))
                .thenThrow(new CodigoDesafioInvalidoException());

        SolicitudVerificarCodigoRegistroDto cuerpoVerificacion = new SolicitudVerificarCodigoRegistroDto(
                TipoDeSegundoFactor.CORREO_ELECTRONICO, "000000");

        mockMvc.perform(post("/api/v1/registro/" + usuarioId + "/codigo/verificar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cuerpoVerificacion)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://ayni.pe/problemas/codigo-desafio-invalido"));
    }
}
