package pe.ayni.bank.notification.infrastructure.out.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TitularJpaRepository extends JpaRepository<TitularEntity, UUID> {

    /**
     * Inserta o actualiza en una sola sentencia. Leer y luego escribir dejaria una ventana en
     * la que dos eventos del mismo titular chocan con la clave primaria.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO notification.titular (usuario_id, correo, nombre_pila, actualizado_en)
            VALUES (:usuarioId, :correo, :nombrePila, :actualizadoEn)
            ON CONFLICT (usuario_id) DO UPDATE
               SET correo = EXCLUDED.correo,
                   nombre_pila = EXCLUDED.nombre_pila,
                   actualizado_en = EXCLUDED.actualizado_en
            """)
    void guardar(@Param("usuarioId") UUID usuarioId, @Param("correo") String correo,
                 @Param("nombrePila") String nombrePila, @Param("actualizadoEn") Instant actualizadoEn);
}
