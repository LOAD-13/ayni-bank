package pe.ayni.bank.identity.domain.port.in;

import java.util.UUID;

/** Puerto de entrada para verificar un código OTP (HU-22 / AYNI-128). */
public interface VerificarDesafioCodigoUseCase {

    boolean verificar(UUID desafioId, String codigo);
}
