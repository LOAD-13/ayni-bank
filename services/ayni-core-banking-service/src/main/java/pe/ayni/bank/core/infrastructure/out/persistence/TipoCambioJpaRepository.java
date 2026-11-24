package pe.ayni.bank.core.infrastructure.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TipoCambioJpaRepository extends JpaRepository<TipoCambioEntity, Long> {

    @Query("select t from TipoCambioEntity t where t.monedaOrigen = 'USD' and t.monedaDestino = 'PEN'"
            + " and t.obtenidoEn >= :desde order by t.obtenidoEn desc")
    List<TipoCambioEntity> obtenidosDesde(@Param("desde") Instant desde, Limit limite);

    @Query("select t from TipoCambioEntity t where t.monedaOrigen = 'USD' and t.monedaDestino = 'PEN'"
            + " order by t.fecha desc, t.obtenidoEn desc")
    List<TipoCambioEntity> masRecientes(Limit limite);

    /**
     * Inserta o, si ya hay una fila de esa fecha y fuente ({@code ux_tipo_cambio_dia}), actualiza
     * el valor y el momento de obtencion. Es una sola sentencia: dos peticiones simultaneas que
     * consultan la fuente a la vez no chocan.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "INSERT INTO core.tipo_cambio (fecha, moneda_origen, moneda_destino, referencia_compra,"
            + " referencia_venta, fuente, obtenido_en) VALUES (:fecha, 'USD', 'PEN', :compra, :venta, :fuente,"
            + " :obtenidoEn) ON CONFLICT (fecha, moneda_origen, moneda_destino, fuente) DO UPDATE SET"
            + " referencia_compra = EXCLUDED.referencia_compra, referencia_venta = EXCLUDED.referencia_venta,"
            + " obtenido_en = EXCLUDED.obtenido_en", nativeQuery = true)
    void guardar(@Param("fecha") LocalDate fecha, @Param("compra") BigDecimal compra,
                 @Param("venta") BigDecimal venta, @Param("fuente") String fuente,
                 @Param("obtenidoEn") Instant obtenidoEn);
}
