package pe.ayni.bank.identity.infrastructure.out.persistence.adapter;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import pe.ayni.bank.identity.domain.model.DesafioPorCodigo;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeDesafioPorCodigoPort;
import pe.ayni.bank.identity.infrastructure.out.persistence.entity.DesafioPorCodigoEntity;
import pe.ayni.bank.identity.infrastructure.out.persistence.repository.DesafioPorCodigoJpaRepository;

/** Adaptador de persistencia JPA para el puerto RepositorioDeDesafioPorCodigoPort. */
@Component
public class AdaptadorRepositorioDeDesafioPorCodigo implements RepositorioDeDesafioPorCodigoPort {

    private final DesafioPorCodigoJpaRepository repository;

    public AdaptadorRepositorioDeDesafioPorCodigo(DesafioPorCodigoJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public DesafioPorCodigo guardar(DesafioPorCodigo desafio) {
        DesafioPorCodigoEntity entity = DesafioPorCodigoEntity.desdeDominio(desafio);
        return repository.save(entity).aDominio();
    }

    @Override
    public Optional<DesafioPorCodigo> buscarPorId(UUID id) {
        return repository.findById(id).map(DesafioPorCodigoEntity::aDominio);
    }

    @Override
    public Optional<DesafioPorCodigo> buscarUltimoPendiente(UUID usuarioId, TipoDeSegundoFactor tipo) {
        return repository.findFirstByUsuarioIdAndTipoFactorOrderByCreadoEnDesc(usuarioId, tipo)
                .map(DesafioPorCodigoEntity::aDominio);
    }
}
