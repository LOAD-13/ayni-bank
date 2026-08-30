package pe.ayni.bank.core.domain.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Recuerda que operaciones ya se ejecutaron.
 *
 * <p>RabbitMQ garantiza que un mensaje llega «al menos una vez», no «exactamente una». Un
 * corte de red en el momento equivocado hace que el mismo evento se entregue dos veces, y
 * sin este registro eso serian dos cuentas para la misma persona. Es el mismo invariante
 * que exige el documento de diseno para toda operacion monetaria.
 */
public interface RegistroDeIdempotenciaPort {

    /** @return el resultado anterior si esta operacion ya se ejecuto */
    Optional<UUID> resultadoDe(UUID claveDeIdempotencia);

    void recordar(UUID claveDeIdempotencia, UUID resultado);
}
