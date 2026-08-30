package pe.ayni.bank.identity.infrastructure.in.web;

import java.time.Instant;

import pe.ayni.bank.identity.domain.model.SesionIniciada;

/**
 * Respuesta de un ingreso completo.
 *
 * <p><strong>El token de renovacion no esta aqui</strong>, y su ausencia es la decision de
 * seguridad principal de HU-04: viaja en una cookie {@code HttpOnly} que JavaScript no
 * puede leer. Si estuviera en este cuerpo, cualquier XSS en la aplicacion web se llevaria
 * siete dias de sesion.
 */
public record SesionDto(String tokenDeAcceso, Instant expiraEn) {

    public static SesionDto desde(SesionIniciada sesion) {
        return new SesionDto(sesion.tokenDeAcceso(), sesion.elAccesoExpiraEn());
    }

    @Override
    public String toString() {
        return "SesionDto[tokenDeAcceso=oculto, expiraEn=" + expiraEn + "]";
    }
}
