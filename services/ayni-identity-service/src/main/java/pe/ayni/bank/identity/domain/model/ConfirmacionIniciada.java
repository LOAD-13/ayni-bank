package pe.ayni.bank.identity.domain.model;

import java.time.Instant;
import java.util.UUID;

/** HU-07: la confirmacion abierta y el metodo con el que el titular debe confirmarla. */
public record ConfirmacionIniciada(UUID confirmacionId, TipoDeSegundoFactor metodo, Instant expiraEn) {
}
