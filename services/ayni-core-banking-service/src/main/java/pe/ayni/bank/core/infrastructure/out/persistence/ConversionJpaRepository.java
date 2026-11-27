package pe.ayni.bank.core.infrastructure.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ConversionJpaRepository extends JpaRepository<ConversionEntity, UUID> {

    Optional<ConversionEntity> findByMovimientoSalidaId(UUID movimientoSalidaId);
}
