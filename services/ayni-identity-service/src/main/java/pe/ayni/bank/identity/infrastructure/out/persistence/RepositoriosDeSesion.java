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

    /** Todas las familias del usuario de una sola sentencia, por el mismo motivo (HU-21). */
    @Modifying
    @Query("update RefreshTokenEntity t set t.invalidadoEn = :momento "
            + "where t.usuarioId = :usuarioId and t.invalidadoEn is null")
    void invalidarDelUsuario(@Param("usuarioId") UUID usuarioId,
                             @Param("momento") Instant momento);
}

interface TokenDeRecuperacionJpaRepository extends JpaRepository<TokenDeRecuperacionEntity, UUID> {

    Optional<TokenDeRecuperacionEntity> findByHuellaAndAnuladoEnIsNull(String huella);

    long countByUsuarioIdAndEmitidoEnGreaterThanEqual(UUID usuarioId, Instant desde);

    @Modifying
    @Query("update TokenDeRecuperacionEntity t set t.anuladoEn = :momento "
            + "where t.usuarioId = :usuarioId and t.usadoEn is null and t.anuladoEn is null")
    void anularPendientesDe(@Param("usuarioId") UUID usuarioId,
                            @Param("momento") Instant momento);

    /**
     * Gasta el token solo si sigue libre. Es un UPDATE condicional y no leer, comprobar y
     * guardar: entre la lectura y la escritura cabe otra peticion con el mismo enlace, y
     * la base es el unico sitio donde las dos se ven.
     */
    @Modifying
    @Query("update TokenDeRecuperacionEntity t set t.usadoEn = :momento "
            + "where t.id = :id and t.usadoEn is null and t.anuladoEn is null")
    int marcarUsado(@Param("id") UUID id, @Param("momento") Instant momento);
}

interface EventoAuditoriaJpaRepository extends JpaRepository<EventoAuditoriaEntity, Long> {
}
