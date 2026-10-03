package pe.ayni.bank.core.infrastructure.in.web;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import pe.ayni.bank.core.domain.model.Comprobante;
import pe.ayni.bank.core.domain.model.MotivoDeRechazo;
import pe.ayni.bank.core.domain.model.TipoDeMovimiento;

/**
 * KPIs de negocio para Grafana (AYNI-159).
 *
 * <ul>
 *   <li>{@code ayni_operaciones_total{tipo, resultado}}: operaciones aceptadas y rechazadas.</li>
 *   <li>{@code ayni_operaciones_rechazos_total{motivo}}: por que se rechazan.</li>
 *   <li>{@code ayni_operaciones_importe_soles_total{tipo}}: volumen movido.</li>
 * </ul>
 *
 * <p>Ninguna etiqueta identifica al cliente ni a la cuenta: una metrica la lee cualquiera
 * con acceso a Grafana, y las etiquetas con valores ilimitados ademas revientan Prometheus.
 */
@Component
public class MetricasDeOperaciones {

    private final MeterRegistry registro;

    public MetricasDeOperaciones(MeterRegistry registro) {
        this.registro = registro;
        registrarEnCero();
    }

    /**
     * Crea todas las series en 0 al arrancar. Un contador que nace con la primera operacion
     * aparece ya valiendo 1, y {@code increase()} de Prometheus no ve aumento en una serie sin
     * muestra anterior: el primer deposito o la primera transferencia tras cada despliegue no
     * se contaban y el tablero de KPIs quedaba en 0 o sin datos.
     */
    private void registrarEnCero() {
        for (TipoDeMovimiento tipo : TipoDeMovimiento.values()) {
            for (String resultado : new String[] {"ACEPTADA", "RECHAZADA"}) {
                Counter.builder("ayni.operaciones")
                        .description("Operaciones monetarias por tipo y resultado")
                        .tags("tipo", tipo.name(), "resultado", resultado)
                        .register(registro);
            }
            Counter.builder("ayni.operaciones.importe.soles")
                    .description("Importe movido, en soles")
                    .tag("tipo", tipo.name())
                    .register(registro);
        }
        for (MotivoDeRechazo motivo : MotivoDeRechazo.values()) {
            Counter.builder("ayni.operaciones.rechazos")
                    .description("Operaciones rechazadas por motivo")
                    .tag("motivo", motivo.name())
                    .register(registro);
        }
    }

    public void aceptada(Comprobante comprobante) {
        String tipo = comprobante.tipo().name();
        Counter.builder("ayni.operaciones")
                .description("Operaciones monetarias por tipo y resultado")
                .tags("tipo", tipo, "resultado", "ACEPTADA")
                .register(registro)
                .increment();
        Counter.builder("ayni.operaciones.importe.soles")
                .description("Importe movido, en soles")
                .tag("tipo", tipo)
                .register(registro)
                // Un contador de Prometheus es double. Es un indicador para el tablero, no
                // contabilidad: el importe exacto sigue en los asientos, en BigDecimal.
                .increment(comprobante.importe().importe().doubleValue());
    }

    public void rechazada(TipoDeMovimiento tipo, MotivoDeRechazo motivo) {
        Counter.builder("ayni.operaciones")
                .description("Operaciones monetarias por tipo y resultado")
                .tags("tipo", tipo.name(), "resultado", "RECHAZADA")
                .register(registro)
                .increment();
        Counter.builder("ayni.operaciones.rechazos")
                .description("Operaciones rechazadas por motivo")
                .tag("motivo", motivo.name())
                .register(registro)
                .increment();
    }
}
