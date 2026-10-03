package pe.ayni.bank.identity.domain.model;

import java.util.Objects;

/** Lo que dice kyc-service de una foto: si se acepta y, si no, por que. */
public record EvaluacionDeCaptura(boolean aceptada, MotivoDeRechazoDeCaptura motivo) {

    public EvaluacionDeCaptura {
        if (aceptada && motivo != null) {
            throw new IllegalArgumentException("Una captura aceptada no tiene motivo de rechazo.");
        }
        if (!aceptada) {
            Objects.requireNonNull(motivo, "Una captura rechazada necesita un motivo.");
        }
    }

    public static EvaluacionDeCaptura aprobada() {
        return new EvaluacionDeCaptura(true, null);
    }

    public static EvaluacionDeCaptura rechazada(MotivoDeRechazoDeCaptura motivo) {
        return new EvaluacionDeCaptura(false, motivo);
    }
}
