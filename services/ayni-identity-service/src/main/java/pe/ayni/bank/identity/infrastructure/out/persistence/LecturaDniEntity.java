package pe.ayni.bank.identity.infrastructure.out.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila de {@code identity.lectura_dni} (V8). El numero, siempre cifrado. */
@Entity
@Table(name = "lectura_dni")
class LecturaDniEntity {

    @Id
    private UUID id;

    @Column(name = "solicitud_id", nullable = false)
    private UUID solicitudId;

    @Column(nullable = false, length = 24)
    private String fuente;

    @Column(nullable = false)
    private boolean confiable;

    /** Criptograma AES-256-GCM. Jamas el numero en claro. */
    @Column(name = "numero_cifrado", nullable = false, length = 255)
    private String numeroCifrado;

    @Column(name = "numero_ultimos4", nullable = false, length = 4)
    private String numeroUltimos4;

    @Column(nullable = false, length = 80)
    private String nombres;

    @Column(nullable = false, length = 120)
    private String apellidos;

    @Column(name = "fecha_nacimiento", nullable = false)
    private LocalDate fechaNacimiento;

    @Column(nullable = false, length = 1)
    private String sexo;

    @Column(name = "fecha_emision")
    private LocalDate fechaEmision;

    @Column(name = "leida_en", nullable = false)
    private Instant leidaEn;

    protected LecturaDniEntity() {
        // Exigido por JPA.
    }

    @SuppressWarnings("java:S107") // Una columna por parametro: agruparlos solo moveria el problema.
    LecturaDniEntity(UUID id, UUID solicitudId, String fuente, boolean confiable, String numeroCifrado,
                     String numeroUltimos4, String nombres, String apellidos, LocalDate fechaNacimiento,
                     String sexo, LocalDate fechaEmision, Instant leidaEn) {
        this.id = id;
        this.solicitudId = solicitudId;
        this.fuente = fuente;
        this.confiable = confiable;
        this.numeroCifrado = numeroCifrado;
        this.numeroUltimos4 = numeroUltimos4;
        this.nombres = nombres;
        this.apellidos = apellidos;
        this.fechaNacimiento = fechaNacimiento;
        this.sexo = sexo;
        this.fechaEmision = fechaEmision;
        this.leidaEn = leidaEn;
    }

    String getFuente() {
        return fuente;
    }

    boolean isConfiable() {
        return confiable;
    }

    String getNumeroCifrado() {
        return numeroCifrado;
    }

    String getNombres() {
        return nombres;
    }

    String getApellidos() {
        return apellidos;
    }

    LocalDate getFechaNacimiento() {
        return fechaNacimiento;
    }

    String getSexo() {
        return sexo;
    }

    LocalDate getFechaEmision() {
        return fechaEmision;
    }

    /** Sin ningun dato del DNI: es lo que imprimiria cualquier traza que lleve la entidad. */
    @Override
    public String toString() {
        return "LecturaDniEntity[id=" + id + ", fuente=" + fuente + "]";
    }
}
