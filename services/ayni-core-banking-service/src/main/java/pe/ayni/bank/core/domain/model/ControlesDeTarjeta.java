package pe.ayni.bank.core.domain.model;

import java.util.Objects;

/**
 * Los controles vigentes de la tarjeta de debito virtual del titular y el consumo acumulado
 * en el dia actual (hora de Lima) · HU-10 (AYNI-21).
 */
public record ControlesDeTarjeta(
        EstadoDeTarjeta estado,
        String ultimos4,
        Moneda moneda,
        Dinero limiteDiario,
        Dinero consumidoHoy,
        boolean comprasPorInternet) {

    public ControlesDeTarjeta {
        Objects.requireNonNull(estado, "El estado de la tarjeta es obligatorio.");
        Objects.requireNonNull(ultimos4, "Los ultimos 4 digitos son obligatorios.");
        Objects.requireNonNull(moneda, "La moneda es obligatoria.");
        Objects.requireNonNull(limiteDiario, "El limite diario es obligatorio.");
        Objects.requireNonNull(consumidoHoy, "El importe consumido hoy es obligatorio.");
        if (!limiteDiario.esPositivo()) {
            throw new IllegalArgumentException("El limite diario debe ser mayor que cero.");
        }
        if (consumidoHoy.esNegativo()) {
            throw new IllegalArgumentException("El consumo del dia no puede ser negativo.");
        }
        if (limiteDiario.moneda() != moneda || consumidoHoy.moneda() != moneda) {
            throw new MonedasIncompatiblesException(moneda,
                    limiteDiario.moneda() != moneda ? limiteDiario.moneda() : consumidoHoy.moneda());
        }
    }
}
