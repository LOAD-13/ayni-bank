package pe.ayni.bank.core.domain.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pe.ayni.bank.core.domain.model.Asiento;
import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.Movimiento;
import pe.ayni.bank.core.domain.model.NumeroDeCuenta;

/**
 * El libro mayor: lo que hace falta para mover dinero entre cuentas y consultarlo.
 *
 * <p>Separado de {@link RepositorioDeCuentasPort} a proposito. Aquel sirve a la apertura de
 * cuentas (HU-05); este a las operaciones (HU-07, HU-08). Cada caso de uso depende solo de
 * lo que usa.
 */
public interface LibroMayorPort {

    Optional<Cuenta> buscarPorNumero(NumeroDeCuenta numero);

    Optional<Cuenta> buscarPorId(UUID cuentaId);

    /** La cuenta tecnica del banco que hace de contrapartida de los depositos simulados. */
    Cuenta cuentaDeFondeo(Moneda moneda);

    /**
     * Bloquea las cuentas hasta el final de la transaccion ({@code SELECT ... FOR UPDATE}).
     *
     * <p>Sin el bloqueo, dos transferencias simultaneas desde la misma cuenta leerian el
     * mismo saldo y podrian gastarlo dos veces. Se bloquean siempre en el mismo orden para
     * que dos transferencias cruzadas (A→B y B→A) no se esperen mutuamente.
     */
    void bloquear(List<UUID> cuentaIds);

    /** El saldo como suma de asientos, calculada por la base. Ver ADR-0011. */
    Dinero saldoDe(UUID cuentaId, Moneda moneda);

    void registrar(Movimiento movimiento);

    /** Los ultimos asientos de la cuenta, del mas reciente al mas antiguo. */
    List<Asiento> ultimosAsientosDe(UUID cuentaId, int limite);

    List<Asiento> asientosDelMovimiento(UUID movimientoId);
}
