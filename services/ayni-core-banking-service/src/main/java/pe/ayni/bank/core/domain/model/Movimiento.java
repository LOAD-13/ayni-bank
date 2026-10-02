package pe.ayni.bank.core.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Una operacion monetaria completa: el conjunto de asientos que comparten
 * {@code movimientoId}.
 *
 * <p><strong>Partida doble.</strong> Todo movimiento tiene al menos un cargo y un abono, y
 * la suma de sus efectos es exactamente cero. El constructor lo comprueba: no existe forma
 * de construir un movimiento que cree o destruya dinero. Una transferencia carga al
 * ordenante y abona al beneficiario; un deposito carga a la cuenta tecnica de fondeo del
 * banco y abona al cliente.
 *
 * <p>Las reglas de negocio —saldo suficiente, limites, cuentas operativas— se comprueban en
 * las fabricas y no en el servicio: son del dominio, y asi no hay camino que las salte.
 */
public record Movimiento(UUID id, TipoDeMovimiento tipo, Asiento cargo, Asiento abono) {

    /** Importe minimo de cualquier operacion: un centimo no es una transferencia. */
    public static final BigDecimal MINIMO = new BigDecimal("1.00");

    /** Limite por transferencia entre cuentas Ayni. */
    public static final BigDecimal MAXIMO_TRANSFERENCIA = new BigDecimal("5000.00");

    /** Limite por deposito simulado (entorno academico). */
    public static final BigDecimal MAXIMO_DEPOSITO = new BigDecimal("2000.00");

    public Movimiento {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tipo);
        Objects.requireNonNull(cargo);
        Objects.requireNonNull(abono);
        if (cargo.tipo() != TipoDeAsiento.CARGO || abono.tipo() != TipoDeAsiento.ABONO) {
            throw new IllegalArgumentException("Un movimiento es un cargo y un abono.");
        }
        if (!cargo.movimientoId().equals(id) || !abono.movimientoId().equals(id)) {
            throw new IllegalArgumentException("Los asientos no pertenecen a este movimiento.");
        }
        if (!cargo.efecto().mas(abono.efecto()).esCero()) {
            throw new IllegalArgumentException("La partida doble no cuadra: el movimiento no suma cero.");
        }
    }

    /**
     * Transferencia entre dos cuentas Ayni.
     *
     * @param saldoOrigen saldo disponible del ordenante en este instante, leido con la
     *                    cuenta bloqueada: si se leyera sin bloqueo, dos transferencias
     *                    simultaneas podrian gastar el mismo saldo.
     */
    public static Movimiento transferencia(UUID id, Cuenta origen, Dinero saldoOrigen,
                                           Cuenta destino, Dinero importe, String concepto,
                                           Instant momento) {
        if (origen.equals(destino)) {
            throw new OperacionRechazadaException(MotivoDeRechazo.MISMA_CUENTA);
        }
        exigirOperativa(origen);
        exigirOperativa(destino);
        if (origen.moneda() != destino.moneda() || importe.moneda() != origen.moneda()) {
            throw new OperacionRechazadaException(MotivoDeRechazo.MONEDA_DISTINTA);
        }
        exigirDentroDeLimites(importe, MAXIMO_TRANSFERENCIA);
        if (saldoOrigen.menos(importe).esNegativo()) {
            throw new OperacionRechazadaException(MotivoDeRechazo.SALDO_INSUFICIENTE);
        }

        String detalle = concepto == null || concepto.isBlank() ? "Transferencia" : concepto.trim();
        return new Movimiento(id, TipoDeMovimiento.TRANSFERENCIA,
                new Asiento(UUID.randomUUID(), origen.id(), id, TipoDeAsiento.CARGO, importe,
                        recortar(detalle + " · a " + destino.numero().enmascarado()), momento),
                new Asiento(UUID.randomUUID(), destino.id(), id, TipoDeAsiento.ABONO, importe,
                        recortar(detalle + " · de " + origen.numero().enmascarado()), momento));
    }

    /**
     * Deposito simulado: dinero que entra desde fuera del banco.
     *
     * <p>En produccion real llegaria por una transferencia interbancaria (HU-11) o un
     * agente. Mientras eso no existe, la contrapartida es la cuenta tecnica de fondeo del
     * banco, cuyo saldo negativo es exactamente el total de dinero que ha entrado. Asi se
     * respeta la partida doble y el dinero no aparece de la nada.
     */
    public static Movimiento depositoSimulado(UUID id, Cuenta fondeo, Cuenta destino,
                                              Dinero importe, Instant momento) {
        exigirOperativa(destino);
        if (importe.moneda() != destino.moneda() || fondeo.moneda() != destino.moneda()) {
            throw new OperacionRechazadaException(MotivoDeRechazo.MONEDA_DISTINTA);
        }
        exigirDentroDeLimites(importe, MAXIMO_DEPOSITO);

        return new Movimiento(id, TipoDeMovimiento.DEPOSITO_SIMULADO,
                new Asiento(UUID.randomUUID(), fondeo.id(), id, TipoDeAsiento.CARGO, importe,
                        "Fondeo · deposito simulado a " + destino.numero().enmascarado(), momento),
                new Asiento(UUID.randomUUID(), destino.id(), id, TipoDeAsiento.ABONO, importe,
                        "Deposito simulado", momento));
    }

    public Dinero importe() {
        return abono.importe();
    }

    public List<Asiento> asientos() {
        return List.of(cargo, abono);
    }

    private static void exigirOperativa(Cuenta cuenta) {
        if (!cuenta.admiteMovimientos()) {
            throw new OperacionRechazadaException(MotivoDeRechazo.CUENTA_NO_OPERATIVA);
        }
    }

    private static void exigirDentroDeLimites(Dinero importe, BigDecimal maximo) {
        if (importe.importe().compareTo(MINIMO) < 0 || importe.importe().compareTo(maximo) > 0) {
            throw new OperacionRechazadaException(MotivoDeRechazo.IMPORTE_FUERA_DE_LIMITE);
        }
    }

    private static String recortar(String concepto) {
        return concepto.length() <= 140 ? concepto : concepto.substring(0, 140);
    }
}
