package pe.ayni.bank.identity.application.usecase;

import java.util.Optional;
import java.util.UUID;

import pe.ayni.bank.identity.domain.port.out.RepositorioDeSolicitudesPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeUsuariosPort;

/**
 * Quién está detrás del identificador que llega durante el registro (AYNI-321).
 *
 * <p>En el registro, el cliente conoce el identificador de su solicitud y no el de su usuario: si
 * la solicitud tiene titular, ese es el usuario real. Si no lo tiene y tampoco existe un usuario
 * con ese identificador, es una solicitud <b>señuelo</b> (ADR-0008, antienumeración: el correo ya
 * estaba registrado) y quien llama debe responder algo efímero e indistinguible en vez de fallar.
 *
 * <p>Esta resolución estaba copiada en {@link GenerarDesafioCodigoService} y en
 * {@link SeleccionarSegundoFactorService}; vive aquí una sola vez y con sus propias pruebas.
 *
 * @param usuarioId el usuario real (el titular de la solicitud, o el identificador recibido)
 * @param senuelo   {@code true} si no hay titular ni usuario: responder sin persistir nada
 */
record TitularDeLaSolicitud(UUID usuarioId, boolean senuelo) {

    /** Los repositorios pueden ser nulos: hay instalaciones del servicio que no los registran. */
    static TitularDeLaSolicitud de(UUID recibido, RepositorioDeSolicitudesPort solicitudes,
                                   RepositorioDeUsuariosPort usuarios) {
        Optional<UUID> titular = solicitudes != null ? solicitudes.titularDe(recibido) : Optional.empty();
        UUID real = titular.orElse(recibido);
        boolean senuelo = solicitudes != null && titular.isEmpty()
                && usuarios != null && usuarios.buscarPorId(real).isEmpty();
        return new TitularDeLaSolicitud(real, senuelo);
    }
}
