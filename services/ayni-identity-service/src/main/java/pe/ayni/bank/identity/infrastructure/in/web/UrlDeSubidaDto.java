package pe.ayni.bank.identity.infrastructure.in.web;

import java.time.Instant;
import java.util.Map;

import pe.ayni.bank.identity.domain.model.UrlDeSubida;

/**
 * Formulario de subida directa al almacen: el navegador envia {@code campos} tal cual y el
 * archivo al final, por POST a {@code url}. Despues devuelve {@code claveDeObjeto} al pedir la
 * evaluacion de la foto.
 */
public record UrlDeSubidaDto(String url, Map<String, String> campos, String claveDeObjeto, Instant expiraEn) {

    static UrlDeSubidaDto desde(UrlDeSubida resultado) {
        return new UrlDeSubidaDto(resultado.url(), resultado.campos(), resultado.claveDeObjeto(),
                resultado.expiraEn());
    }
}
