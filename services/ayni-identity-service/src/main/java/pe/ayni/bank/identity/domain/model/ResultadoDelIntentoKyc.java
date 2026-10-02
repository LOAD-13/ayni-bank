package pe.ayni.bank.identity.domain.model;

/**
 * Que pasa con la solicitud despues de registrar un intento de verificacion fallido.
 *
 * <p>Ver ADR-0021 y ADR-0026: al tercer fallo de un mismo lado del DNI la solicitud se
 * deriva a revision manual en vez de dejar que el usuario siga intentando indefinidamente.
 *
 * @param intentosRestantes cuantas fotos mas de ese lado puede enviar; 0 si se derivo
 */
public record ResultadoDelIntentoKyc(boolean derivadaARevisionManual, int intentosRestantes) {

    public ResultadoDelIntentoKyc {
        if (intentosRestantes < 0) {
            throw new IllegalArgumentException("Los intentos restantes no pueden ser negativos.");
        }
        if (derivadaARevisionManual && intentosRestantes != 0) {
            throw new IllegalArgumentException("Una solicitud derivada no tiene intentos restantes.");
        }
    }

    public static ResultadoDelIntentoKyc puedeReintentar(int intentosRestantes) {
        return new ResultadoDelIntentoKyc(false, intentosRestantes);
    }

    public static ResultadoDelIntentoKyc derivada() {
        return new ResultadoDelIntentoKyc(true, 0);
    }
}
