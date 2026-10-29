package pe.ayni.bank.identity.infrastructure.in.web;

import pe.ayni.bank.identity.domain.model.MetodoDeSegundoFactor;

public record MetodoSegundoFactorDto(
        String id,
        String usuarioId,
        String tipo,
        String secreto,
        String uriDeAprovisionamiento,
        boolean confirmado
) {
    public static MetodoSegundoFactorDto desde(MetodoDeSegundoFactor metodo) {
        // OWASP A01 / A02: un método ya confirmado jamás vuelve a exponer su secreto TOTP
        // ni su URI de aprovisionamiento en respuestas posteriores.
        String secretoVisible = metodo.estaConfirmado() ? null : metodo.secreto();
        String uri = secretoVisible != null
                ? "otpauth://totp/Ayni%20Bank?secret=" + secretoVisible + "&issuer=Ayni%20Bank&digits=6&period=30"
                : null;
        return new MetodoSegundoFactorDto(
                metodo.id().toString(),
                metodo.usuarioId().toString(),
                metodo.tipo().name(),
                secretoVisible,
                uri,
                metodo.estaConfirmado()
        );
    }
}

