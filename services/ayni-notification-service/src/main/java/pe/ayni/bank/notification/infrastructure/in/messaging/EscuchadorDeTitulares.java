package pe.ayni.bank.notification.infrastructure.in.messaging;

import java.util.UUID;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import pe.ayni.bank.notification.domain.model.Titular;
import pe.ayni.bank.notification.domain.port.in.RegistrarTitularUseCase;
import pe.ayni.bank.notification.infrastructure.config.ConfiguracionDeMensajeria;

/** Mantiene la proyeccion de titulares con lo que publica identity al aprobar una cuenta. */
@Component
public class EscuchadorDeTitulares {

    private final RegistrarTitularUseCase registrar;

    public EscuchadorDeTitulares(RegistrarTitularUseCase registrar) {
        this.registrar = registrar;
    }

    @RabbitListener(queues = ConfiguracionDeMensajeria.COLA_TITULARES)
    public void alActivarseElTitular(TitularActivado evento) {
        registrar.registrar(new Titular(evento.usuarioId(), evento.correo(), evento.nombrePila()));
    }

    /** El contrato de identity. Se declara aqui y no en un modulo compartido, como en core-banking. */
    public record TitularActivado(UUID usuarioId, String correo, String nombrePila, String activadoEn) {

        @Override
        public String toString() {
            return "TitularActivado[usuarioId=" + usuarioId + ", activadoEn=" + activadoEn + "]";
        }
    }
}
