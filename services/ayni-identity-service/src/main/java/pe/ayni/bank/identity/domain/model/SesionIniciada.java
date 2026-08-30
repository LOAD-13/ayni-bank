package pe.ayni.bank.identity.domain.model;

import java.time.Duration;
import java.time.Instant;

/**
 * El resultado de un ingreso completo.
 *
 * <p>El token de renovacion viaja aqui <strong>en claro</strong>, y es la unica vez que
 * existe fuera del navegador: de la base de datos solo se guarda su huella. Quien reciba
 * este objeto tiene que ponerlo en la cookie y olvidarlo.
 */
public record SesionIniciada(String tokenDeAcceso, Instant elAccesoExpiraEn,
                             String tokenDeRenovacion, Instant laRenovacionExpiraEn) {

    /** Vigencia del token de acceso exigida por HU-04. */
    public static final Duration VIGENCIA_DEL_ACCESO = Duration.ofMinutes(15);

    @Override
    public String toString() {
        return "SesionIniciada[tokenDeAcceso=oculto, tokenDeRenovacion=oculto]";
    }
}
