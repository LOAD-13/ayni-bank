package pe.ayni.bank.core.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Raiz del agregado de cuenta.
 *
 * <p><strong>No tiene saldo.</strong> Es la decision central de este modelo y la que
 * sostiene todo lo demas. Un saldo guardado es un numero que alguien puede escribir, y que
 * tarde o temprano deja de cuadrar con los movimientos que lo produjeron; cuando eso pasa
 * en un banco, no hay forma de saber cual de los dos miente. Aqui el saldo se
 * {@link #saldo(List) calcula} a partir de los asientos, de modo que la pregunta «por que
 * tengo este saldo» siempre tiene respuesta y no existe ninguna operacion capaz de
 * cambiarlo sin dejar rastro.
 *
 * <p>Inmutable, como {@code Usuario} en identidad y por el mismo motivo: un objeto que
 * cambia de estado mientras otro hilo lo lee produce incoherencias que solo aparecen bajo
 * carga.
 */
public final class Cuenta {

    private final UUID id;
    private final UUID usuarioId;
    private final short productoId;
    private final NumeroDeCuenta numero;
    private final Cci cci;
    private final Moneda moneda;
    private final EstadoCuenta estado;
    private final Instant abiertaEn;

    private Cuenta(UUID id, UUID usuarioId, short productoId, NumeroDeCuenta numero,
                   Cci cci, Moneda moneda, EstadoCuenta estado, Instant abiertaEn) {
        this.id = Objects.requireNonNull(id);
        this.usuarioId = Objects.requireNonNull(usuarioId);
        this.productoId = productoId;
        this.numero = Objects.requireNonNull(numero);
        this.cci = Objects.requireNonNull(cci);
        this.moneda = Objects.requireNonNull(moneda);
        this.estado = Objects.requireNonNull(estado);
        this.abiertaEn = Objects.requireNonNull(abiertaEn);
    }

    /**
     * Abre una cuenta nueva.
     *
     * <p>Nace ACTIVA y sin un solo asiento, es decir con saldo cero. No hay constructor
     * publico ni forma de crearla con saldo inicial: meter dinero exige un asiento, y un
     * asiento deja rastro. Es lo que impide que aparezca dinero de la nada.
     */
    public static Cuenta abrir(UUID id, UUID usuarioId, short productoId,
                               NumeroDeCuenta numero, Moneda moneda, Instant momento) {
        if (numero.moneda() != moneda) {
            throw new IllegalArgumentException(
                    "El numero de cuenta no corresponde a la moneda indicada.");
        }
        return new Cuenta(id, usuarioId, productoId, numero, Cci.para(numero),
                moneda, EstadoCuenta.ACTIVA, momento);
    }

    public static Cuenta reconstituir(UUID id, UUID usuarioId, short productoId,
                                      NumeroDeCuenta numero, Cci cci, Moneda moneda,
                                      EstadoCuenta estado, Instant abiertaEn) {
        return new Cuenta(id, usuarioId, productoId, numero, cci, moneda, estado, abiertaEn);
    }

    /**
     * El saldo, como suma de los asientos que se le pasen.
     *
     * <p>Recibe los asientos en lugar de guardarlos dentro a proposito. Una cuenta con dos
     * anos de movimientos tiene miles, y cargarlos todos cada vez que alguien mira su saldo
     * no se sostiene; lo que se hace en produccion es sumarlos en la base o mantener una
     * proyeccion. Que la operacion viva aqui garantiza que la <em>definicion</em> de saldo
     * sea una sola y este en el dominio, no repartida por consultas SQL.
     */
    public Dinero saldo(List<Asiento> asientos) {
        Dinero total = Dinero.cero(moneda);
        for (Asiento asiento : asientos) {
            if (!asiento.cuentaId().equals(id)) {
                throw new IllegalArgumentException(
                        "Se intento calcular el saldo con asientos de otra cuenta.");
            }
            total = asiento.tipo() == TipoDeAsiento.ABONO
                    ? total.mas(asiento.importe())
                    : total.menos(asiento.importe());
        }
        return total;
    }

    public Cuenta bloquear() {
        if (estado == EstadoCuenta.CERRADA) {
            throw new TransicionDeCuentaInvalidaException(estado, EstadoCuenta.BLOQUEADA);
        }
        return new Cuenta(id, usuarioId, productoId, numero, cci, moneda,
                EstadoCuenta.BLOQUEADA, abiertaEn);
    }

    public boolean admiteMovimientos() {
        return estado.admiteMovimientos();
    }

    public UUID id() {
        return id;
    }

    public UUID usuarioId() {
        return usuarioId;
    }

    public short productoId() {
        return productoId;
    }

    public NumeroDeCuenta numero() {
        return numero;
    }

    public Cci cci() {
        return cci;
    }

    public Moneda moneda() {
        return moneda;
    }

    public EstadoCuenta estado() {
        return estado;
    }

    public Instant abiertaEn() {
        return abiertaEn;
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof Cuenta cuenta && id.equals(cuenta.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /** Sin numero ni CCI: los dos identifican al titular ante terceros. */
    @Override
    public String toString() {
        return "Cuenta[id=" + id + ", moneda=" + moneda + ", estado=" + estado + "]";
    }
}
