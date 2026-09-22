package pe.ayni.bank.identity.infrastructure.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import pe.ayni.bank.identity.domain.model.MetodoDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeMetodoSegundoFactorPort;

@Component
public class AdaptadorRepositorioDeMetodoSegundoFactor implements RepositorioDeMetodoSegundoFactorPort {

    private final MetodoSegundoFactorJpaRepository jpaRepository;

    public AdaptadorRepositorioDeMetodoSegundoFactor(MetodoSegundoFactorJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<MetodoDeSegundoFactor> buscarPorUsuarioYTipo(UUID usuarioId, TipoDeSegundoFactor tipo) {
        return jpaRepository.findByUsuarioIdAndTipo(usuarioId, tipo)
                .map(this::aDominio);
    }

    @Override
    public List<MetodoDeSegundoFactor> listarPorUsuario(UUID usuarioId) {
        return jpaRepository.findByUsuarioId(usuarioId).stream()
                .map(this::aDominio)
                .toList();
    }

    @Override
    public MetodoDeSegundoFactor guardar(MetodoDeSegundoFactor metodo) {
        MetodoSegundoFactorEntity entity = new MetodoSegundoFactorEntity(
                metodo.id(),
                metodo.usuarioId(),
                metodo.tipo(),
                metodo.secreto(),
                metodo.creadoEn(),
                metodo.confirmadoEn()
        );
        jpaRepository.save(entity);
        return metodo;
    }

    private MetodoDeSegundoFactor aDominio(MetodoSegundoFactorEntity entity) {
        return MetodoDeSegundoFactor.reconstituir(
                entity.getId(),
                entity.getUsuarioId(),
                entity.getTipo(),
                entity.getSecreto(),
                entity.getCreadoEn(),
                entity.getConfirmadoEn()
        );
    }
}
