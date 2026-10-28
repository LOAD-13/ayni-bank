package pe.ayni.bank.core.infrastructure.interbancaria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import pe.ayni.bank.core.application.usecase.OperarCuentaService.ClaveDeIdempotenciaReutilizadaException;
import pe.ayni.bank.core.domain.model.ConfirmacionInvalidaException;
import pe.ayni.bank.core.infrastructure.interbancaria.RechazoInterbancarioException.Motivo;
import pe.ayni.bank.core.infrastructure.interbancaria.TransferenciaInterbancariaService.Beneficiario;
import pe.ayni.bank.core.infrastructure.interbancaria.TransferenciaInterbancariaService.Resultado;
import pe.ayni.bank.core.infrastructure.interbancaria.TransferenciaInterbancariaService.Solicitud;

/** El contrato HTTP de HU-11: de donde sale la identidad y que responde cada error. */
class InterbancariaControllerTest {

    private static final UUID ANA = UUID.randomUUID();
    private static final UUID CLAVE = UUID.randomUUID();
    private static final String CUERPO = """
            {"cci":"00219300001234567886","importe":"120.50","concepto":"Alquiler"}""";

    private final TransferenciaInterbancariaService servicio = mock(TransferenciaInterbancariaService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new InterbancariaController(servicio)).build();

    private ResultActions transferir(String cuerpo) throws Exception {
        return mvc.perform(post("/api/v1/interbancarias")
                .header(InterbancariaController.CABECERA_USUARIO, ANA)
                .header(InterbancariaController.CABECERA_IDEMPOTENCIA, CLAVE)
                .header(InterbancariaController.CABECERA_CONFIRMACION, "token")
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    @Test
    @DisplayName("El titular sale de la cabecera del gateway y el importe del texto, sin coma flotante")
    void iniciaConLaIdentidadDeLaCabecera() throws Exception {
        UUID id = UUID.randomUUID();
        when(servicio.iniciar(any(), eq("token"))).thenReturn(new Resultado(id, EstadoInterbancario.PENDIENTE));
        when(servicio.consultar(id, ANA)).thenReturn(new Resultado(id, EstadoInterbancario.COMPLETADA));

        transferir(CUERPO).andExpect(status().isAccepted()).andExpect(jsonPath("$.estado").value("COMPLETADA"));

        ArgumentCaptor<Solicitud> solicitud = ArgumentCaptor.forClass(Solicitud.class);
        verify(servicio).iniciar(solicitud.capture(), eq("token"));
        assertThat(solicitud.getValue().usuarioId()).isEqualTo(ANA);
        assertThat(solicitud.getValue().clave()).isEqualTo(CLAVE);
        assertThat(solicitud.getValue().importe()).hasToString("120.50");
        verify(servicio).despachar(id);
    }

    @Test
    @DisplayName("Sin la cabecera del gateway no hay titular: 401")
    void sinCabeceraDeUsuario() throws Exception {
        mvc.perform(post("/api/v1/interbancarias").header(InterbancariaController.CABECERA_IDEMPOTENCIA, CLAVE)
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Sesion requerida"));
    }

    @Test
    @DisplayName("Sin clave de idempotencia: 400 que dice que cabecera falta")
    void sinClave() throws Exception {
        mvc.perform(post("/api/v1/interbancarias").header(InterbancariaController.CABECERA_USUARIO, ANA)
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Falta la cabecera Idempotency-Key."));
    }

    @Test
    @DisplayName("Un rechazo de negocio responde con su estado, su codigo y un detalle que dice que hacer")
    void rechazoDeNegocio() throws Exception {
        when(servicio.iniciar(any(), any())).thenThrow(new RechazoInterbancarioException(Motivo.CCI_INVALIDO));

        transferir(CUERPO).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CCI_INVALIDO"))
                .andExpect(jsonPath("$.detail").value(Motivo.CCI_INVALIDO.detalle()));
    }

    @Test
    @DisplayName("Sin segundo factor: 403 para que la web pida la confirmacion")
    void sinConfirmacion() throws Exception {
        when(servicio.iniciar(any(), any())).thenThrow(new ConfirmacionInvalidaException());

        transferir(CUERPO).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CONFIRMACION_REQUERIDA"));
    }

    @Test
    @DisplayName("La misma clave con otros datos: 409")
    void claveReutilizada() throws Exception {
        when(servicio.iniciar(any(), any())).thenThrow(new ClaveDeIdempotenciaReutilizadaException());

        transferir(CUERPO).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Un importe que no es texto con dos decimales se rechaza antes de llegar al servicio")
    void importeMalFormado() throws Exception {
        transferir("""
                {"cci":"00219300001234567886","importe":"12,50"}""").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Consultar y gestionar beneficiarios siempre es sobre el titular de la cabecera")
    void consultasYBeneficiarios() throws Exception {
        UUID id = UUID.randomUUID();
        UUID beneficiario = UUID.randomUUID();
        when(servicio.consultar(id, ANA)).thenReturn(new Resultado(id, EstadoInterbancario.RECHAZADA));
        when(servicio.beneficiarios(ANA)).thenReturn(List.of(new Beneficiario(beneficiario, "Mama", "00219300001234567886")));
        when(servicio.guardarBeneficiario(ANA, "Mama", "00219300001234567886"))
                .thenReturn(new Beneficiario(beneficiario, "Mama", "00219300001234567886"));

        mvc.perform(get("/api/v1/interbancarias/{id}", id).header(InterbancariaController.CABECERA_USUARIO, ANA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("RECHAZADA"));
        mvc.perform(get("/api/v1/interbancarias/beneficiarios").header(InterbancariaController.CABECERA_USUARIO, ANA))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].alias").value("Mama"));
        mvc.perform(post("/api/v1/interbancarias/beneficiarios").header(InterbancariaController.CABECERA_USUARIO, ANA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alias\":\"Mama\",\"cci\":\"00219300001234567886\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(beneficiario.toString()));
        mvc.perform(delete("/api/v1/interbancarias/beneficiarios/{id}", beneficiario)
                        .header(InterbancariaController.CABECERA_USUARIO, ANA))
                .andExpect(status().isNoContent());
        verify(servicio).borrarBeneficiario(ANA, beneficiario);
    }

    @Test
    @DisplayName("La validacion en vivo del CCI responde si o no sin tocar datos")
    void validarCci() throws Exception {
        mvc.perform(get("/api/v1/interbancarias/validar-cci/{cci}", "00219300001234567886"))
                .andExpect(status().isOk()).andExpect(jsonPath("$").value(true));
        mvc.perform(get("/api/v1/interbancarias/validar-cci/{cci}", "00219300001234567887"))
                .andExpect(status().isOk()).andExpect(jsonPath("$").value(false));
    }
}
