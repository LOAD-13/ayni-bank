package pe.ayni.bank.identity.domain.port.in;

import pe.ayni.bank.identity.domain.model.ComandoDeRegistro;
import pe.ayni.bank.identity.domain.model.ResultadoDeRegistro;

/** Puerto de entrada de HU-01. Lo invoca el controlador REST; lo implementa la aplicacion. */
public interface RegistrarVisitanteUseCase {

    /**
     * @throws pe.ayni.bank.identity.domain.model.ConsentimientoNoOtorgadoException
     *         si no se aceptaron los terminos
     * @throws pe.ayni.bank.identity.domain.model.ContrasenaInvalidaException
     *         si la contrasena incumple la politica
     */
    ResultadoDeRegistro registrar(ComandoDeRegistro comando);
}
