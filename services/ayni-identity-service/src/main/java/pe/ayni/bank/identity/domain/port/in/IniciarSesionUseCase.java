package pe.ayni.bank.identity.domain.port.in;

import pe.ayni.bank.identity.domain.model.ComandoDeIngreso;
import pe.ayni.bank.identity.domain.model.ComandoDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.DesafioAbierto;
import pe.ayni.bank.identity.domain.model.HuellaDeCliente;
import pe.ayni.bank.identity.domain.model.SesionIniciada;

/**
 * Puerto de entrada de HU-04.
 *
 * <p>El ingreso son dos pasos porque las pantallas aprobadas son dos: «Entra a tu banca» y
 * «Confirma que eres tu». Partirlo en dos operaciones evita que la segunda pantalla tenga
 * que volver a mandar la contrasena, y por tanto que el navegador la guarde entre una y
 * otra.
 */
public interface IniciarSesionUseCase {

    /**
     * Paso 1: correo y contrasena.
     *
     * @throws pe.ayni.bank.identity.domain.model.CredencialesInvalidasException
     *         si el correo no existe o la contrasena no coincide. El mensaje es el mismo en
     *         ambos casos, a proposito
     * @throws pe.ayni.bank.identity.domain.model.CuentaBloqueadaException
     *         si el ingreso esta pausado por intentos fallidos
     */
    DesafioAbierto presentarCredenciales(ComandoDeIngreso comando);

    /**
     * Paso 2: el codigo de la aplicacion de autenticacion.
     *
     * @throws pe.ayni.bank.identity.domain.model.SegundoFactorInvalidoException
     *         si el desafio no existe, caduco, o el codigo no es valido
     */
    SesionIniciada verificarSegundoFactor(ComandoDeSegundoFactor comando);

    /**
     * Renueva la sesion rotando el token.
     *
     * @throws pe.ayni.bank.identity.domain.model.ReutilizacionDeRefreshTokenException
     *         si el token ya se habia consumido. Invalida la familia entera
     * @throws pe.ayni.bank.identity.domain.model.SesionExpiradaException
     *         si el token no existe o caduco
     */
    SesionIniciada renovar(String tokenDeRenovacion, HuellaDeCliente cliente);
}
