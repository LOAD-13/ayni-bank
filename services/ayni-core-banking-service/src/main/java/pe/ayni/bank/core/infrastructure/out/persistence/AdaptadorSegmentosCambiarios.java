package pe.ayni.bank.core.infrastructure.out.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.SegmentoCambiario;
import pe.ayni.bank.core.domain.port.out.SegmentosCambiariosPort;

/** Implementa {@link SegmentosCambiariosPort} sobre {@code segmento_cambiario} y {@code devengo_interes}. */
@Repository
public class AdaptadorSegmentosCambiarios implements SegmentosCambiariosPort {

    private final SegmentoCambiarioJpaRepository filas;

    AdaptadorSegmentosCambiarios(SegmentoCambiarioJpaRepository filas) {
        this.filas = filas;
    }

    @Override
    public List<SegmentoCambiario> segmentos() {
        return filas.findAll().stream()
                .map(f -> new SegmentoCambiario(f.getCodigo(), f.getSpread(), f.getSaldoPromedioMinimo()))
                .toList();
    }

    @Override
    public BigDecimal saldoPromedioEnSoles(UUID usuarioId, LocalDate desde, LocalDate hasta) {
        long dias = ChronoUnit.DAYS.between(desde, hasta) + 1;
        if (dias <= 0) {
            throw new IllegalArgumentException("El rango de fechas esta al reves");
        }
        return filas.sumaDeSaldosDeCierre(usuarioId, desde, hasta)
                .divide(BigDecimal.valueOf(dias), Dinero.DECIMALES, Dinero.REDONDEO);
    }
}
