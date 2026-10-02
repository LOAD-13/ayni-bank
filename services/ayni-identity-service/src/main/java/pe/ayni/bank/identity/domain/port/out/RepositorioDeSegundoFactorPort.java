package pe.ayni.bank.identity.domain.port.out;

import java.util.Optional;
import java.util.UUID;

import pe.ayni.bank.identity.domain.model.SegundoFactor;

/** El secreto TOTP de cada usuario. Se guarda cifrado con AES-256-GCM. */
public interface RepositorioDeSegundoFactorPort {

    Optional<SegundoFactor> buscarPorUsuario(UUID usuarioId);

    SegundoFactor guardar(SegundoFactor segundoFactor);
}
