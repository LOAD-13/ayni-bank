package pe.ayni.bank.identity.infrastructure.out.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Fila de {@code identity.solicitud_onboarding}. */
@Entity
@Table(name = "solicitud_onboarding")
public class SolicitudOnboardingEntity {

    @Id
    private UUID id;

    /**
     * Nulo en las solicitudes senuelo. Ver ADR-0008.
     *
     * <p>Se mapea como UUID suelto y no como {@code @ManyToOne} a proposito: una relacion
     * gestionada obligaria a que exista un usuario, que es justo lo que el senuelo no
     * tiene, y ademas arrastraria carga perezosa donde solo hace falta una clave.
     */
    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(nullable = false, length = 32)
    private String estado;

    @Column(name = "paso_actual", nullable = false)
    private short pasoActual;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    @Column(name = "actualizada_en", nullable = false)
    private Instant actualizadaEn;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    // ─── Identidad declarada en el paso 1 ──────────────────────────────────
    // Nula en los senuelos. Es el termino de comparacion del OCR en HU-02: lo que la
    // persona dijo ser, frente a lo que diga el documento.

    @Column(name = "nombres_declarados", length = 80)
    private String nombresDeclarados;

    @Column(name = "apellidos_declarados", length = 120)
    private String apellidosDeclarados;

    @Column(name = "tipo_documento_declarado", length = 16)
    private String tipoDocumentoDeclarado;

    /** Criptograma AES-256-GCM. Jamas el numero en claro. */
    @Column(name = "documento_declarado", length = 255)
    private String documentoDeclarado;

    @Column(name = "documento_declarado_ultimos4", length = 4)
    private String documentoDeclaradoUltimos4;

    @Column(name = "fecha_nacimiento_declarada")
    private LocalDate fechaNacimientoDeclarada;

    /** Fotos del anverso rechazadas. Ver ADR-0021 y ADR-0028. */
    @Column(name = "intentos_kyc_anverso", nullable = false)
    private short intentosKycAnverso;

    /** Fotos del reverso rechazadas, o lecturas fallidas del OCR. Ver ADR-0028. */
    @Column(name = "intentos_kyc_reverso", nullable = false)
    private short intentosKycReverso;

    /**
     * Bloqueo optimista: sin el, dos fallos simultaneos del mismo lado leen el mismo contador
     * y uno de los dos incrementos se pierde. Ver V8.
     *
     * <p>{@code Long} y no {@code long}: nulo en una solicitud recien creada es lo que le dice
     * a Spring Data que es nueva y hay que insertarla. Con un primitivo, el id asignado a mano
     * la haria pasar por existente, y Hibernate 6.6 rechaza fusionar una entidad versionada
     * que no esta en la base.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    protected SolicitudOnboardingEntity() {
        // Exigido por JPA.
    }

    SolicitudOnboardingEntity(UUID id, UUID usuarioId, String estado, short pasoActual,
                              Instant creadaEn, Instant actualizadaEn, Instant expiraEn) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.estado = estado;
        this.pasoActual = pasoActual;
        this.creadaEn = creadaEn;
        this.actualizadaEn = actualizadaEn;
        this.expiraEn = expiraEn;
    }

    void aprobar(Instant momento) {
        this.estado = "APROBADA";
        this.pasoActual = 5;
        this.actualizadaEn = momento;
    }

    /**
     * Suma un intento fallido de ese lado y devuelve el total acumulado. Nunca pasa de
     * {@code maximo}: la restriccion de la tabla lo prohibe, y quien decide que hacer al
     * llegar ahi es el caso de uso.
     */
    short incrementarIntentosDeVerificacionKyc(boolean anverso, short maximo, Instant momento) {
        this.actualizadaEn = momento;
        if (anverso) {
            this.intentosKycAnverso = (short) Math.min(this.intentosKycAnverso + 1, maximo);
            return this.intentosKycAnverso;
        }
        this.intentosKycReverso = (short) Math.min(this.intentosKycReverso + 1, maximo);
        return this.intentosKycReverso;
    }

    void derivarARevisionManual(Instant momento) {
        this.estado = "EN_REVISION_MANUAL";
        this.actualizadaEn = momento;
    }

    void marcarDocumentoCargado(Instant momento) {
        this.estado = "DOCUMENTO_CARGADO";
        this.actualizadaEn = momento;
    }

    void declarar(String nombres, String apellidos, String tipoDocumento,
                  String documentoCifrado, String ultimos4, LocalDate fechaNacimiento) {
        this.nombresDeclarados = nombres;
        this.apellidosDeclarados = apellidos;
        this.tipoDocumentoDeclarado = tipoDocumento;
        this.documentoDeclarado = documentoCifrado;
        this.documentoDeclaradoUltimos4 = ultimos4;
        this.fechaNacimientoDeclarada = fechaNacimiento;
    }

    UUID getId() {
        return id;
    }

    String getNombresDeclarados() {
        return nombresDeclarados;
    }

    UUID getUsuarioId() {
        return usuarioId;
    }

    Instant getExpiraEn() {
        return expiraEn;
    }

    String getEstado() {
        return estado;
    }

    String getApellidosDeclarados() {
        return apellidosDeclarados;
    }

    String getTipoDocumentoDeclarado() {
        return tipoDocumentoDeclarado;
    }

    String getDocumentoDeclarado() {
        return documentoDeclarado;
    }

    LocalDate getFechaNacimientoDeclarada() {
        return fechaNacimientoDeclarada;
    }

    short getIntentosKycAnverso() {
        return intentosKycAnverso;
    }

    short getIntentosKycReverso() {
        return intentosKycReverso;
    }

    /**
     * Sin ninguno de los datos declarados. Este {@code toString()} es lo que imprime
     * cualquier traza que lleve la entidad dentro.
     */
    @Override
    public String toString() {
        return "SolicitudOnboardingEntity[id=" + id + ", estado=" + estado + "]";
    }
}
