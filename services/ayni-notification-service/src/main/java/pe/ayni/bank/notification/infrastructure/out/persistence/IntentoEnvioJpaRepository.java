package pe.ayni.bank.notification.infrastructure.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface IntentoEnvioJpaRepository extends JpaRepository<IntentoEnvioEntity, UUID> {
}
