package pe.ayni.bank.identity.infrastructure.in.web;

import java.time.Instant;

import pe.ayni.bank.identity.domain.model.UrlDeSubida;

public record UrlDeSubidaDto(String url, Instant expiraEn) {

    static UrlDeSubidaDto desde(UrlDeSubida resultado) {
        return new UrlDeSubidaDto(resultado.url(), resultado.expiraEn());
    }
}
