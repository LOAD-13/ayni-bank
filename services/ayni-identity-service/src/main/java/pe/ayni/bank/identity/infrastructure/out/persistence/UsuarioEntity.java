package pe.ayni.bank.identity.infrastructure.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Fila de la tabla {@code identity.usuario}.
 *
 * <p>Existe separada de {@code Usuario} y no como la misma clase anotada. Anotar el
 * agregado con JPA lo ataria a Hibernate: el dominio dejaria de compilar sin el, ArchUnit
 * romperia el build, y las decisiones de persistencia —tipos de columna, estrategias de
 * carga— acabarian condicionando el modelo de negocio. El precio es este mapeador; la
 * alternativa es que la base de datos dicte las reglas del banco.
 */
@Entity
@Table(name = "usuario")
public class UsuarioEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 254)
    private String correo;

    @Column(nullable = false, length = 9)
    private String celular;

    @Column(name = "contrasena_hash", nullable = false, length = 255)
    private String contrasenaHash;

    @Column(nullable = false, length = 24)
    private String estado;

    @Column(name = "consentimiento_en", nullable = false)
    private Instant consentimientoEn;

    @Column(name = "terminos_version", nullable = false, length = 16)
    private String terminosVersion;

    @Column(name = "registrado_en", nullable = false)
    private Instant registradoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    /** Exigido por JPA. No lo use nadie mas. */
    protected UsuarioEntity() {
        // Hibernate necesita un constructor sin argumentos para instanciar por reflexion.
    }

    UsuarioEntity(UUID id, String correo, String celular, String contrasenaHash, String estado,
                  Instant consentimientoEn, String terminosVersion,
                  Instant registradoEn, Instant actualizadoEn) {
        this.id = id;
        this.correo = correo;
        this.celular = celular;
        this.contrasenaHash = contrasenaHash;
        this.estado = estado;
        this.consentimientoEn = consentimientoEn;
        this.terminosVersion = terminosVersion;
        this.registradoEn = registradoEn;
        this.actualizadoEn = actualizadoEn;
    }

    UUID getId() {
        return id;
    }

    String getCorreo() {
        return correo;
    }

    String getCelular() {
        return celular;
    }

    String getContrasenaHash() {
        return contrasenaHash;
    }

    String getEstado() {
        return estado;
    }

    Instant getConsentimientoEn() {
        return consentimientoEn;
    }

    String getTerminosVersion() {
        return terminosVersion;
    }

    Instant getRegistradoEn() {
        return registradoEn;
    }

    Instant getActualizadoEn() {
        return actualizadoEn;
    }

    /** Sin correo, sin celular y sin derivacion: esto acaba en logs de Hibernate. */
    @Override
    public String toString() {
        return "UsuarioEntity[id=" + id + ", estado=" + estado + "]";
    }
}
