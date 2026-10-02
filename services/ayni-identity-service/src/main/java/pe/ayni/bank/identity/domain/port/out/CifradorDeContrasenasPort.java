package pe.ayni.bank.identity.domain.port.out;

import pe.ayni.bank.identity.domain.model.ContrasenaCifrada;

/**
 * Derivacion de contrasenas.
 *
 * <p>Es un puerto y no una llamada directa a Argon2 para que el dominio no dependa de la
 * biblioteca de criptografia. Cuando Argon2id deje de ser la recomendacion —OWASP revisa
 * sus parametros cada pocos anos— cambia el adaptador y el dominio no se entera.
 */
public interface CifradorDeContrasenasPort {

    /**
     * @param contrasenaEnClaro no se registra en ningun log ni se conserva tras la llamada
     */
    ContrasenaCifrada cifrar(String contrasenaEnClaro);

    boolean coincide(String contrasenaEnClaro, ContrasenaCifrada cifrada);
}
