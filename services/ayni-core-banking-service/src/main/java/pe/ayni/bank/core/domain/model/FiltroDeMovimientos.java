package pe.ayni.bank.core.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Criterios de búsqueda y paginación sobre el historial de movimientos de una cuenta · HU-08.
 */
public record FiltroDeMovimientos(
        LocalDate desde,
        LocalDate hasta,
        TipoDeAsiento tipo,
        BigDecimal importeMinimo,
        BigDecimal importeMaximo,
        int pagina,
        int tamano) {

    private static final int TAMANO_POR_DEFECTO = 20;
    private static final int TAMANO_MAXIMO = 50;

    public FiltroDeMovimientos {
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new IllegalArgumentException("La fecha final no puede ser anterior a la inicial.");
        }
        if (importeMinimo != null && importeMaximo != null && importeMaximo.compareTo(importeMinimo) < 0) {
            throw new IllegalArgumentException("El importe maximo no puede ser menor que el minimo.");
        }
        if (pagina < 0) {
            throw new IllegalArgumentException("La pagina no puede ser negativa.");
        }
        if (tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw new IllegalArgumentException("El tamano de pagina debe estar entre 1 y " + TAMANO_MAXIMO + ".");
        }
    }

    public static FiltroDeMovimientos porFechas(LocalDate desde, LocalDate hasta) {
        return new FiltroDeMovimientos(desde, hasta, null, null, null, 0, TAMANO_POR_DEFECTO);
    }

    public boolean cumple(Asiento asiento) {
        LocalDate fecha = asiento.registradoEn().atZone(ZoneOffset.UTC).toLocalDate();
        if (desde != null && fecha.isBefore(desde)) {
            return false;
        }
        if (hasta != null && fecha.isAfter(hasta)) {
            return false;
        }
        if (tipo != null && asiento.tipo() != tipo) {
            return false;
        }
        BigDecimal importe = asiento.importe().importe();
        if (importeMinimo != null && importe.compareTo(importeMinimo) < 0) {
            return false;
        }
        return importeMaximo == null || importe.compareTo(importeMaximo) <= 0;
    }
}
