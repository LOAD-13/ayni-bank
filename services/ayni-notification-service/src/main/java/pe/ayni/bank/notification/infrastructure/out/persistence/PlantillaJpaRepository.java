package pe.ayni.bank.notification.infrastructure.out.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PlantillaJpaRepository extends JpaRepository<PlantillaEntity, Short> {

    @Query("""
            SELECT p FROM PlantillaEntity p
             WHERE p.codigo = :codigo AND p.canal = 'EMAIL' AND p.idioma = 'es' AND p.activo = true
            """)
    Optional<PlantillaEntity> deCorreo(@Param("codigo") String codigo);
}
