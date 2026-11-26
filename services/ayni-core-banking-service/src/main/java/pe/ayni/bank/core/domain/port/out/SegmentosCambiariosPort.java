package pe.ayni.bank.core.domain.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import pe.ayni.bank.core.domain.model.SegmentoCambiario;

/** El catalogo de segmentos cambiarios y lo que hace falta para ubicar a un cliente en uno (HU-12). */
public interface SegmentosCambiariosPort {

    List<SegmentoCambiario> segmentos();

    /**
     * Saldo promedio diario de la cuenta en soles del titular entre {@code desde} y {@code hasta},
     * ambos incluidos, a partir de los saldos de cierre que registra el devengo. Un dia sin
     * devengo —sin saldo— cuenta como cero. Sin cuenta o sin devengos, cero.
     */
    BigDecimal saldoPromedioEnSoles(UUID usuarioId, LocalDate desde, LocalDate hasta);
}
