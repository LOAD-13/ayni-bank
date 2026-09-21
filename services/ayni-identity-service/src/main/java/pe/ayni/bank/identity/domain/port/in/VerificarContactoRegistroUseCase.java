package pe.ayni.bank.identity.domain.port.in;

import pe.ayni.bank.identity.domain.model.SolicitudVerificacionContacto;

/** Puerto de entrada para verificar el contacto primario durante el registro (HU-22 / AYNI-130). */
public interface VerificarContactoRegistroUseCase {

    boolean verificarContacto(SolicitudVerificacionContacto solicitud);
}
