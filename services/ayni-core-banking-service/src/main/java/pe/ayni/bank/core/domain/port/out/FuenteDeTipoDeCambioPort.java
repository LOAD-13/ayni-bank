package pe.ayni.bank.core.domain.port.out;

import java.time.LocalDate;

import pe.ayni.bank.core.domain.model.TipoDeCambioOficial;

/**
 * Fuente oficial del tipo de cambio soles/dolar (HU-12). El dominio no sabe que es la API del
 * BCRP: solo que devuelve la ultima referencia publicada. La cache diaria y el respaldo al
 * ultimo valor conocido se resuelven fuera de este puerto.
 */
public interface FuenteDeTipoDeCambioPort {

    /**
     * @param hasta dia hasta el que se busca; la fuente no publica en fines de semana ni feriados
     * @return la ultima referencia publicada en o antes de {@code hasta}
     * @throws pe.ayni.bank.core.domain.model.FuenteDeTipoDeCambioNoDisponibleException si la
     *         fuente no responde o no trae ningun valor en los ultimos dias
     */
    TipoDeCambioOficial ultimoPublicado(LocalDate hasta);
}
