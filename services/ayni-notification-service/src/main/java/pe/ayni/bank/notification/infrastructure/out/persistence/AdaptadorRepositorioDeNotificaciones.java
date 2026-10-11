package pe.ayni.bank.notification.infrastructure.out.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import pe.ayni.bank.notification.domain.model.EstadoDeNotificacion;
import pe.ayni.bank.notification.domain.model.IntentoDeEnvio;
import pe.ayni.bank.notification.domain.model.Notificacion;
import pe.ayni.bank.notification.domain.model.TipoDeAviso;
import pe.ayni.bank.notification.domain.port.out.RepositorioDeNotificacionesPort;

@Component
public class AdaptadorRepositorioDeNotificaciones implements RepositorioDeNotificacionesPort {

    private final NotificacionJpaRepository notificaciones;
    private final IntentoEnvioJpaRepository intentos;
    private final ObjectMapper mapeador;

    AdaptadorRepositorioDeNotificaciones(NotificacionJpaRepository notificaciones,
                                         IntentoEnvioJpaRepository intentos, ObjectMapper mapeador) {
        this.notificaciones = notificaciones;
        this.intentos = intentos;
        this.mapeador = mapeador;
    }

    @Override
    public boolean registrarSiEsNueva(Notificacion n) {
        return notificaciones.insertarSiEsNueva(n.id(), n.eventoId(), n.usuarioId(), n.tipo().name(),
                n.proximoIntentoEn(), json(n), n.creadaEn()) == 1;
    }

    @Override
    public List<Notificacion> pendientesHasta(Instant ahora, int limite) {
        return notificaciones.findByEstadoAndProximoIntentoEnLessThanEqualOrderByProximoIntentoEn(
                        EstadoDeNotificacion.PENDIENTE.name(), ahora, Limit.of(limite))
                .stream()
                .map(e -> new Notificacion(e.getId(), e.getEventoId(), e.getUsuarioId(),
                        TipoDeAviso.valueOf(e.getPlantilla()), e.getDatos(),
                        EstadoDeNotificacion.valueOf(e.getEstado()), e.getIntentos(), e.getProximoIntentoEn(),
                        e.getCreadaEn(), e.getEnviadaEn()))
                .toList();
    }

    @Override
    @Transactional
    public void actualizar(Notificacion n, IntentoDeEnvio intento) {
        NotificacionEntity fila = notificaciones.findById(n.id())
                .orElseThrow(() -> new IllegalStateException("No existe la notificacion " + n.id()));
        fila.actualizar(n.estado().name(), (short) n.intentos(), n.proximoIntentoEn(), n.enviadaEn());
        intentos.save(new IntentoEnvioEntity(intento.id(), intento.notificacionId(), (short) intento.numero(),
                intento.enviado() ? "ENVIADO" : "ERROR", intento.detalle(), intento.ocurridoEn()));
    }

    private String json(Notificacion n) {
        try {
            return mapeador.writeValueAsString(n.datos());
        } catch (JsonProcessingException e) {
            // Un Map<String, String> siempre se puede escribir: llegar aqui es un error de programacion.
            throw new IllegalStateException("No se pudieron serializar los datos del aviso", e);
        }
    }
}
