package pe.ayni.bank.identity.domain.port.out;

import pe.ayni.bank.identity.domain.model.CorreoElectronico;

/**
 * Correos de HU-21.
 *
 * <p>Las implementaciones <strong>no deben bloquear</strong> a quien llama: si el envio se
 * hiciera dentro de la peticion, la respuesta tardaria mas cuando la cuenta existe y el
 * cronometro delataria lo que el cuerpo identico oculta (ADR-0008, ADR-0030).
 */
public interface NotificadorDeRecuperacionPort {

    /** @param tokenEnClaro va solo en el enlace del correo; nunca a un log */
    void enviarEnlaceDeRecuperacion(CorreoElectronico correo, String tokenEnClaro);

    /** Aviso al titular de que su contrasena cambio, por si no fue el. */
    void avisarContrasenaCambiada(CorreoElectronico correo);
}
