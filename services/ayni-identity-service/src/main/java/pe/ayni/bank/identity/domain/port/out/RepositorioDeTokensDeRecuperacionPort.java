package pe.ayni.bank.identity.domain.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import pe.ayni.bank.identity.domain.model.TokenDeRecuperacion;

/** Persistencia de los enlaces de recuperacion (HU-21). */
public interface RepositorioDeTokensDeRecuperacionPort {

    void guardar(TokenDeRecuperacion token);

    /** Solo devuelve tokens que no hayan sido anulados por uno posterior. */
    Optional<TokenDeRecuperacion> buscarPorHuella(String huella);

    /** Cuantos enlaces se emitieron para el usuario desde ese instante: es el freno anti-bombardeo. */
    long contarEmitidosDesde(UUID usuarioId, Instant desde);

    /**
     * Anula los enlaces sin usar del usuario. Se llama al emitir uno nuevo —solo vale el
     * ultimo que llego al correo— y al restablecer la contrasena.
     */
    void anularPendientesDe(UUID usuarioId, Instant momento);

    /**
     * Marca el token como usado solo si nadie lo uso antes.
     *
     * @return {@code false} si otra peticion se adelanto: dos clics simultaneos en el mismo
     *         enlace no pueden cambiar la contrasena dos veces
     */
    boolean marcarUsado(UUID tokenId, Instant momento);
}
