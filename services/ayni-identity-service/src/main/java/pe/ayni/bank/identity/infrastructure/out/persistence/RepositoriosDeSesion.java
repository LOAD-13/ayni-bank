package pe.ayni.bank.identity.infrastructure.out.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Los cuatro repositorios de Spring Data que necesita HU-04.
 *
 * <p>Van en un mismo fichero porque son cuatro interfaces sin cuerpo: repartirlas en
 * cuatro ficheros de seis lineas no hace el codigo mas legible, solo mas disperso.
 * Ninguna es publica, asi que el compilador lo admite.
 */
final class RepositoriosDeSesion {

    private RepositoriosDeSesion() {
    }
}

interface SegundoFactorJpaRepository extends JpaRepository<SegundoFactorEntity, UUID> {
}

interface ControlDeAccesoJpaRepository extends JpaRepository<ControlDeAccesoEntity, UUID> {
}

interface DesafioJpaRepository extends JpaRepository<DesafioEntity, UUID> {

    /** Solo sirve el desafio que aun no se ha canjeado. */
    Optional<DesafioEntity> findByIdAndConsumidoEnIsNull(UUID id);
}

interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    Optional<RefreshTokenEntity> findByHuellaAndInvalidadoEnIsNull(String huella);

    /**
     * Invalida la familia entera de una sola sentencia.
     *
     * <p>Se hace con UPDATE y no cargando las entidades una por una porque es una
     * respuesta a un incidente de seguridad: cuanto antes dejen de valer esos tokens,
     * menos tiempo tiene quien robo la cookie.
     */
    @Modifying
    @Query("update RefreshTokenEntity t set t.invalidadoEn = :momento "
            + "where t.familiaId = :familiaId and t.invalidadoEn is null")
    void invalidarFamilia(@Param("familiaId") UUID familiaId,
                          @Param("momento") Instant momento);
}

interface EventoAuditoriaJpaRepository extends JpaRepository<EventoAuditoriaEntity, Long> {
}
