package pe.ayni.bank.identity.domain.port.out;

import java.time.Instant;

import pe.ayni.bank.identity.domain.model.TokenDeRenovacion;
import pe.ayni.bank.identity.domain.model.Usuario;

/**
 * Emision del token de acceso.
 *
 * <p>El dominio no sabe que es un JWT ni con que se firma. Sabe que hay algo que acredita
 * a un usuario durante quince minutos, y eso es todo lo que necesita saber para razonar
 * sobre el ingreso.
 */
public interface EmisorDeTokensDeAccesoPort {

    String emitir(Usuario usuario, Instant momento, Instant expiraEn);

    /**
     * Valor aleatorio para el token de renovacion, y su huella.
     *
     * <p>Ver {@link TokenDeRenovacion} para por que los dos viajan juntos.
     */
    TokenDeRenovacion generarTokenDeRenovacion();

    /** Huella de un token que llega del navegador, para buscarlo en la base. */
    String huellaDe(String tokenEnClaro);
}
