package pe.ayni.bank.identity.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * El segundo factor de un usuario: su secreto TOTP y si llego a confirmarlo.
 *
 * <p><strong>Por que hay un estado «sin confirmar».</strong> Dar de alta el segundo factor
 * son dos gestos: el servidor genera el secreto y el usuario lo escanea, y despues el
 * usuario teclea un codigo para demostrar que su aplicacion lo guardo bien. Si se diera por
 * confirmado tras el primero, alguien que cierra la pantalla antes de escanear se quedaria
 * con un segundo factor activo que no puede generar codigos: sin acceso, y sin forma de
 * recuperarlo que no sea soporte.
 *
 * <p>Mientras no este confirmado, el secreto se puede regenerar. Una vez confirmado, no:
 * cambiarlo exigiria autenticarse, y eso es otra historia de usuario.
 */
public final class SegundoFactor {

    private final UUID usuarioId;
    private final SecretoTotp secreto;
    private final Instant creadoEn;
    private final Instant confirmadoEn;

    private SegundoFactor(UUID usuarioId, SecretoTotp secreto, Instant creadoEn,
                          Instant confirmadoEn) {
        this.usuarioId = Objects.requireNonNull(usuarioId);
        this.secreto = Objects.requireNonNull(secreto);
        this.creadoEn = Objects.requireNonNull(creadoEn);
        this.confirmadoEn = confirmadoEn;
    }

    public static SegundoFactor inscribir(UUID usuarioId, SecretoTotp secreto, Instant momento) {
        return new SegundoFactor(usuarioId, secreto, momento, null);
    }

    public static SegundoFactor reconstituir(UUID usuarioId, SecretoTotp secreto,
                                             Instant creadoEn, Instant confirmadoEn) {
        return new SegundoFactor(usuarioId, secreto, creadoEn, confirmadoEn);
    }

    public SegundoFactor confirmar(Instant momento) {
        if (estaConfirmado()) {
            return this;
        }
        return new SegundoFactor(usuarioId, secreto, creadoEn, momento);
    }

    public boolean estaConfirmado() {
        return confirmadoEn != null;
    }

    public UUID usuarioId() {
        return usuarioId;
    }

    public SecretoTotp secreto() {
        return secreto;
    }

    public Instant creadoEn() {
        return creadoEn;
    }

    public Instant confirmadoEn() {
        return confirmadoEn;
    }

    @Override
    public String toString() {
        return "SegundoFactor[usuarioId=" + usuarioId + ", confirmado=" + estaConfirmado() + "]";
    }
}
