package pe.ayni.bank.identity.domain.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pe.ayni.bank.identity.domain.model.MetodoDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;

/** Puerto de salida para persistencia de métodos de segundo factor del usuario. */
public interface RepositorioDeMetodoSegundoFactorPort {

    Optional<MetodoDeSegundoFactor> buscarPorUsuarioYTipo(UUID usuarioId, TipoDeSegundoFactor tipo);

    List<MetodoDeSegundoFactor> listarPorUsuario(UUID usuarioId);

    MetodoDeSegundoFactor guardar(MetodoDeSegundoFactor metodo);
}
