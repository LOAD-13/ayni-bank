package pe.ayni.bank.core.domain.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import pe.ayni.bank.core.domain.model.ControlesDeTarjeta;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.EstadoDeTarjeta;
import pe.ayni.bank.core.domain.model.Pan;
import pe.ayni.bank.core.domain.model.Tarjeta;

/**
 * Donde viven las tarjetas y sus controles · HU-09 y HU-10. Quien lo implemente guarda el PAN
 * cifrado y nunca en claro; el dominio no sabe como.
 */
public interface RepositorioDeTarjetasPort {

    Optional<Tarjeta> deTitular(UUID usuarioId);

    /** Si ya hay una tarjeta con ese PAN; la emision genera otro si choca. */
    boolean existePan(Pan pan);

    void guardar(Tarjeta tarjeta);

    /** HU-10: lee los controles vigentes y la suma consumida hoy (hora de Lima). */
    default ControlesDeTarjeta controlesDe(Tarjeta tarjeta, Instant ahora) {
        throw new UnsupportedOperationException();
    }

    /** HU-10: cambia el estado de la tarjeta (ACTIVA o CONGELADA) con efecto inmediato. */
    default void actualizarEstado(UUID tarjetaId, EstadoDeTarjeta estado) {
        throw new UnsupportedOperationException();
    }

    /** HU-10: reemplaza el limite diario y las compras por internet de la tarjeta. */
    default void actualizarControles(UUID tarjetaId, Dinero limiteDiario, boolean comprasPorInternet, Instant ahora) {
        throw new UnsupportedOperationException();
    }

    /** HU-10: registra un consumo aprobado con la tarjeta para acumular en el limite diario. */
    default void registrarConsumo(UUID tarjetaId, UUID movimientoId, Dinero importe,
                                  boolean porInternet, String comercio, Instant registradoEn) {
        throw new UnsupportedOperationException();
    }
}
