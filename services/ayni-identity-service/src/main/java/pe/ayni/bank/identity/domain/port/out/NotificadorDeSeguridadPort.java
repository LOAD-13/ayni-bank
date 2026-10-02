package pe.ayni.bank.identity.domain.port.out;

import pe.ayni.bank.identity.domain.model.CorreoElectronico;

/** Avisos que solo tiene derecho a recibir el titular de la cuenta. */
public interface NotificadorDeSeguridadPort {

    /** Escenario 3 de HU-04: se pauso el ingreso tras cinco intentos fallidos. */
    void avisarIngresoPausado(CorreoElectronico correo);

    /** Escenario 4: se detecto la reutilizacion de un token y se cerro la sesion. */
    void avisarSesionCerradaPorSeguridad(CorreoElectronico correo);
}
