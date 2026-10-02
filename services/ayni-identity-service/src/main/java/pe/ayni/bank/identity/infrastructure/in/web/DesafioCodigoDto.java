package pe.ayni.bank.identity.infrastructure.in.web;

import java.time.Instant;
import java.util.UUID;

import pe.ayni.bank.identity.domain.model.DesafioPorCodigo;

/** DTO de respuesta que representa un desafío OTP sin revelar el código. */
public record DesafioCodigoDto(
        UUID id,
        UUID usuarioId,
        String tipoFactor,
        Instant expiraEn,
        int intentosRealizados,
        boolean verificado
) {
    public static DesafioCodigoDto desde(DesafioPorCodigo desafio) {
        return new DesafioCodigoDto(
                desafio.id(),
                desafio.usuarioId(),
                desafio.tipoFactor().name(),
                desafio.expiraEn(),
                desafio.intentosRealizados(),
                desafio.estaVerificado()
        );
    }
}
