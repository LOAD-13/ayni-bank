package pe.ayni.bank.notification.application.usecase;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.notification.domain.model.AvisoDeMovimiento;
import pe.ayni.bank.notification.domain.model.Notificacion;
import pe.ayni.bank.notification.domain.port.in.RegistrarAvisosUseCase;
import pe.ayni.bank.notification.domain.port.out.RepositorioDeNotificacionesPort;

/**
 * Registra los avisos de un evento sin enviarlos.
 *
 * <p>El envio lo hace el despachador, fuera de la transaccion del mensaje. Si el correo se
 * enviara aqui y la base fallara despues, RabbitMQ volveria a entregar el evento y el
 * titular recibiria el correo dos veces: justo lo que el escenario 2 prohibe.
 */
@Service
public class RegistrarAvisosService implements RegistrarAvisosUseCase {

    private static final Logger log = LoggerFactory.getLogger(RegistrarAvisosService.class);

    private final RepositorioDeNotificacionesPort notificaciones;
    private final Clock reloj;

    public RegistrarAvisosService(RepositorioDeNotificacionesPort notificaciones, Clock reloj) {
        this.notificaciones = notificaciones;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public int registrar(List<AvisoDeMovimiento> avisos) {
        Instant ahora = reloj.instant();
        int nuevos = 0;
        for (AvisoDeMovimiento aviso : avisos) {
            if (notificaciones.registrarSiEsNueva(Notificacion.de(aviso, ahora))) {
                nuevos++;
            } else {
                log.info("Evento {} repetido: el aviso {} ya estaba registrado", aviso.eventoId(), aviso.tipo());
            }
        }
        return nuevos;
    }
}
