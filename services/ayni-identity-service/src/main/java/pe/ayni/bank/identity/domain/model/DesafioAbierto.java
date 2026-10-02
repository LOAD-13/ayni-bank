package pe.ayni.bank.identity.domain.model;

import java.util.UUID;

/**
 * Lo que devuelve el primer paso del ingreso.
 *
 * <p>Cuando el usuario todavia no tiene segundo factor, {@code uriDeAprovisionamiento}
 * trae el {@code otpauth://} con el que la pantalla pinta el codigo QR. Cuando ya lo tiene
 * confirmado, viene nulo y la pantalla se limita a pedir el codigo.
 *
 * <p>El campo se llama {@code requiereInscripcion} y no {@code esNuevo} a proposito: lo que
 * la interfaz necesita saber es que pantalla mostrar, no como llego el usuario hasta aqui.
 */
public record DesafioAbierto(UUID desafioId, boolean requiereInscripcion,
                             String uriDeAprovisionamiento) {

    public static DesafioAbierto paraQuienYaTieneSegundoFactor(UUID desafioId) {
        return new DesafioAbierto(desafioId, false, null);
    }

    public static DesafioAbierto conInscripcion(UUID desafioId, String uri) {
        return new DesafioAbierto(desafioId, true, uri);
    }

    /** El URI lleva el secreto dentro: no puede acabar en un log. */
    @Override
    public String toString() {
        return "DesafioAbierto[desafioId=" + desafioId
                + ", requiereInscripcion=" + requiereInscripcion + "]";
    }
}
