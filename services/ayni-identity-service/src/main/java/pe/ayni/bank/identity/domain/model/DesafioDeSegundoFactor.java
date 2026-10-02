package pe.ayni.bank.identity.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * El vale que se entrega tras validar la contrasena y que la segunda pantalla canjea por
 * un codigo de verificacion.
 *
 * <p>Existe para que la pantalla del codigo no tenga que volver a enviar la contrasena.
 * Las dos pantallas aprobadas —«Entra a tu banca» y «Confirma que eres tu»— son dos
 * peticiones; sin este vale, la segunda tendria que llevar la contrasena otra vez, lo que
 * significa guardarla en el navegador entre pantalla y pantalla.
 *
 * <p>Dura dos minutos. No es una sesion: es el hueco entre teclear la contrasena y teclear
 * el codigo. Cuanto mas corto, menos vale robarlo.
 */
public record DesafioDeSegundoFactor(UUID id, UUID usuarioId, Instant expiraEn) {

    public static final Duration VIGENCIA = Duration.ofMinutes(2);

    public DesafioDeSegundoFactor {
        Objects.requireNonNull(id);
        Objects.requireNonNull(usuarioId);
        Objects.requireNonNull(expiraEn);
    }

    public static DesafioDeSegundoFactor abrir(UUID usuarioId, Instant momento) {
        return new DesafioDeSegundoFactor(UUID.randomUUID(), usuarioId,
                momento.plus(VIGENCIA));
    }

    public boolean haCaducado(Instant momento) {
        return !momento.isBefore(expiraEn);
    }
}
