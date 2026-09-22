package pe.ayni.bank.identity.infrastructure.out.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import pe.ayni.bank.identity.domain.model.DesafioPorCodigo;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;

/** Entidad JPA para la tabla desafio_por_codigo (HU-22 / AYNI-128). */
@Entity
@Table(name = "desafio_por_codigo")
public class DesafioPorCodigoEntity {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_factor", nullable = false)
    private TipoDeSegundoFactor tipoFactor;

    @Column(name = "hash_codigo", nullable = false)
    private String hashCodigo;

    @Column(name = "intentos_realizados", nullable = false)
    private int intentosRealizados;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(name = "verificado_en")
    private Instant verificadoEn;

    public DesafioPorCodigoEntity() {
        // Constructor sin argumentos exigido por la especificación JPA
    }

    public static DesafioPorCodigoEntity desdeDominio(DesafioPorCodigo d) {
        DesafioPorCodigoEntity e = new DesafioPorCodigoEntity();
        e.id = d.id();
        e.usuarioId = d.usuarioId();
        e.tipoFactor = d.tipoFactor();
        e.hashCodigo = d.hashCodigo();
        e.intentosRealizados = d.intentosRealizados();
        e.creadoEn = d.creadoEn();
        e.expiraEn = d.expiraEn();
        e.verificadoEn = d.verificadoEn();
        return e;
    }

    public DesafioPorCodigo aDominio() {
        return DesafioPorCodigo.reconstituir(
                id, usuarioId, tipoFactor, hashCodigo, intentosRealizados,
                creadoEn, expiraEn, verificadoEn);
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUsuarioId() { return usuarioId; }
    public void setUsuarioId(UUID usuarioId) { this.usuarioId = usuarioId; }
    public TipoDeSegundoFactor getTipoFactor() { return tipoFactor; }
    public void setTipoFactor(TipoDeSegundoFactor tipoFactor) { this.tipoFactor = tipoFactor; }
    public String getHashCodigo() { return hashCodigo; }
    public void setHashCodigo(String hashCodigo) { this.hashCodigo = hashCodigo; }
    public int getIntentosRealizados() { return intentosRealizados; }
    public void setIntentosRealizados(int intentosRealizados) { this.intentosRealizados = intentosRealizados; }
    public Instant getCreadoEn() { return creadoEn; }
    public void setCreadoEn(Instant creadoEn) { this.creadoEn = creadoEn; }
    public Instant getExpiraEn() { return expiraEn; }
    public void setExpiraEn(Instant expiraEn) { this.expiraEn = expiraEn; }
    public Instant getVerificadoEn() { return verificadoEn; }
    public void setVerificadoEn(Instant verificadoEn) { this.verificadoEn = verificadoEn; }
}
