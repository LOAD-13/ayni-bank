package pe.ayni.bank.notification.domain.model;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Lo que hay que contarle a un titular sobre un movimiento.
 *
 * @param eventoId el identificador del evento de origen; con el titular y el tipo, es la
 *                 clave que impide avisar dos veces del mismo hecho
 * @param datos    los valores de la plantilla. Cuentas enmascaradas, nunca datos sensibles
 */
public record AvisoDeMovimiento(UUID eventoId, UUID usuarioId, TipoDeAviso tipo, Map<String, String> datos) {

    public AvisoDeMovimiento {
        Objects.requireNonNull(eventoId, "eventoId");
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(tipo, "tipo");
        datos = Map.copyOf(datos);
    }
}
