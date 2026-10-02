package pe.ayni.bank.core.infrastructure.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acceso a la bandeja de salida para el publicador.
 *
 * <p>Existe como pieza aparte del adaptador que escribe los eventos porque son dos usos
 * distintos de la misma tabla: uno escribe dentro de la transacción de negocio, el otro lee
 * y marca desde un proceso programado. Mezclarlos en una sola clase invitaría a que el
 * publicador acabara escribiendo eventos, que es justo lo que no debe hacer.
 *
 * <p>Vive en `persistence` y no en `messaging` a propósito: aquí no se envía nada, solo se
 * consulta y se marca una tabla.
 */
@Component
public class BandejaDeSalida {

    private final OutboxJpaRepository repositorio;

    BandejaDeSalida(OutboxJpaRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional(readOnly = true)
    public List<EventoPendiente> pendientes() {
        return repositorio.findTop50ByPublicadoEnIsNullOrderByCreadoEnAsc().stream()
                .map(fila -> new EventoPendiente(
                        fila.getId(), fila.getTipoEvento(), fila.getCarga(),
                        fila.getIntentos()))
                .toList();
    }

    @Transactional
    public void marcarPublicado(UUID id, Instant momento) {
        repositorio.findById(id).ifPresent(fila -> fila.marcarPublicado(momento));
    }

    @Transactional
    public void anotarFallo(UUID id, String error) {
        repositorio.findById(id).ifPresent(fila -> fila.anotarFallo(error));
    }

    /**
     * La carga viaja como texto JSON tal cual salió de la tabla.
     *
     * <p>No se deserializa a un objeto para volver a serializarlo al enviarlo: sería
     * trabajo doble y, peor, una oportunidad de que el JSON que sale no sea exactamente el
     * que se guardó.
     */
    public record EventoPendiente(UUID id, String tipoEvento, String carga, short intentos) {
    }
}
