package pe.ayni.bank.identity.infrastructure.out.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.identity.domain.model.TokenDeRecuperacion;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeTokensDeRecuperacionPort;

/** Implementa {@link RepositorioDeTokensDeRecuperacionPort} sobre JPA. */
@Repository
public class AdaptadorRepositorioDeTokensDeRecuperacion implements RepositorioDeTokensDeRecuperacionPort {

    private final TokenDeRecuperacionJpaRepository repositorio;

    AdaptadorRepositorioDeTokensDeRecuperacion(TokenDeRecuperacionJpaRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public void guardar(TokenDeRecuperacion token) {
        repositorio.save(new TokenDeRecuperacionEntity(token.id(), token.usuarioId(), token.huella(),
                token.emitidoEn(), token.expiraEn(), token.usadoEn()));
    }

    @Override
    public Optional<TokenDeRecuperacion> buscarPorHuella(String huella) {
        return repositorio.findByHuellaAndAnuladoEnIsNull(huella)
                .map(fila -> TokenDeRecuperacion.reconstituir(fila.getId(), fila.getUsuarioId(),
                        fila.getHuella(), fila.getEmitidoEn(), fila.getExpiraEn(), fila.getUsadoEn()));
    }

    @Override
    public long contarEmitidosDesde(UUID usuarioId, Instant desde) {
        return repositorio.countByUsuarioIdAndEmitidoEnGreaterThanEqual(usuarioId, desde);
    }

    @Override
    @Transactional
    public void anularPendientesDe(UUID usuarioId, Instant momento) {
        repositorio.anularPendientesDe(usuarioId, momento);
    }

    @Override
    @Transactional
    public boolean marcarUsado(UUID tokenId, Instant momento) {
        return repositorio.marcarUsado(tokenId, momento) == 1;
    }
}
