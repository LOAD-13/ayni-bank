package pe.ayni.bank.identity.domain.model;

import java.time.Instant;

/** URL pre-firmada de subida y el momento en que deja de ser valida. */
public record UrlDeSubida(String url, Instant expiraEn) {
}
