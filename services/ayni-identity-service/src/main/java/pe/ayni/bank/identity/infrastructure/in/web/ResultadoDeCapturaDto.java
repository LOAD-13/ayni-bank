package pe.ayni.bank.identity.infrastructure.in.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import pe.ayni.bank.identity.domain.model.ResultadoDeCaptura;

/**
 * @param estado ACEPTADO, RECHAZADO, EN_REVISION_MANUAL o VERIFICACION_DIFERIDA
 * @param motivo solo si se rechazo: NO_ES_DNI, ENCUADRE, DESENFOQUE, REFLEJO o ILUMINACION
 * @param intentosRestantes solo si se rechazo: fotos que quedan de ese lado
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResultadoDeCapturaDto(String estado, String motivo, Integer intentosRestantes) {

    static ResultadoDeCapturaDto desde(ResultadoDeCaptura resultado) {
        boolean rechazada = resultado.motivo() != null;
        return new ResultadoDeCapturaDto(
                resultado.estado().name(),
                rechazada ? resultado.motivo().name() : null,
                rechazada ? resultado.intentosRestantes() : null);
    }
}
