package pe.ayni.bank.notification.infrastructure.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface NotificacionJpaRepository extends JpaRepository<NotificacionEntity, UUID> {

    /**
     * Inserta salvo que el evento ya tenga su aviso para ese titular.
     *
     * <p>ON CONFLICT y no una consulta previa: dos entregas simultaneas del mismo mensaje
     * pasarian las dos la consulta. Y no un INSERT que falle por la restriccion: la excepcion
     * marcaria la transaccion para deshacer y se perderian los demas avisos del evento.
     *
     * @return 1 si inserto, 0 si ya existia
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO notification.notificacion (id, evento_id, usuario_id, plantilla, estado, intentos,
                                      proximo_intento_en, datos, creada_en)
            VALUES (:id, :eventoId, :usuarioId, :plantilla, 'PENDIENTE', 0, :proximoIntentoEn,
                    CAST(:datos AS jsonb), :creadaEn)
            ON CONFLICT ON CONSTRAINT ux_notificacion_evento DO NOTHING
            """)
    int insertarSiEsNueva(@Param("id") UUID id, @Param("eventoId") UUID eventoId,
                          @Param("usuarioId") UUID usuarioId, @Param("plantilla") String plantilla,
                          @Param("proximoIntentoEn") Instant proximoIntentoEn, @Param("datos") String datos,
                          @Param("creadaEn") Instant creadaEn);

    List<NotificacionEntity> findByEstadoAndProximoIntentoEnLessThanEqualOrderByProximoIntentoEn(
            String estado, Instant ahora, Limit limite);
}
