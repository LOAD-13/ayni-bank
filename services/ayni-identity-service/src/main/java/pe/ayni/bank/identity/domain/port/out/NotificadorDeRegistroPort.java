package pe.ayni.bank.identity.domain.port.out;

import pe.ayni.bank.identity.domain.model.CorreoElectronico;

/**
 * Avisos que salen del registro.
 *
 * <p>Son dos y no uno, y esa es la clave de la antienumeracion: quien se registra recibe
 * la bienvenida; quien ya tenia cuenta recibe el aviso de que alguien intento registrarse
 * con su direccion. Desde fuera, la respuesta HTTP es la misma en ambos casos. Desde la
 * bandeja de entrada del titular legitimo, no: el se entera del intento, que es
 * exactamente a quien le interesa saberlo.
 */
public interface NotificadorDeRegistroPort {

    void enviarBienvenida(CorreoElectronico correo);

    void avisarIntentoDeRegistroSobreCuentaExistente(CorreoElectronico correo);
}
