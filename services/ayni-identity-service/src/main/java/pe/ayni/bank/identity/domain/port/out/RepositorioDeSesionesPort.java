package pe.ayni.bank.identity.domain.port.out;

import java.util.Optional;
import java.util.UUID;

import pe.ayni.bank.identity.domain.model.DesafioDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.RefreshToken;

/**
 * Estado efimero de la sesion: el desafio de segundo factor y los tokens de renovacion.
 *
 * <p>Van en el mismo puerto porque son la misma cosa vista en dos momentos —lo que dura
 * entre pantalla y pantalla, y lo que dura entre visita y visita— y porque el caso de uso
 * los maneja siempre junto.
 */
public interface RepositorioDeSesionesPort {

    void guardarDesafio(DesafioDeSegundoFactor desafio);

    Optional<DesafioDeSegundoFactor> buscarDesafio(UUID desafioId);

    /** Un desafio se canjea una sola vez. */
    void consumirDesafio(UUID desafioId);

    RefreshToken guardarToken(RefreshToken token);

    Optional<RefreshToken> buscarTokenPorHuella(String huella);

    /**
     * Invalida de golpe todos los tokens de una familia. Es la respuesta al escenario 4:
     * detectada una copia, se cae la sesion entera y hay que volver a autenticarse.
     */
    void invalidarFamilia(UUID familiaId);
}
