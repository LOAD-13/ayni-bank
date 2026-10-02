package pe.ayni.bank.identity.infrastructure.in.web;

import java.util.UUID;

import pe.ayni.bank.identity.domain.model.ResultadoDeRegistro;

/**
 * Cuerpo de la respuesta 202. Es identico exista o no el correo: ahi esta la
 * antienumeracion. Ver ADR-0008.
 */
public record RespuestaDeRegistroDto(UUID solicitudId, String estado, String mensaje) {

    static RespuestaDeRegistroDto desde(ResultadoDeRegistro resultado) {
        return new RespuestaDeRegistroDto(
                resultado.solicitudId(), resultado.estado().name(), resultado.mensaje());
    }
}
