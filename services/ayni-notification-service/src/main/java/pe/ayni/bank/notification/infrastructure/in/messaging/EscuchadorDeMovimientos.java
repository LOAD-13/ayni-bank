package pe.ayni.bank.notification.infrastructure.in.messaging;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import pe.ayni.bank.notification.domain.port.in.RegistrarAvisosUseCase;
import pe.ayni.bank.notification.infrastructure.config.ConfiguracionDeMensajeria;
import pe.ayni.bank.notification.infrastructure.in.messaging.TraductorDeMovimientos.DepositoSimuladoRegistrado;
import pe.ayni.bank.notification.infrastructure.in.messaging.TraductorDeMovimientos.TransferenciaRealizada;

/**
 * Oye los movimientos de core-banking y deja pendiente un aviso por titular afectado.
 *
 * <p>RabbitMQ entrega cada mensaje al menos una vez; el mismo evento puede llegar dos veces.
 * El caso de uso lo detecta por el identificador del evento y no crea un segundo aviso.
 */
@Component
public class EscuchadorDeMovimientos {

    private final RegistrarAvisosUseCase registrar;

    public EscuchadorDeMovimientos(RegistrarAvisosUseCase registrar) {
        this.registrar = registrar;
    }

    @RabbitListener(queues = ConfiguracionDeMensajeria.COLA_TRANSFERENCIAS)
    public void alRealizarseUnaTransferencia(TransferenciaRealizada evento,
                                             @Header(name = AmqpHeaders.MESSAGE_ID, required = false)
                                             String messageId) {
        registrar.registrar(TraductorDeMovimientos.deTransferencia(
                TraductorDeMovimientos.eventoId(messageId, evento.movimientoId()), evento));
    }

    @RabbitListener(queues = ConfiguracionDeMensajeria.COLA_DEPOSITOS)
    public void alRegistrarseUnDeposito(DepositoSimuladoRegistrado evento,
                                        @Header(name = AmqpHeaders.MESSAGE_ID, required = false)
                                        String messageId) {
        registrar.registrar(TraductorDeMovimientos.deDeposito(
                TraductorDeMovimientos.eventoId(messageId, evento.movimientoId()), evento));
    }
}
