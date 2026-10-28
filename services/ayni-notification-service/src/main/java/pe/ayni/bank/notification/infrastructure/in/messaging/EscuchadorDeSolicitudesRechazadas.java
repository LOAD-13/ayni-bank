package pe.ayni.bank.notification.infrastructure.in.messaging;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import pe.ayni.bank.notification.domain.model.AvisoDeMovimiento;
import pe.ayni.bank.notification.domain.model.TipoDeAviso;
import pe.ayni.bank.notification.domain.model.Titular;
import pe.ayni.bank.notification.domain.port.in.RegistrarAvisosUseCase;
import pe.ayni.bank.notification.domain.port.in.RegistrarTitularUseCase;
import pe.ayni.bank.notification.infrastructure.config.ConfiguracionDeMensajeria;

/**
 * HU-05 escenario 3: avisa al solicitante de que su solicitud se rechazo y por que.
 *
 * <p>Quien recibe este aviso no llego a ser titular, asi que no esta en la proyeccion que
 * llena {@link EscuchadorDeTitulares}. Por eso el evento trae el correo y el nombre: primero
 * se registra a quien hay que escribir y despues el aviso, y el despachador lo envia como
 * cualquier otro, con sus reintentos.
 *
 * <p>La solicitud hace de identificador del evento: se rechaza una sola vez, de modo que una
 * entrega repetida de RabbitMQ choca con la restriccion unica y no manda un segundo correo.
 */
@Component
public class EscuchadorDeSolicitudesRechazadas {

    private final RegistrarTitularUseCase contactos;
    private final RegistrarAvisosUseCase avisos;

    public EscuchadorDeSolicitudesRechazadas(RegistrarTitularUseCase contactos, RegistrarAvisosUseCase avisos) {
        this.contactos = contactos;
        this.avisos = avisos;
    }

    @RabbitListener(queues = ConfiguracionDeMensajeria.COLA_SOLICITUDES_RECHAZADAS)
    public void alRechazarseLaSolicitud(SolicitudRechazada evento) {
        contactos.registrar(new Titular(evento.usuarioId(), evento.correo(), evento.nombrePila()));
        avisos.registrar(List.of(new AvisoDeMovimiento(evento.solicitudId(), evento.usuarioId(),
                TipoDeAviso.SOLICITUD_RECHAZADA, Map.of("motivo", evento.detalle()))));
    }

    /** El contrato de identity. Se declara aqui y no en un modulo compartido, como en core-banking. */
    public record SolicitudRechazada(UUID solicitudId, UUID usuarioId, String correo, String nombrePila,
                                     String motivo, String detalle, String rechazadaEn) {

        /** El correo es dato personal: no sale entero en una traza. */
        @Override
        public String toString() {
            return "SolicitudRechazada[solicitudId=" + solicitudId + ", motivo=" + motivo + "]";
        }
    }
}
