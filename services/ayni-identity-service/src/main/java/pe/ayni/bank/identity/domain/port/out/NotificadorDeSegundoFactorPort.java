package pe.ayni.bank.identity.domain.port.out;

import pe.ayni.bank.identity.domain.model.Celular;
import pe.ayni.bank.identity.domain.model.CorreoElectronico;

/** Envío del código OTP de 6 dígitos al canal de contacto elegido (HU-22 / AYNI-128). */
public interface NotificadorDeSegundoFactorPort {

    void enviarCodigoPorCorreo(CorreoElectronico correo, String codigo);

    void enviarCodigoPorSms(Celular celular, String codigo);
}
