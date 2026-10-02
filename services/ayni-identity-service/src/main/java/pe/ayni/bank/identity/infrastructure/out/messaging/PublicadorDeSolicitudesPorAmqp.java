package pe.ayni.bank.identity.infrastructure.out.messaging;

import java.time.Clock;
import java.util.UUID;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import pe.ayni.bank.identity.domain.port.out.PublicadorDeSolicitudesPort;
import pe.ayni.bank.identity.infrastructure.config.ConfiguracionDeMensajeria;

/**
 * Publica en RabbitMQ que una solicitud quedo aprobada.
 *
 * <p><strong>Sin bandeja de salida, y eso es una deuda consciente.</strong> Lo correcto
 * segun ADR-0003 es escribir el evento en una tabla dentro de la misma transaccion y
 * publicarlo despues, exactamente como hace core-banking con {@code CuentaAperturada}. Aqui
 * se publica directo porque montar el segundo outbox se lleva por delante lo que queda de
 * sprint, y el riesgo concreto es acotado: si el envio falla, la solicitud queda aprobada y
 * la cuenta no se abre, cosa que se resuelve reprocesando.
 *
 * <p>Queda anotado en lugar de escondido, y con el mismo criterio que
 * {@code NotificadorDeRegistroPorEventos}: entra con HU-13, que es la que trae el outbox
 * completo a identidad.
 */
@Component
public class PublicadorDeSolicitudesPorAmqp implements PublicadorDeSolicitudesPort {

    private final RabbitTemplate rabbit;
    private final Clock reloj;

    public PublicadorDeSolicitudesPorAmqp(RabbitTemplate rabbit, Clock reloj) {
        this.rabbit = rabbit;
        this.reloj = reloj;
    }

    @Override
    public void anunciarAprobacion(UUID solicitudId, UUID usuarioId) {
        rabbit.convertAndSend(
                ConfiguracionDeMensajeria.EXCHANGE,
                ConfiguracionDeMensajeria.CLAVE_SOLICITUD_APROBADA,
                new SolicitudAprobada(solicitudId, usuarioId, reloj.instant().toString()));
    }

    /**
     * El contrato entre identidad y core-banking.
     *
     * <p>Se declara en los dos servicios en lugar de compartir una clase. Un modulo de
     * eventos comunes los acoplaria en tiempo de compilacion y obligaria a desplegarlos a
     * la vez, que es justo lo que los microservicios evitan. El contrato es el JSON.
     */
    public record SolicitudAprobada(UUID solicitudId, UUID usuarioId, String aprobadaEn) {
    }
}
