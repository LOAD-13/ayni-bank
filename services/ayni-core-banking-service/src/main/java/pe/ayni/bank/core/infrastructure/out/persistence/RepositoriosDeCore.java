package pe.ayni.bank.core.infrastructure.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * {@code SELECT ... FOR UPDATE} sobre las cuentas indicadas, en orden de id.
     *
     * <p>El orden fijo evita que dos transferencias cruzadas se bloqueen mutuamente.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CuentaEntity c WHERE c.id IN :ids ORDER BY c.id")
    List<CuentaEntity> bloquearPorId(@Param("ids") List<UUID> ids);
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

    List<AsientoEntity> findByCuentaIdOrderByRegistradoEnDesc(UUID cuentaId, Limit limite);

    List<AsientoEntity> findByMovimientoId(UUID movimientoId);

    /**
     * El saldo calculado por la base: abonos menos cargos.
     *
     * <p>JPQL con parametro enlazado, no SQL concatenado. Sumar en la base evita traer
     * miles de filas a memoria solo para sumarlas. La definicion del saldo sigue siendo la
     * misma que {@code Cuenta.saldo}: lo comprueba una prueba de este adaptador.
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN a.tipo = 'ABONO' THEN a.importe ELSE -a.importe END), 0) "
            + "FROM AsientoEntity a WHERE a.cuentaId = :cuenta")
    java.math.BigDecimal saldoDe(@Param("cuenta") UUID cuentaId);
}

interface OutboxJpaRepository extends JpaRepository<OutboxEntity, UUID> {

    /** Lo pendiente, en orden de creacion: los eventos se publican como ocurrieron. */
    List<OutboxEntity> findTop50ByPublicadoEnIsNullOrderByCreadoEnAsc();
}

interface OperacionIdempotenteJpaRepository
        extends JpaRepository<OperacionIdempotenteEntity, UUID> {
}

interface EventoAuditoriaJpaRepository extends JpaRepository<EventoAuditoriaEntity, Long> {
}
