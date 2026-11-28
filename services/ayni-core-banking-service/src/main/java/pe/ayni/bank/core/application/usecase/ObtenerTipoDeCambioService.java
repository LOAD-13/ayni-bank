package pe.ayni.bank.core.application.usecase;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import pe.ayni.bank.core.domain.model.FuenteDeTipoDeCambioNoDisponibleException;
import pe.ayni.bank.core.domain.model.TipoDeCambioConocido;
import pe.ayni.bank.core.domain.model.TipoDeCambioOficial;
import pe.ayni.bank.core.domain.model.TipoDeCambioVigente;
import pe.ayni.bank.core.domain.port.in.ObtenerTipoDeCambioUseCase;
import pe.ayni.bank.core.domain.port.out.FuenteDeTipoDeCambioPort;
import pe.ayni.bank.core.domain.port.out.RepositorioDeTipoDeCambioPort;

/**
 * Cache diaria y respaldo del tipo de cambio oficial (HU-12, AYNI-267 y AYNI-268).
 *
 * <p>La fuente publica un valor por dia habil, asi que se consulta una vez al dia: lo que ya se
 * obtuvo hoy se sirve de la tabla {@code tipo_cambio}. "Hoy" es el dia de Lima, no el de UTC, porque
 * el reloj del sistema va en UTC y a las 19:00 de Lima ya es manana para el. Si la fuente no
 * responde, o su circuito esta abierto, se sirve el ultimo valor guardado marcado como cache, para
 * que la pantalla avise al cliente de la fecha antes de confirmar.
 */
@Service
public class ObtenerTipoDeCambioService implements ObtenerTipoDeCambioUseCase {

    private static final Logger log = LoggerFactory.getLogger(ObtenerTipoDeCambioService.class);
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final FuenteDeTipoDeCambioPort fuente;
    private final RepositorioDeTipoDeCambioPort guardados;
    private final Clock reloj;

    public ObtenerTipoDeCambioService(FuenteDeTipoDeCambioPort fuente,
                                      RepositorioDeTipoDeCambioPort guardados, Clock reloj) {
        this.fuente = fuente;
        this.guardados = guardados;
        this.reloj = reloj;
    }

    @Override
    public TipoDeCambioVigente vigente() {
        Instant ahora = reloj.instant();
        LocalDate hoy = ahora.atZone(LIMA).toLocalDate();

        Optional<TipoDeCambioConocido> deHoy = guardados.obtenidoDesde(hoy.atStartOfDay(LIMA).toInstant());
        if (deHoy.isPresent()) {
            return vigente(deHoy.get(), false);
        }
        try {
            TipoDeCambioOficial oficial = fuente.ultimoPublicado(hoy);
            guardados.guardar(oficial, ahora);
            return new TipoDeCambioVigente(oficial, ahora, false);
        } catch (FuenteDeTipoDeCambioNoDisponibleException caida) {
            // Un aviso y no un error: es el caso previsto por el escenario 2 de HU-12.
            log.warn("La fuente del tipo de cambio no respondio: {}. Se usa el ultimo valor conocido.",
                    caida.getMessage());
            return guardados.ultimoConocido()
                    .map(conocido -> vigente(conocido, true))
                    .orElseThrow(() -> new FuenteDeTipoDeCambioNoDisponibleException(
                            "La fuente no responde y no hay ningun tipo de cambio guardado", caida));
        }
    }

    private static TipoDeCambioVigente vigente(TipoDeCambioConocido conocido, boolean enCache) {
        return new TipoDeCambioVigente(conocido.oficial(), conocido.obtenidoEn(), enCache);
    }
}
