package pe.ayni.bank.identity.domain.port.in;

import java.util.UUID;

import pe.ayni.bank.identity.domain.model.ResultadoGeneracionDesafio;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;

/** Puerto de entrada para solicitar la generación de un desafío OTP (HU-22 / AYNI-128). */
public interface GenerarDesafioCodigoUseCase {

    ResultadoGeneracionDesafio generar(UUID usuarioId, TipoDeSegundoFactor tipoFactor);
}
