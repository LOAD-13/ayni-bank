package pe.ayni.bank.identity.domain.model;

/** Record que agrupa el desafío generado y su código de 6 dígitos en claro (HU-22 / AYNI-128). */
public record ResultadoGeneracionDesafio(
        DesafioPorCodigo desafio,
        String codigo6Digitos
) {}
