package pe.ayni.bank.core.domain.port.in;

import java.util.UUID;

import pe.ayni.bank.core.domain.model.ControlesDeTarjeta;
import pe.ayni.bank.core.domain.model.Dinero;

/**
 * Controles y autorizacion de consumos de la tarjeta de debito virtual · HU-10 (AYNI-21).
 */
public interface ControlarTarjetaUseCase {

    ControlesDeTarjeta consultarControles(UUID usuarioId);

    ControlesDeTarjeta congelar(UUID usuarioId);

    ControlesDeTarjeta descongelar(UUID usuarioId);

    ControlesDeTarjeta configurar(UUID usuarioId, Dinero limiteDiario, boolean comprasPorInternet);

    void consumir(UUID usuarioId, Dinero importe, boolean porInternet, String comercio);
}
