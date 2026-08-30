package pe.ayni.bank.identity.infrastructure.in.web;

import pe.ayni.bank.identity.domain.model.DesafioAbierto;

/**
 * Respuesta del paso 1.
 *
 * <p>{@code uriDeAprovisionamiento} solo viene cuando el usuario aun no tiene segundo
 * factor: lleva el secreto dentro y la pantalla lo convierte en codigo QR. En cuanto el
 * segundo factor queda confirmado, este campo es nulo para siempre.
 */
public record DesafioDto(String desafioId, boolean requiereInscripcion,
                         String uriDeAprovisionamiento) {

    public static DesafioDto desde(DesafioAbierto desafio) {
        return new DesafioDto(
                desafio.desafioId().toString(),
                desafio.requiereInscripcion(),
                desafio.uriDeAprovisionamiento());
    }

    @Override
    public String toString() {
        return "DesafioDto[desafioId=" + desafioId
                + ", requiereInscripcion=" + requiereInscripcion + "]";
    }
}
