package pe.ayni.bank.identity.infrastructure.out.persistence;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface LecturaDniJpaRepository extends JpaRepository<LecturaDniEntity, UUID> {

    Optional<LecturaDniEntity> findFirstBySolicitudIdAndFuenteInOrderByLeidaEnDesc(
            UUID solicitudId, Collection<String> fuentes);
}
