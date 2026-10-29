package pe.ayni.bank.notification.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Un aviso a un titular y su ciclo: pendiente, enviada, o fallida tras agotar los reintentos. */
public final class Notificacion {

    private final UUID id;
    private final UUID eventoId;
    private final UUID usuarioId;
    private final TipoDeAviso tipo;
    private final Map<String, String> datos;
    private final Instant creadaEn;
    private EstadoDeNotificacion estado;
    private int intentos;
    private Instant proximoIntentoEn;
    private Instant enviadaEn;

    @SuppressWarnings("java:S107") // Reconstruye la fila completa desde la base: son sus columnas.
    public Notificacion(UUID id, UUID eventoId, UUID usuarioId, TipoDeAviso tipo, Map<String, String> datos,
                        EstadoDeNotificacion estado, int intentos, Instant proximoIntentoEn, Instant creadaEn,
                        Instant enviadaEn) {
        this.id = id;
        this.eventoId = eventoId;
        this.usuarioId = usuarioId;
        this.tipo = tipo;
        this.datos = Map.copyOf(datos);
        this.estado = estado;
        this.intentos = intentos;
        this.proximoIntentoEn = proximoIntentoEn;
        this.creadaEn = creadaEn;
        this.enviadaEn = enviadaEn;
    }

    /** Una notificacion nueva sale en la siguiente pasada del despachador. */
    public static Notificacion de(AvisoDeMovimiento aviso, Instant ahora) {
        return new Notificacion(UUID.randomUUID(), aviso.eventoId(), aviso.usuarioId(), aviso.tipo(),
                aviso.datos(), EstadoDeNotificacion.PENDIENTE, 0, ahora, ahora, null);
    }

    public IntentoDeEnvio marcarEnviada(Instant ahora) {
        exigirPendiente();
        intentos++;
        estado = EstadoDeNotificacion.ENVIADA;
        enviadaEn = ahora;
        return new IntentoDeEnvio(UUID.randomUUID(), id, intentos, true, null, ahora);
    }

    /** Anota el fallo y reprograma, o la da por fallida si ya no quedan intentos. */
    public IntentoDeEnvio registrarFallo(String detalle, Instant ahora, PoliticaDeReintentos politica) {
        exigirPendiente();
        intentos++;
        if (politica.seAgotaron(intentos)) {
            estado = EstadoDeNotificacion.FALLIDA;
        } else {
            proximoIntentoEn = ahora.plus(politica.esperaTras(intentos));
        }
        return new IntentoDeEnvio(UUID.randomUUID(), id, intentos, false, detalle, ahora);
    }

    private void exigirPendiente() {
        if (estado != EstadoDeNotificacion.PENDIENTE) {
            throw new IllegalStateException("La notificacion " + id + " ya esta " + estado);
        }
    }

    public UUID id() {
        return id;
    }

    public UUID eventoId() {
        return eventoId;
    }

    public UUID usuarioId() {
        return usuarioId;
    }

    public TipoDeAviso tipo() {
        return tipo;
    }

    public Map<String, String> datos() {
        return datos;
    }

    public EstadoDeNotificacion estado() {
        return estado;
    }

    public int intentos() {
        return intentos;
    }

    public Instant proximoIntentoEn() {
        return proximoIntentoEn;
    }

    public Instant creadaEn() {
        return creadaEn;
    }

    public Instant enviadaEn() {
        return enviadaEn;
    }
}
