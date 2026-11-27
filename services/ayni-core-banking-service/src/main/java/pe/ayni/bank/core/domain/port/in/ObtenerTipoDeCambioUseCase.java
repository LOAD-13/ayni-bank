package pe.ayni.bank.core.domain.port.in;

import pe.ayni.bank.core.domain.model.TipoDeCambioVigente;

/** El tipo de cambio oficial de hoy, con su cache diaria y el respaldo al ultimo valor (HU-12). */
public interface ObtenerTipoDeCambioUseCase {

    /**
     * @return el valor obtenido hoy de la fuente oficial; si no responde, el ultimo conocido con
     *         {@code enCache} en verdadero
     * @throws pe.ayni.bank.core.domain.model.FuenteDeTipoDeCambioNoDisponibleException si la fuente
     *         no responde y nunca se guardo ningun valor
     */
    TipoDeCambioVigente vigente();
}
