package pe.ayni.bank.core.domain.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pe.ayni.bank.core.domain.model.Asiento;
import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.Moneda;

/** Persistencia de cuentas y de sus asientos. */
public interface RepositorioDeCuentasPort {

    boolean existeCuentaActiva(UUID usuarioId, Moneda moneda);

    Optional<Cuenta> buscarActivaDe(UUID usuarioId, Moneda moneda);

    /**
     * Siguiente valor del correlativo de cuentas.
     *
     * <p>Lo entrega la base y no la aplicacion: garantizar que dos peticiones simultaneas
     * no obtengan el mismo numero es exactamente lo que una secuencia sabe hacer y un
     * contador en memoria no.
     */
    long siguienteCorrelativo();

    Cuenta guardar(Cuenta cuenta);

    List<Asiento> asientosDe(UUID cuentaId);

    /**
     * La TREA vigente del producto de la cuenta, en tanto por ciento.
     *
     * <p>Se lee del catalogo y no se escribe en el codigo. La tasa cambia, y una cifra
     * copiada a mano en una pantalla es una promesa que el banco puede acabar incumpliendo
     * sin darse cuenta.
     */
    Optional<java.math.BigDecimal> treaVigenteDe(short productoId);
}
