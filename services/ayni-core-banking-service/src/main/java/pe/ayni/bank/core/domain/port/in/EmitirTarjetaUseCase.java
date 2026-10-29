package pe.ayni.bank.core.domain.port.in;

import java.util.UUID;

import pe.ayni.bank.core.domain.model.Tarjeta;

/** HU-09: la tarjeta de debito virtual del titular, emitida una sola vez. */
public interface EmitirTarjetaUseCase {

    /** Emite la tarjeta asociada a la cuenta; si el titular ya tiene una, la devuelve. */
    Tarjeta emitirPara(UUID usuarioId, UUID cuentaId);
}
