package pe.ayni.bank.notification.infrastructure.in.programado;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import pe.ayni.bank.notification.domain.model.ResultadoDelDespacho;
import pe.ayni.bank.notification.domain.port.in.DespacharNotificacionesUseCase;

/**
 * Lanza el despacho cada pocos segundos y cuenta el resultado.
 *
 * <p>En Prometheus aparece como {@code ayni_notificaciones_total{resultado=...}}: un aumento
 * de {@code fallida} es la senal de que SES o la proyeccion de titulares tienen un problema.
 */
@Component
public class DespachadorProgramado {

    private final DespacharNotificacionesUseCase despachar;
    private final Counter enviadas;
    private final Counter reprogramadas;
    private final Counter fallidas;

    public DespachadorProgramado(DespacharNotificacionesUseCase despachar, MeterRegistry metricas) {
        this.despachar = despachar;
        this.enviadas = contador(metricas, "enviada");
        this.reprogramadas = contador(metricas, "reprogramada");
        this.fallidas = contador(metricas, "fallida");
    }

    @Scheduled(fixedDelayString = "${ayni.notificaciones.despacho.cada}",
            initialDelayString = "${ayni.notificaciones.despacho.cada}")
    public void despachar() {
        ResultadoDelDespacho r = despachar.despacharPendientes();
        enviadas.increment(r.enviadas());
        reprogramadas.increment(r.reprogramadas());
        fallidas.increment(r.fallidas());
    }

    private static Counter contador(MeterRegistry metricas, String resultado) {
        return Counter.builder("ayni.notificaciones")
                .description("Intentos de envio de las notificaciones de movimientos, por resultado")
                .tag("resultado", resultado)
                .register(metricas);
    }
}
