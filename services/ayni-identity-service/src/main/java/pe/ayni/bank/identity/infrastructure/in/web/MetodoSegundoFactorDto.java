package pe.ayni.bank.identity.infrastructure.in.web;

import pe.ayni.bank.identity.domain.model.MetodoDeSegundoFactor;

public record MetodoSegundoFactorDto(
        String id,
        String usuarioId,
        String tipo,
        String secreto,
        boolean confirmado
) {
    public static MetodoSegundoFactorDto desde(MetodoDeSegundoFactor metodo) {
        return new MetodoSegundoFactorDto(
                metodo.id().toString(),
                metodo.usuarioId().toString(),
                metodo.tipo().name(),
                metodo.secreto(),
                metodo.estaConfirmado()
        );
    }
}
