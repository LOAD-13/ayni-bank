package pe.ayni.bank.core.infrastructure.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Los repositorios de Spring Data de HU-05.
 *
 * <p>Van juntos por el mismo motivo que en identidad: son interfaces sin cuerpo, y
 * repartirlas en cuatro ficheros de seis lineas dispersa sin aclarar nada.
 */
final class RepositoriosDeCore {

    private RepositoriosDeCore() {
    }
}

interface CuentaJpaRepository extends JpaRepository<CuentaEntity, UUID> {

    boolean existsByUsuarioIdAndMonedaAndEstado(UUID usuarioId, String moneda, String estado);

    Optional<CuentaEntity> findByUsuarioIdAndMonedaAndEstado(
            UUID usuarioId, String moneda, String estado);

    Optional<CuentaEntity> findByNumero(String numero);

    /**
     * Siguiente valor de la secuencia de cuentas.
     *
     * <p>Consulta nativa porque una secuencia no es una entidad. Se pide a la base y no se
     * lleva un contador en memoria: garantizar que dos peticiones simultaneas no obtengan
     * el mismo numero es justo lo que una secuencia sabe hacer.
     */
    @Query(value = "SELECT nextval('core.secuencia_cuenta')", nativeQuery = true)
    long siguienteCorrelativo();
}

interface TasaJpaRepository extends JpaRepository<CuentaEntity, UUID> {

    /**
     * TREA vigente hoy para un producto.
     *
     * <p>Consulta nativa porque tasa_producto no tiene entidad: es catalogo de solo
     * lectura y mapearlo entero para leer un numero seria mas codigo del que ahorra.
     * La vigencia importa: si la tasa cambio, los intereses ya devengados se calcularon
     * con la anterior, y por eso la tabla guarda historico en lugar de un solo valor.
     */
    @Query(value = "SELECT trea FROM core.tasa_producto WHERE producto_id = :producto "
            + "AND vigencia_desde <= now() ORDER BY vigencia_desde DESC LIMIT 1",
            nativeQuery = true)
    java.math.BigDecimal treaVigente(@org.springframework.data.repository.query.Param("producto") short producto);
}

interface AsientoJpaRepository extends JpaRepository<AsientoEntity, UUID> {

    List<AsientoEntity> findByCuentaIdOrderByRegistradoEnAsc(UUID cuentaId);
}

interface OutboxJpaRepository extends JpaRepository<OutboxEntity, UUID> {

    /** Lo pendiente, en orden de creacion: los eventos se publican como ocurrieron. */
    List<OutboxEntity> findTop50ByPublicadoEnIsNullOrderByCreadoEnAsc();
}

interface OperacionIdempotenteJpaRepository
        extends JpaRepository<OperacionIdempotenteEntity, UUID> {
}
