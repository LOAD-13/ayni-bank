package pe.ayni.bank.core.domain.port.out;

import java.time.Instant;
import java.util.Optional;

import pe.ayni.bank.core.domain.model.TipoDeCambioConocido;
import pe.ayni.bank.core.domain.model.TipoDeCambioOficial;

/**
 * Donde se guarda el tipo de cambio oficial ya consultado (HU-12, AYNI-267). Hace de cache diaria y
 * de memoria del ultimo valor conocido: vive en la tabla {@code tipo_cambio} y por eso sobrevive a
 * un reinicio, que es justo cuando la fuente externa puede no estar disponible.
 */
public interface RepositorioDeTipoDeCambioPort {

    /** El valor mas reciente obtenido de la fuente en {@code desde} o despues: la cache del dia. */
    Optional<TipoDeCambioConocido> obtenidoDesde(Instant desde);

    /** El valor mas reciente que se tenga, sea de cuando sea: el respaldo si la fuente no responde. */
    Optional<TipoDeCambioConocido> ultimoConocido();

    /**
     * Guarda el valor. Si ya habia uno para la misma fecha y fuente, se actualiza el momento en que se
     * obtuvo: una fuente que no publica en fin de semana devuelve el mismo dia varias veces.
     */
    void guardar(TipoDeCambioOficial tipo, Instant obtenidoEn);
}
