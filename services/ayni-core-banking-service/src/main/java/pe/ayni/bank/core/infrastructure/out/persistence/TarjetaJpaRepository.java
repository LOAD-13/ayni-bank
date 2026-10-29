package pe.ayni.bank.core.infrastructure.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface TarjetaJpaRepository extends JpaRepository<TarjetaEntity, UUID> {

    Optional<TarjetaEntity> findByUsuarioId(UUID usuarioId);

    boolean existsByPanHuella(String panHuella);
}
