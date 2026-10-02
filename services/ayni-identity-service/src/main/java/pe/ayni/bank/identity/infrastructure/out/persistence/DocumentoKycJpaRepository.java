package pe.ayni.bank.identity.infrastructure.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface DocumentoKycJpaRepository extends JpaRepository<DocumentoKycEntity, UUID> {

    Optional<DocumentoKycEntity> findFirstBySolicitudIdAndTipoDocumentoOrderBySubidoEnDesc(
            UUID solicitudId, String tipoDocumento);
}
