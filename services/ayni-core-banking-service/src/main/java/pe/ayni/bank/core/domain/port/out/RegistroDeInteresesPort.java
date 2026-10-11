package pe.ayni.bank.core.domain.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pe.ayni.bank.core.domain.model.Capitalizacion;
import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.Devengo;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.TasaVigente;

/** Lo que necesitan el cierre diario y la consulta de rendimiento (HU-06). */
public interface RegistroDeInteresesPort {

    Optional<LocalDate> ultimoCierre();

    boolean estaCerrado(LocalDate fecha);

    /** Las cuentas de ahorro de clientes, activas y abiertas antes del corte. Nunca las tecnicas. */
    List<Cuenta> cuentasQueDevengan(Instant corte);

    /** El saldo con los asientos registrados antes del corte: el saldo de cierre del dia. */
    Dinero saldoAlCorte(UUID cuentaId, Moneda moneda, Instant corte);

    /** La tasa del producto que regia ese dia, aunque despues haya cambiado. */
    Optional<TasaVigente> tasaVigente(short productoId, LocalDate fecha);

    void registrar(Devengo devengo);

    void registrarCierre(LocalDate fecha, int cuentas, BigDecimal devengado, Instant ahora);

    /** Las cuentas con devengos hasta esa fecha que aun no se abonaron. */
    List<UUID> cuentasConDevengoPendiente(LocalDate hasta);

    BigDecimal devengadoSinCapitalizar(UUID cuentaId, LocalDate hasta);

    /** La fraccion de centimo que dejo la ultima capitalizacion; cero si nunca hubo una. */
    BigDecimal ultimoResiduo(UUID cuentaId);

    /** Guarda la capitalizacion y le asigna los devengos pendientes hasta esa fecha. */
    void registrar(Capitalizacion capitalizacion, LocalDate hasta, Instant ahora);

    /** Los devengos de la cuenta, del mas reciente al mas antiguo. */
    List<Devengo> ultimosDevengos(UUID cuentaId, int limite);

    BigDecimal devengadoEntre(UUID cuentaId, LocalDate desde, LocalDate hasta);

    BigDecimal abonadoTotal(UUID cuentaId);
}
