package pe.ayni.bank.identity.infrastructure.out.persistence;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import pe.ayni.bank.identity.domain.model.HuellaDeCliente;
import pe.ayni.bank.identity.domain.model.TipoDeEventoDeAcceso;
import pe.ayni.bank.identity.domain.port.out.PistaDeAuditoriaPort;

/**
 * Implementa {@link PistaDeAuditoriaPort} sobre JPA.
 *
 * <p>Cada evento suma tambien en {@code ayni_acceso_eventos_total{tipo}}, que alimenta los
 * KPIs de ingreso en Grafana (AYNI-159). La metrica solo lleva el tipo: ni usuario ni IP.
 */
@Repository
public class AdaptadorPistaDeAuditoria implements PistaDeAuditoriaPort {

    private final EventoAuditoriaJpaRepository repositorio;
    private final Clock reloj;
    private final MeterRegistry metricas;

    AdaptadorPistaDeAuditoria(EventoAuditoriaJpaRepository repositorio, Clock reloj,
                              MeterRegistry metricas) {
        this.repositorio = repositorio;
        this.reloj = reloj;
        this.metricas = metricas;
        // Todas las series en 0 al arrancar: increase() de Prometheus no ve el primer evento
        // de una serie que nace ya valiendo 1, y el tablero de accesos quedaba en 0.
        for (TipoDeEventoDeAcceso tipo : TipoDeEventoDeAcceso.values()) {
            contador(tipo);
        }
    }

    private Counter contador(TipoDeEventoDeAcceso tipo) {
        return Counter.builder("ayni.acceso.eventos")
                .description("Eventos de acceso: ingresos, fallos, bloqueos y renovaciones")
                .tag("tipo", tipo.name())
                .register(metricas);
    }

    @Override
    public void registrar(TipoDeEventoDeAcceso tipo, UUID usuarioId,
                          HuellaDeCliente cliente) {
        repositorio.save(new EventoAuditoriaEntity(
                tipo.name(), usuarioId, cliente.ip(), cliente.agenteDeUsuario(),
                reloj.instant()));
        contador(tipo).increment();
    }
}
