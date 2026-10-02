package pe.ayni.bank.identity.domain.port.out;

import java.time.Instant;

import pe.ayni.bank.identity.domain.model.CodigoTotp;
import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.model.SecretoTotp;

/**
 * TOTP segun RFC 6238.
 *
 * <p>Es un puerto porque el algoritmo es criptografia, no negocio. Lo que si es negocio
 * —seis digitos, treinta segundos, una ventana de tolerancia— vive en los objetos de valor
 * y en el caso de uso.
 */
public interface GeneradorDeTotpPort {

    SecretoTotp generarSecreto();

    /**
     * @param momento cuando llego el codigo. Se admite tambien la ventana anterior y la
     *                siguiente: entre que el usuario lee el codigo y lo teclea pasan
     *                segundos, y los relojes de los moviles no van sincronizados al
     *                milisegundo. Sin esa tolerancia, un codigo correcto falla por poco
     *                y el usuario no entiende por que.
     */
    boolean verificar(SecretoTotp secreto, CodigoTotp codigo, Instant momento);

    /**
     * URI {@code otpauth://totp/...} que la pantalla convierte en codigo QR.
     *
     * <p>Lleva el secreto dentro, asi que solo se entrega por HTTPS al propio usuario y
     * jamas se registra en un log.
     */
    String uriDeAprovisionamiento(SecretoTotp secreto, CorreoElectronico correo);
}
