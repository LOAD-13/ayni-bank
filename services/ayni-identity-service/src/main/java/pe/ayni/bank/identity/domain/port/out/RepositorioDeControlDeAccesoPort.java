package pe.ayni.bank.identity.domain.port.out;

import java.util.UUID;

import pe.ayni.bank.identity.domain.model.ControlDeAcceso;

/** Contador de intentos fallidos y estado de bloqueo. */
public interface RepositorioDeControlDeAccesoPort {

    /** Devuelve el control limpio si el usuario nunca ha fallado. */
    ControlDeAcceso cargar(UUID usuarioId);

    void guardar(ControlDeAcceso control);
}
