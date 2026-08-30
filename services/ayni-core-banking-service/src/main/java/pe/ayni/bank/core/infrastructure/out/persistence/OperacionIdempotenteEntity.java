package pe.ayni.bank.core.infrastructure.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila de {@code core.operacion_idempotente}. */
@Entity
@Table(name = "operacion_idempotente")
public class OperacionIdempotenteEntity {

    /** La clave de idempotencia ES la clave primaria: el duplicado lo rechaza la base. */
    @Id
    private UUID clave;

    @Column(name = "resultado_id", nullable = false)
    private UUID resultadoId;

    @Column(name = "registrado_en", nullable = false)
    private Instant registradoEn;

    protected OperacionIdempotenteEntity() {
        // Exigido por JPA.
    }

    OperacionIdempotenteEntity(UUID clave, UUID resultadoId, Instant registradoEn) {
        this.clave = clave;
        this.resultadoId = resultadoId;
        this.registradoEn = registradoEn;
    }

    UUID getResultadoId() {
        return resultadoId;
    }

    @Override
    public String toString() {
        return "OperacionIdempotenteEntity[clave=" + clave + "]";
    }
}
