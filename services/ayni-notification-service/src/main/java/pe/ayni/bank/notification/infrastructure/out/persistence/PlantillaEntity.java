package pe.ayni.bank.notification.infrastructure.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/** El catalogo lo escriben las migraciones; el servicio solo lo lee. */
@Entity
@Immutable
@Table(name = "plantilla")
public class PlantillaEntity {

    @Id
    private short id;

    @Column(nullable = false)
    private String codigo;

    @Column(nullable = false)
    private String canal;

    @Column(nullable = false, columnDefinition = "bpchar(2)")
    private String idioma;

    private String asunto;

    @Column(nullable = false)
    private String cuerpo;

    @Column(nullable = false)
    private boolean activo;

    protected PlantillaEntity() {
    }

    public String getAsunto() {
        return asunto;
    }

    public String getCuerpo() {
        return cuerpo;
    }
}
