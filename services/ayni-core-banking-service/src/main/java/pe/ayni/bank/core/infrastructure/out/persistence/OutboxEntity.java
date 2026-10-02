package pe.ayni.bank.core.infrastructure.out.persistence;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila de {@code core.outbox}. Ver ADR-0003. */
@Entity
@Table(name = "outbox")
public class OutboxEntity {

    @Id
    private UUID id;

    @Column(name = "agregado_tipo", nullable = false, length = 32)
    private String agregadoTipo;

    @Column(name = "agregado_id", nullable = false)
    private UUID agregadoId;

    @Column(name = "tipo_evento", nullable = false, length = 64)
    private String tipoEvento;

    /**
     * La carga del evento en JSON.
     *
     * <p>{@code JdbcTypeCode(JSON)} es imprescindible: sin el, Hibernate manda un
     * {@code varchar} a una columna {@code jsonb} y PostgreSQL rechaza la conversion
     * implicita con un error que no menciona ni la columna ni el tipo.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String carga;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "publicado_en")
    private Instant publicadoEn;

    @Column(nullable = false)
    private short intentos;

    @Column(name = "ultimo_error")
    private String ultimoError;

    protected OutboxEntity() {
        // Exigido por JPA.
    }

    OutboxEntity(UUID id, String agregadoTipo, UUID agregadoId, String tipoEvento,
                 String carga, Instant creadoEn) {
        this.id = id;
        this.agregadoTipo = agregadoTipo;
        this.agregadoId = agregadoId;
        this.tipoEvento = tipoEvento;
        this.carga = carga;
        this.creadoEn = creadoEn;
        this.intentos = 0;
    }

    void marcarPublicado(Instant momento) {
        this.publicadoEn = momento;
    }

    void anotarFallo(String error) {
        this.intentos++;
        // Se recorta: un stack trace entero por evento fallido llena la tabla de ruido y
        // lo que hace falta para diagnosticar esta siempre en las primeras lineas.
        this.ultimoError = error != null && error.length() > 500
                ? error.substring(0, 500)
                : error;
    }

    UUID getId() {
        return id;
    }

    String getTipoEvento() {
        return tipoEvento;
    }

    String getCarga() {
        return carga;
    }

    UUID getAgregadoId() {
        return agregadoId;
    }

    short getIntentos() {
        return intentos;
    }

    @Override
    public String toString() {
        return "OutboxEntity[id=" + id + ", evento=" + tipoEvento + "]";
    }
}
