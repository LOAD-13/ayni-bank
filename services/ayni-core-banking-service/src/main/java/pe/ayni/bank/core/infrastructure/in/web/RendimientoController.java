package pe.ayni.bank.core.infrastructure.in.web;

import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import pe.ayni.bank.core.domain.model.Devengo;
import pe.ayni.bank.core.domain.model.Rendimiento;
import pe.ayni.bank.core.domain.port.in.ConsultarRendimientoUseCase;

/**
 * HU-06: la TREA vigente, lo ganado en el mes y los devengos con la tasa con que se calcularon.
 *
 * <p>Los porcentajes salen ya como texto ({@code "4.50"}) y los importes con sus decimales:
 * la interfaz no hace aritmetica con dinero.
 */
@RestController
public class RendimientoController {

    private final ConsultarRendimientoUseCase consultar;

    public RendimientoController(ConsultarRendimientoUseCase consultar) {
        this.consultar = consultar;
    }

    @GetMapping("/api/v1/cuentas/mia/rendimiento")
    public ResponseEntity<RendimientoDto> miRendimiento(
            @RequestHeader(OperacionesController.CABECERA_USUARIO) UUID usuarioId) {
        return consultar.deTitular(usuarioId).map(RendimientoDto::desde)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * @param devengadoDelMes con 8 decimales, como se guarda
     * @param devengadoDelMesRedondeado a centimos, para mostrar
     */
    public record RendimientoDto(String tea, String trea, boolean treaIgualATea, String tasaVigenteDesde,
                                 String moneda, String devengadoDelMes, String devengadoDelMesRedondeado,
                                 String abonadoTotal, List<DevengoDto> devengos) {

        static RendimientoDto desde(Rendimiento r) {
            return new RendimientoDto(porcentaje(r.tasa().tea()), porcentaje(r.tasa().trea()),
                    r.tasa().trea().compareTo(r.tasa().tea()) == 0, r.tasa().vigenteDesde().toString(),
                    r.moneda().name(), r.devengadoDelMes().toPlainString(),
                    r.devengadoDelMes().setScale(2, RoundingMode.HALF_EVEN).toPlainString(),
                    r.abonadoTotal().importe().toPlainString(),
                    r.devengos().stream().map(DevengoDto::desde).toList());
        }
    }

    public record DevengoDto(String fecha, String saldoBase, String tea, String importe) {

        static DevengoDto desde(Devengo d) {
            return new DevengoDto(d.fecha().toString(), d.saldoBase().importe().toPlainString(),
                    porcentaje(d.tea()), d.importe().toPlainString());
        }
    }

    /** 0.045000 → "4.50". */
    static String porcentaje(java.math.BigDecimal tantoPorUno) {
        return tantoPorUno.movePointRight(2).setScale(2, RoundingMode.HALF_EVEN).toPlainString();
    }
}
