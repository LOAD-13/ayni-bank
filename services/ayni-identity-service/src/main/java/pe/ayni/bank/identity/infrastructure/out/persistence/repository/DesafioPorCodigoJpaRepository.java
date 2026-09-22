package pe.ayni.bank.identity.infrastructure.out.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;
import pe.ayni.bank.identity.infrastructure.out.persistence.entity.DesafioPorCodigoEntity;

/** Repositorio Spring Data JPA para la entidad DesafioPorCodigoEntity. */
public interface DesafioPorCodigoJpaRepository extends JpaRepository<DesafioPorCodigoEntity, UUID> {

    Optional<DesafioPorCodigoEntity> findFirstByUsuarioIdAndTipoFactorOrderByCreadoEnDesc(
            UUID usuarioId, TipoDeSegundoFactor tipoFactor);
}
