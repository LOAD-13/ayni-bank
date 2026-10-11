package pe.ayni.bank.notification.domain.port.out;

import java.time.Instant;
import java.util.List;

import pe.ayni.bank.notification.domain.model.IntentoDeEnvio;
import pe.ayni.bank.notification.domain.model.Notificacion;

public interface RepositorioDeNotificacionesPort {

    /**
     * Guarda la notificacion salvo que ya exista una del mismo evento, titular y tipo.
     *
     * @return {@code false} si ya existia
     */
    boolean registrarSiEsNueva(Notificacion notificacion);

    /** Las pendientes cuyo turno llego, de la mas antigua a la mas reciente. */
    List<Notificacion> pendientesHasta(Instant ahora, int limite);

    /** Guarda el nuevo estado de la notificacion junto con el intento que lo produjo. */
    void actualizar(Notificacion notificacion, IntentoDeEnvio intento);
}
