package pe.ayni.bank.core.infrastructure.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila de {@code core.cuenta}. */
@Entity
@Table(name = "cuenta")
public class CuentaEntity {

    @Id
    private UUID id;

    /**
     * Proyeccion local del usuario que vive en el schema {@code identity}. Sin clave
     * foranea entre schemas: son bounded contexts distintos y una FK los acoplaria de por
     * vida. Ver ADR-0004.
     */
    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "producto_id", nullable = false)
    private short productoId;

    @Column(nullable = false, length = 14)
    private String numero;

    @Column(nullable = false, length = 20)
    private String cci;

    @Column(nullable = false, length = 3)
    private String moneda;

    @Column(nullable = false, length = 16)
    private String estado;

    @Column(name = "abierta_en", nullable = false)
    private Instant abiertaEn;

    protected CuentaEntity() {
        // Exigido por JPA.
    }

    CuentaEntity(UUID id, UUID usuarioId, short productoId, String numero, String cci,
                 String moneda, String estado, Instant abiertaEn) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.productoId = productoId;
        this.numero = numero;
        this.cci = cci;
        this.moneda = moneda;
        this.estado = estado;
        this.abiertaEn = abiertaEn;
    }

    UUID getId() {
        return id;
    }

    UUID getUsuarioId() {
        return usuarioId;
    }

    short getProductoId() {
        return productoId;
    }

    String getNumero() {
        return numero;
    }

    String getCci() {
        return cci;
    }

    String getMoneda() {
        return moneda;
    }

    String getEstado() {
        return estado;
    }

    Instant getAbiertaEn() {
        return abiertaEn;
    }

    /** Sin numero ni CCI: con ellos se le puede transferir dinero al titular. */
    @Override
    public String toString() {
        return "CuentaEntity[id=" + id + ", estado=" + estado + "]";
    }
}
