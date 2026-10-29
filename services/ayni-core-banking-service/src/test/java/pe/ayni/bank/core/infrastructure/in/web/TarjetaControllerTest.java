package pe.ayni.bank.core.infrastructure.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import pe.ayni.bank.core.domain.model.ConfirmacionInvalidaException;
import pe.ayni.bank.core.domain.model.CvvDinamico;
import pe.ayni.bank.core.domain.model.Pan;
import pe.ayni.bank.core.domain.model.PanRevelado;
import pe.ayni.bank.core.domain.model.SinTarjetaException;
import pe.ayni.bank.core.domain.model.Tarjeta;
import pe.ayni.bank.core.domain.port.in.ConsultarTarjetaUseCase;

/** El contrato HTTP de HU-09: que viaja de la tarjeta y que responde cada error. */
class TarjetaControllerTest {

    private static final UUID ROSA = UUID.randomUUID();
    private static final Tarjeta TARJETA = Tarjeta.emitir(UUID.randomUUID(), ROSA, new Pan("4999990000000007"),
            Instant.parse("2026-10-28T15:00:00Z"));

    private final ConsultarTarjetaUseCase tarjetas = mock(ConsultarTarjetaUseCase.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new TarjetaController(tarjetas)).build();

    @Test
    @DisplayName("La tarjeta viaja solo con los ultimos 4 digitos: el PAN completo no aparece")
    void soloLosUltimos4() throws Exception {
        when(tarjetas.deTitular(ROSA)).thenReturn(Optional.of(TARJETA));

        mvc.perform(get("/api/v1/tarjetas/mia").header(TarjetaController.CABECERA_USUARIO, ROSA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ultimos4").value("0007"))
                .andExpect(jsonPath("$.numeroEnmascarado").value("•••• •••• •••• 0007"))
                .andExpect(jsonPath("$.vencimiento").value("10/30"))
                .andExpect(jsonPath("$.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.numero").doesNotExist());
    }

    @Test
    @DisplayName("Sin tarjeta: 404, que la web muestra como «tu tarjeta aun no esta lista»")
    void sinTarjeta() throws Exception {
        when(tarjetas.deTitular(ROSA)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/tarjetas/mia").header(TarjetaController.CABECERA_USUARIO, ROSA))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("El CVV dinamico y el PAN revelado no se guardan en ninguna cache")
    void cvvYRevelacionSinCache() throws Exception {
        Instant hasta = Instant.parse("2026-10-28T15:05:00Z");
        when(tarjetas.cvv(ROSA)).thenReturn(new CvvDinamico("123", hasta));
        when(tarjetas.revelar(eq(ROSA), eq("token"), any(), eq("203.0.113.25")))
                .thenReturn(new PanRevelado("4999 9900 0000 0007", hasta));

        mvc.perform(get("/api/v1/tarjetas/mia/cvv").header(TarjetaController.CABECERA_USUARIO, ROSA))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.cvv").value("123"));
        mvc.perform(post("/api/v1/tarjetas/mia/revelacion")
                        .header(TarjetaController.CABECERA_USUARIO, ROSA)
                        .header(TarjetaController.CABECERA_IDEMPOTENCIA, UUID.randomUUID())
                        .header(TarjetaController.CABECERA_CONFIRMACION, "token")
                        .header("X-Forwarded-For", "203.0.113.25, 10.0.0.2"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.numero").value("4999 9900 0000 0007"));
        verify(tarjetas).revelar(eq(ROSA), eq("token"), any(), eq("203.0.113.25"));
    }

    @Test
    @DisplayName("Revelar sin segundo factor: 403 para que la web pida la confirmacion")
    void revelarSinConfirmacion() throws Exception {
        when(tarjetas.revelar(eq(ROSA), any(), any(), any())).thenThrow(new ConfirmacionInvalidaException());

        mvc.perform(post("/api/v1/tarjetas/mia/revelacion")
                        .header(TarjetaController.CABECERA_USUARIO, ROSA)
                        .header(TarjetaController.CABECERA_IDEMPOTENCIA, UUID.randomUUID()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CONFIRMACION_REQUERIDA"));
    }

    @Test
    @DisplayName("Sin la cabecera del gateway: 401; sin la clave de la confirmacion: 400")
    void cabecerasQueFaltan() throws Exception {
        mvc.perform(get("/api/v1/tarjetas/mia")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/tarjetas/mia/revelacion").header(TarjetaController.CABECERA_USUARIO, ROSA))
                .andExpect(status().isBadRequest());
        when(tarjetas.cvv(ROSA)).thenThrow(new SinTarjetaException());
        mvc.perform(get("/api/v1/tarjetas/mia/cvv").header(TarjetaController.CABECERA_USUARIO, ROSA))
                .andExpect(status().isNotFound());
    }
}
