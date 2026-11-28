package pe.ayni.bank.core.domain.port.in;

import java.util.UUID;

import pe.ayni.bank.core.domain.model.Cotizacion;
import pe.ayni.bank.core.domain.model.Moneda;

/** Calcula el tipo de cambio que se le aplicaria hoy a un cliente, sin mover dinero (HU-12). */
public interface CotizarConversionUseCase {

    /**
     * @param origen la moneda que entrega el cliente; recibe la otra
     * @throws pe.ayni.bank.core.domain.model.FuenteDeTipoDeCambioNoDisponibleException si no hay ni
     *         fuente ni ultimo valor conocido
     */
    Cotizacion cotizar(UUID usuarioId, Moneda origen);
}
