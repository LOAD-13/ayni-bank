package pe.ayni.bank.identity.domain.port.out;

import java.util.Optional;
import java.util.UUID;

import pe.ayni.bank.identity.domain.model.DesafioPorCodigo;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;

/** Puerto de salida para persistir los desafíos de código OTP por correo/SMS. */
public interface RepositorioDeDesafioPorCodigoPort {

    DesafioPorCodigo guardar(DesafioPorCodigo desafio);

    Optional<DesafioPorCodigo> buscarPorId(UUID id);

    Optional<DesafioPorCodigo> buscarUltimoPendiente(UUID usuarioId, TipoDeSegundoFactor tipo);
}
