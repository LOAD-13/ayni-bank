package pe.ayni.bank.identity.domain.port.in;

import pe.ayni.bank.identity.domain.model.HuellaDeCliente;

/** HU-21 · Recuperacion de la contrasena por correo. */
public interface RecuperarContrasenaUseCase {

    /**
     * Pide un enlace de recuperacion. No devuelve nada a proposito: el resultado es el mismo
     * exista o no la cuenta (ADR-0008).
     */
    void solicitar(String correo, HuellaDeCliente cliente);

    /**
     * Comprueba que el enlace sigue sirviendo, para no pedir una contrasena nueva que luego
     * se rechazaria.
     *
     * @throws pe.ayni.bank.identity.domain.model.EnlaceDeRecuperacionInvalidoException si no sirve
     */
    void validar(String tokenEnClaro);

    /**
     * Fija la contrasena nueva, gasta el enlace y cierra todas las sesiones del titular.
     *
     * @param contrasenaNueva en claro; no se registra en ningun log
     */
    void restablecer(String tokenEnClaro, String contrasenaNueva, HuellaDeCliente cliente);
}
