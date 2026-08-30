package pe.ayni.bank.identity.infrastructure.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface SolicitudJpaRepository extends JpaRepository<SolicitudOnboardingEntity, UUID> {
}
