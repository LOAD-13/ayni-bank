package pe.ayni.bank.notification.domain.model;

/** Lo que hizo una pasada del despachador. Alimenta las metricas. */
public record ResultadoDelDespacho(int enviadas, int reprogramadas, int fallidas) {
}
