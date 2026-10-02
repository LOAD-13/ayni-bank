package pe.ayni.bank.identity.domain.model;

import java.util.UUID;

/** Lo que devuelve kyc-service al aceptar una verificacion (sin filtrar VerificationResponse). */
public record ResultadoDeVerificacionKyc(UUID verificacionId, EstadoDeVerificacionKyc estado) {
}
