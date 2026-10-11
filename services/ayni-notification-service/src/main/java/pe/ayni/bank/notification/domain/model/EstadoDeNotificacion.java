package pe.ayni.bank.notification.domain.model;

public enum EstadoDeNotificacion {
    PENDIENTE,
    ENVIADA,
    /** Agoto los reintentos. Queda registrada para revisarla a mano. */
    FALLIDA
}
