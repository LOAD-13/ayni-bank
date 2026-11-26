package pe.ayni.bank.core.infrastructure.out.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SegmentoCambiarioJpaRepository extends JpaRepository<SegmentoCambiarioEntity, Short> {

    /** Suma de los saldos de cierre devengados de la cuenta en soles del titular en el rango. */
    @Query(value = "SELECT COALESCE(SUM(d.saldo_base), 0) FROM core.devengo_interes d"
            + " JOIN core.cuenta c ON c.id = d.cuenta_id"
            + " WHERE c.usuario_id = :usuarioId AND c.moneda = 'PEN' AND c.tecnica = FALSE"
            + " AND d.fecha BETWEEN :desde AND :hasta", nativeQuery = true)
    BigDecimal sumaDeSaldosDeCierre(@Param("usuarioId") UUID usuarioId, @Param("desde") LocalDate desde,
                                    @Param("hasta") LocalDate hasta);
}
