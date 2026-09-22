package pe.ayni.bank.identity.infrastructure.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;

@Entity
@Table(name = "metodo_segundo_factor")
public class MetodoSegundoFactorEntity {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private TipoDeSegundoFactor tipo;

    @Column(name = "secreto")
    private String secreto;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "confirmado_en")
    private Instant confirmadoEn;

    public MetodoSegundoFactorEntity() {}

    public MetodoSegundoFactorEntity(UUID id, UUID usuarioId, TipoDeSegundoFactor tipo,
                                    String secreto, Instant creadoEn, Instant confirmadoEn) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.tipo = tipo;
        this.secreto = secreto;
        this.creadoEn = creadoEn;
        this.confirmadoEn = confirmadoEn;
    }

    public UUID getId() { return id; }
    public UUID getUsuarioId() { return usuarioId; }
    public TipoDeSegundoFactor getTipo() { return tipo; }
    public String getSecreto() { return secreto; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getConfirmadoEn() { return confirmadoEn; }
}
