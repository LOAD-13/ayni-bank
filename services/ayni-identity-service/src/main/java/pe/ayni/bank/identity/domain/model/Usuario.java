package pe.ayni.bank.identity.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Raiz del agregado de identidad.
 *
 * <p>Inmutable: cada transicion devuelve una instancia nueva en lugar de mutar la actual.
 * En un sistema financiero eso importa mas que en otros: un objeto que cambia de estado a
 * mitad de una transaccion, mientras otro hilo lo lee, produce incoherencias que solo
 * aparecen bajo carga y que son practicamente imposibles de reproducir despues.
 *
 * <p>No hay constructor publico. Un usuario se crea por {@link #registrar}, que es lo unico
 * que garantiza los tres invariantes de HU-01: estado inicial PENDIENTE_VERIFICACION,
 * consentimiento demostrable y contrasena derivada. Un constructor abierto permitiria
 * construir un usuario ACTIVO sin KYC, que es exactamente lo que este diseno impide.
 */
public final class Usuario {

    private final UUID id;
    private final CorreoElectronico correo;
    private final Celular celular;
    private final ContrasenaCifrada contrasena;
    private final EstadoUsuario estado;
    private final Consentimiento consentimiento;
    private final Instant registradoEn;

    private Usuario(UUID id, CorreoElectronico correo, Celular celular,
                    ContrasenaCifrada contrasena, EstadoUsuario estado,
                    Consentimiento consentimiento, Instant registradoEn) {
        this.id = Objects.requireNonNull(id);
        this.correo = Objects.requireNonNull(correo);
        this.celular = Objects.requireNonNull(celular);
        this.contrasena = Objects.requireNonNull(contrasena);
        this.estado = Objects.requireNonNull(estado);
        this.consentimiento = Objects.requireNonNull(consentimiento);
        this.registradoEn = Objects.requireNonNull(registradoEn);
    }

    /**
     * Registra un visitante. Queda en PENDIENTE_VERIFICACION: existe, pero no opera hasta
     * superar el KYC.
     */
    public static Usuario registrar(UUID id, CorreoElectronico correo, Celular celular,
                                    ContrasenaCifrada contrasena, Consentimiento consentimiento,
                                    Instant momento) {
        return new Usuario(id, correo, celular, contrasena,
                EstadoUsuario.PENDIENTE_VERIFICACION, consentimiento, momento);
    }

    /**
     * Reconstruye un usuario ya persistido. Lo usa exclusivamente el adaptador de
     * persistencia: a diferencia de {@link #registrar}, admite cualquier estado, porque
     * reconstruir no es crear y la base de datos ya contiene usuarios ACTIVOS.
     */
    public static Usuario reconstituir(UUID id, CorreoElectronico correo, Celular celular,
                                       ContrasenaCifrada contrasena, EstadoUsuario estado,
                                       Consentimiento consentimiento, Instant registradoEn) {
        return new Usuario(id, correo, celular, contrasena, estado, consentimiento, registradoEn);
    }

    /** Transicion tras un KYC aprobado. */
    public Usuario activar() {
        if (estado != EstadoUsuario.PENDIENTE_VERIFICACION && estado != EstadoUsuario.EN_REVISION) {
            throw new TransicionDeEstadoInvalidaException(estado, EstadoUsuario.ACTIVO);
        }
        return new Usuario(id, correo, celular, contrasena,
                EstadoUsuario.ACTIVO, consentimiento, registradoEn);
    }

    /** Transicion tras un KYC que no alcanza el umbral de similitud. */
    public Usuario derivarARevision() {
        if (estado != EstadoUsuario.PENDIENTE_VERIFICACION) {
            throw new TransicionDeEstadoInvalidaException(estado, EstadoUsuario.EN_REVISION);
        }
        return new Usuario(id, correo, celular, contrasena,
                EstadoUsuario.EN_REVISION, consentimiento, registradoEn);
    }

    public Usuario bloquear() {
        return new Usuario(id, correo, celular, contrasena,
                EstadoUsuario.BLOQUEADO, consentimiento, registradoEn);
    }

    public UUID id() {
        return id;
    }

    public CorreoElectronico correo() {
        return correo;
    }

    public Celular celular() {
        return celular;
    }

    public ContrasenaCifrada contrasena() {
        return contrasena;
    }

    public EstadoUsuario estado() {
        return estado;
    }

    public Consentimiento consentimiento() {
        return consentimiento;
    }

    public Instant registradoEn() {
        return registradoEn;
    }

    public boolean puedeOperar() {
        return estado.puedeOperar();
    }

    @Override
    public boolean equals(Object otro) {
        // Identidad por id y no por atributos: es una entidad. Dos instancias con el mismo
        // id son la misma persona aunque una este ACTIVA y la otra sea una lectura anterior.
        if (this == otro) {
            return true;
        }
        return otro instanceof Usuario usuario && id.equals(usuario.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /**
     * Sin correo, sin celular y sin derivacion de la contrasena. Este {@code toString()} va
     * a acabar en un log: es lo que imprime cualquier traza de excepcion que lleve un
     * usuario dentro. Lo que no aparezca aqui no puede filtrarse por esa via.
     */
    @Override
    public String toString() {
        return "Usuario[id=" + id + ", estado=" + estado + "]";
    }
}
