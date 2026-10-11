package pe.ayni.bank.core.infrastructure.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import pe.ayni.bank.core.domain.model.Devengo;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.Rendimiento;
import pe.ayni.bank.core.domain.model.TasaVigente;
import pe.ayni.bank.core.domain.port.in.ConsultarRendimientoUseCase;

class RendimientoControllerTest {

    private final ConsultarRendimientoUseCase consultar = mock(ConsultarRendimientoUseCase.class);
    private final RendimientoController controller = new RendimientoController(consultar);

    @Test
    @DisplayName("muestra TREA = TEA, lo ganado en el mes y cada devengo con su tasa")
    void rendimiento() {
        UUID ana = UUID.randomUUID();
        var tasa = new TasaVigente(1, new BigDecimal("0.045000"), new BigDecimal("0.045000"),
                LocalDate.parse("2026-08-17"));
        var devengo = new Devengo(UUID.randomUUID(), UUID.randomUUID(), LocalDate.parse("2026-10-15"),
                Dinero.de("1000.00", Moneda.PEN), 1, new BigDecimal("0.050000"), new BigDecimal("0.13553742"),
                Instant.parse("2026-10-16T05:05:00Z"));
        when(consultar.deTitular(ana)).thenReturn(Optional.of(new Rendimiento(tasa, Moneda.PEN,
                new BigDecimal("2.03306130"), Dinero.de("4.20", Moneda.PEN), List.of(devengo))));

        var r = controller.miRendimiento(ana).getBody();

        assertThat(r).isNotNull();
        assertThat(r.tea()).isEqualTo("4.50");
        assertThat(r.trea()).isEqualTo("4.50");
        assertThat(r.treaIgualATea()).isTrue();
        assertThat(r.devengadoDelMes()).isEqualTo("2.03306130");
        assertThat(r.devengadoDelMesRedondeado()).isEqualTo("2.03");
        assertThat(r.abonadoTotal()).isEqualTo("4.20");
        assertThat(r.devengos()).singleElement().isEqualTo(
                new RendimientoController.DevengoDto("2026-10-15", "1000.00", "5.00", "0.13553742"));
    }

    @Test
    @DisplayName("sin cuenta activa responde 404")
    void sinCuenta() {
        UUID nadie = UUID.randomUUID();
        when(consultar.deTitular(nadie)).thenReturn(Optional.empty());
        assertThat(controller.miRendimiento(nadie).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
