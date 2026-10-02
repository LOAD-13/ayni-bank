package pe.ayni.bank.identity.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Cuenta los intentos fallidos de un usuario y decide cuándo se le pausa el ingreso.
 *
 * <p>Vive aparte de {@link Usuario} por dos razones. La primera es de diseño: el usuario es
 * quién eres, esto es cómo va tu sesión; mezclarlos convierte el agregado de identidad en un
 * cajón. La segunda es práctica: este contador cambia en cada intento fallido, y tenerlo en
 * la fila del usuario significaría escribir sobre el registro de identidad de alguien cada
 * vez que un atacante prueba una contraseña.
 *
 * <p><strong>El retardo es progresivo y no fijo.</strong> Un bloqueo de duración constante
 * se sortea esperando; uno que se duplica convierte un ataque por fuerza bruta en algo que
 * tarda días, sin dejar fuera para siempre a quien simplemente se equivocó de contraseña.
 * HU-04 exige cinco intentos antes del primer bloqueo.
 */
public final class ControlDeAcceso {

    /** Intentos consecutivos que se toleran antes de pausar el ingreso. Criterio de HU-04. */
    public static final int INTENTOS_TOLERADOS = 5;

    /** Primer bloqueo. Del sexto intento en adelante se duplica con cada fallo. */
    private static final Duration BLOQUEO_INICIAL = Duration.ofMinutes(5);

    /**
     * Techo del retardo progresivo. Sin él, unos cuantos fallos más dejan la cuenta
     * inaccesible durante semanas, y eso deja de ser una protección para convertirse en
     * una denegación de servicio contra el titular: basta que alguien falle a propósito.
     */
    private static final Duration BLOQUEO_MAXIMO = Duration.ofHours(1);

    private final UUID usuarioId;
    private final int fallosConsecutivos;
    private final Instant bloqueadoHasta;

    private ControlDeAcceso(UUID usuarioId, int fallosConsecutivos, Instant bloqueadoHasta) {
        this.usuarioId = Objects.requireNonNull(usuarioId);
        this.fallosConsecutivos = fallosConsecutivos;
        this.bloqueadoHasta = bloqueadoHasta;
    }

    /** Estado de quien nunca ha fallado. */
    public static ControlDeAcceso limpio(UUID usuarioId) {
        return new ControlDeAcceso(usuarioId, 0, null);
    }

    /** Reconstruye el estado leído de la base de datos. */
    public static ControlDeAcceso reconstituir(UUID usuarioId, int fallosConsecutivos,
                                               Instant bloqueadoHasta) {
        if (fallosConsecutivos < 0) {
            throw new IllegalArgumentException("Los fallos consecutivos no pueden ser negativos.");
        }
        return new ControlDeAcceso(usuarioId, fallosConsecutivos, bloqueadoHasta);
    }

    /**
     * Registra un fallo y devuelve el estado resultante.
     *
     * <p>Hasta el quinto fallo no hay bloqueo: solo se cuenta. A partir de ahí, cada fallo
     * duplica la espera hasta el techo.
     */
    public ControlDeAcceso registrarFallo(Instant momento) {
        int fallos = fallosConsecutivos + 1;
        if (fallos <= INTENTOS_TOLERADOS) {
            return new ControlDeAcceso(usuarioId, fallos, bloqueadoHasta);
        }
        return new ControlDeAcceso(usuarioId, fallos, momento.plus(esperaTras(fallos)));
    }

    /** Un acierto borra el historial: los fallos que cuentan son los consecutivos. */
    public ControlDeAcceso registrarAcierto() {
        return limpio(usuarioId);
    }

    public boolean estaBloqueado(Instant momento) {
        return bloqueadoHasta != null && momento.isBefore(bloqueadoHasta);
    }

    /** Lo que le queda de espera a quien está bloqueado. {@code ZERO} si no lo está. */
    public Duration esperaRestante(Instant momento) {
        return estaBloqueado(momento) ? Duration.between(momento, bloqueadoHasta) : Duration.ZERO;
    }

    /**
     * @return si este fallo es el que acaba de pausar el ingreso, y por tanto el momento de
     *         avisar al titular. Sin esta distinción, cada intento posterior le mandaría
     *         otro correo y el aviso se convertiría en el propio ataque.
     */
    public boolean acabaDeBloquearse() {
        return fallosConsecutivos == INTENTOS_TOLERADOS + 1;
    }

    private static Duration esperaTras(int fallos) {
        int duplicaciones = Math.min(fallos - INTENTOS_TOLERADOS - 1, 8);
        Duration espera = BLOQUEO_INICIAL.multipliedBy(1L << duplicaciones);
        return espera.compareTo(BLOQUEO_MAXIMO) > 0 ? BLOQUEO_MAXIMO : espera;
    }

    public UUID usuarioId() {
        return usuarioId;
    }

    public int fallosConsecutivos() {
        return fallosConsecutivos;
    }

    public Instant bloqueadoHasta() {
        return bloqueadoHasta;
    }

    @Override
    public String toString() {
        return "ControlDeAcceso[usuarioId=" + usuarioId
                + ", fallos=" + fallosConsecutivos + "]";
    }
}
