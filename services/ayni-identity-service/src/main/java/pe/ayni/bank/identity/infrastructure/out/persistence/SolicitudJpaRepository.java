package pe.ayni.bank.identity.infrastructure.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface SolicitudJpaRepository extends JpaRepository<SolicitudOnboardingEntity, UUID> {

    /** La solicitud mas reciente de un titular. Hoy solo hay una; manana podria reintentar. */
    java.util.Optional<SolicitudOnboardingEntity> findFirstByUsuarioIdOrderByCreadaEnDesc(
            UUID usuarioId);
}
