package pe.ayni.bank.identity.infrastructure.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;

public interface MetodoSegundoFactorJpaRepository extends JpaRepository<MetodoSegundoFactorEntity, UUID> {

    Optional<MetodoSegundoFactorEntity> findByUsuarioIdAndTipo(UUID usuarioId, TipoDeSegundoFactor tipo);

    List<MetodoSegundoFactorEntity> findByUsuarioId(UUID usuarioId);
}
