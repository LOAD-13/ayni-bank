package pe.ayni.bank.identity.domain.model;

/**
 * Respuesta a la foto de un lado del DNI.
 *
 * @param motivo solo cuando {@code estado} es {@code RECHAZADO}
 * @param intentosRestantes fotos que quedan para ese lado antes de derivar a revision manual
 */
public record ResultadoDeCaptura(EstadoDelPasoKyc estado, MotivoDeRechazoDeCaptura motivo,
                                 int intentosRestantes) {

    public static ResultadoDeCaptura aceptada() {
        return new ResultadoDeCaptura(EstadoDelPasoKyc.ACEPTADO, null, 0);
    }

    public static ResultadoDeCaptura rechazada(MotivoDeRechazoDeCaptura motivo, ResultadoDelIntentoKyc intento) {
        if (intento.derivadaARevisionManual()) {
            return enRevisionManual();
        }
        return new ResultadoDeCaptura(EstadoDelPasoKyc.RECHAZADO, motivo, intento.intentosRestantes());
    }

    public static ResultadoDeCaptura enRevisionManual() {
        return new ResultadoDeCaptura(EstadoDelPasoKyc.EN_REVISION_MANUAL, null, 0);
    }

    public static ResultadoDeCaptura diferida() {
        return new ResultadoDeCaptura(EstadoDelPasoKyc.VERIFICACION_DIFERIDA, null, 0);
    }
}
