package pe.ayni.bank.core.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Un cambio de moneda en tres piezas, sin mezclar nunca monedas en un asiento (HU-12):
 *
 * <ol>
 *   <li>{@code salida}: en la moneda de origen, cargo al cliente y abono a la posicion cambiaria
 *       del banco en esa moneda;</li>
 *   <li>la conversion misma, este registro, que une las dos patas con el tipo aplicado;</li>
 *   <li>{@code entrada}: en la moneda de destino, cargo a la posicion cambiaria del banco en esa
 *       moneda y abono al cliente.</li>
 * </ol>
 *
 * <p>Cada pata es un {@link Movimiento} que cuadra a cero por si solo. Con el importe de origen y
 * el tipo aplicado se recalcula el importe recibido (escenario 3).
 */
public record Conversion(UUID id, UUID usuarioId, Movimiento salida, Movimiento entrada,
                         TipoDeCambioAplicado tipo, Instant registradaEn) {

    public Conversion {
        Objects.requireNonNull(id);
        Objects.requireNonNull(usuarioId);
        Objects.requireNonNull(salida);
        Objects.requireNonNull(entrada);
        Objects.requireNonNull(tipo);
        Objects.requireNonNull(registradaEn);
        if (salida.importe().moneda() == entrada.importe().moneda()) {
            throw new IllegalArgumentException("Una conversion cambia de moneda.");
        }
    }

    /**
     * @param saldoOrigen saldo de {@code origen} leido con la cuenta ya bloqueada
     * @param posicionOrigen posicion cambiaria del banco en la moneda de {@code origen}
     * @param posicionDestino posicion cambiaria del banco en la moneda de {@code destino}
     */
    public static Conversion de(UUID id, Cuenta origen, Dinero saldoOrigen, Cuenta destino,
                                Cuenta posicionOrigen, Cuenta posicionDestino, Dinero importe,
                                TipoDeCambioAplicado tipo, Instant momento) {
        Movimiento.exigirOperativa(origen);
        Movimiento.exigirOperativa(destino);
        if (!origen.usuarioId().equals(destino.usuarioId())) {
            throw new IllegalArgumentException("Solo se convierte entre cuentas del mismo titular.");
        }
        if (origen.moneda() == destino.moneda() || importe.moneda() != origen.moneda()
                || posicionOrigen.moneda() != origen.moneda() || posicionDestino.moneda() != destino.moneda()) {
            throw new OperacionRechazadaException(MotivoDeRechazo.MONEDA_DISTINTA);
        }
        Movimiento.exigirDentroDeLimites(importe, Movimiento.MAXIMO_TRANSFERENCIA);
        if (saldoOrigen.menos(importe).esNegativo()) {
            throw new OperacionRechazadaException(MotivoDeRechazo.SALDO_INSUFICIENTE);
        }
        Dinero recibido = tipo.convertir(importe);
        if (!recibido.esPositivo()) {
            throw new OperacionRechazadaException(MotivoDeRechazo.IMPORTE_FUERA_DE_LIMITE);
        }

        String detalle = "Cambio " + importe.moneda() + " a " + recibido.moneda()
                + " · tipo " + tipo.solesPorDolar().toPlainString();
        UUID salidaId = UUID.randomUUID();
        UUID entradaId = UUID.randomUUID();
        Movimiento salida = new Movimiento(salidaId, TipoDeMovimiento.CONVERSION,
                new Asiento(UUID.randomUUID(), origen.id(), salidaId, TipoDeAsiento.CARGO, importe,
                        Movimiento.recortar(detalle), momento),
                new Asiento(UUID.randomUUID(), posicionOrigen.id(), salidaId, TipoDeAsiento.ABONO, importe,
                        Movimiento.recortar("Posicion cambiaria · " + detalle), momento));
        Movimiento entrada = new Movimiento(entradaId, TipoDeMovimiento.CONVERSION,
                new Asiento(UUID.randomUUID(), posicionDestino.id(), entradaId, TipoDeAsiento.CARGO, recibido,
                        Movimiento.recortar("Posicion cambiaria · " + detalle), momento),
                new Asiento(UUID.randomUUID(), destino.id(), entradaId, TipoDeAsiento.ABONO, recibido,
                        Movimiento.recortar(detalle), momento));
        return new Conversion(id, origen.usuarioId(), salida, entrada, tipo, momento);
    }

    public Dinero importeOrigen() {
        return salida.importe();
    }

    public Dinero importeDestino() {
        return entrada.importe();
    }

    public List<Asiento> asientos() {
        return List.of(salida.cargo(), salida.abono(), entrada.cargo(), entrada.abono());
    }
}
