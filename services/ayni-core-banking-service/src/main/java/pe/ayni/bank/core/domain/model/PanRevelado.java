package pe.ayni.bank.core.domain.model;

import java.time.Instant;

/** HU-09: el PAN completo en grupos de cuatro y hasta cuando debe mostrarse. */
public record PanRevelado(String numero, Instant visibleHasta) {

    @Override
    public String toString() {
        return "PanRevelado[oculto]";
    }
}
