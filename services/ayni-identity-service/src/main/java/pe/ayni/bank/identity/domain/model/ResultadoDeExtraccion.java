package pe.ayni.bank.identity.domain.model;

/**
 * Respuesta a la lectura de los datos del DNI.
 *
 * @param lectura solo cuando {@code estado} es {@code ACEPTADO}
 * @param intentosRestantes solo cuando {@code estado} es {@code RECHAZADO}: el OCR no pudo
 *        leer el documento y hay que repetir la foto del reverso
 */
public record ResultadoDeExtraccion(EstadoDelPasoKyc estado, LecturaDelDni lectura, int intentosRestantes) {

    public static ResultadoDeExtraccion leida(LecturaDelDni lectura) {
        return new ResultadoDeExtraccion(EstadoDelPasoKyc.ACEPTADO, lectura, 0);
    }

    public static ResultadoDeExtraccion ilegible(ResultadoDelIntentoKyc intento) {
        if (intento.derivadaARevisionManual()) {
            return enRevisionManual();
        }
        return new ResultadoDeExtraccion(EstadoDelPasoKyc.RECHAZADO, null, intento.intentosRestantes());
    }

    public static ResultadoDeExtraccion enRevisionManual() {
        return new ResultadoDeExtraccion(EstadoDelPasoKyc.EN_REVISION_MANUAL, null, 0);
    }

    public static ResultadoDeExtraccion diferida() {
        return new ResultadoDeExtraccion(EstadoDelPasoKyc.VERIFICACION_DIFERIDA, null, 0);
    }
}
