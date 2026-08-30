package pe.ayni.bank.identity.infrastructure.in.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.ayni.bank.identity.domain.port.out.RepositorioDeSolicitudesPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeUsuariosPort;

/**
 * Datos minimos del titular para saludarle por su nombre.
 *
 * <p><strong>Devuelve el nombre de pila y el correo enmascarado, y nada mas.</strong> Ni
 * apellidos, ni documento, ni celular. La pantalla final del onboarding necesita decir
 * «Listo, Ana Lucia» y recordar a que direccion se envio la confirmacion; cualquier otro
 * dato que viajara por aqui seria informacion personal expuesta sin que nadie la necesite.
 *
 * <p>Toma el titular de la ruta y no del token, igual que el endpoint de cuentas y con la
 * misma cautela: la validacion del JWT vive en el gateway y llega con HU-07. Hasta entonces
 * esto solo es aceptable porque el servicio no esta publicado fuera de la red interna.
 */
@RestController
@RequestMapping("/api/v1/usuarios")
public class UsuarioController {

    private final RepositorioDeUsuariosPort usuarios;
    private final RepositorioDeSolicitudesPort solicitudes;

    public UsuarioController(RepositorioDeUsuariosPort usuarios,
                             RepositorioDeSolicitudesPort solicitudes) {
        this.usuarios = usuarios;
        this.solicitudes = solicitudes;
    }

    @GetMapping("/{usuarioId}/resumen")
    public ResumenDto resumen(@PathVariable UUID usuarioId) {
        var usuario = usuarios.buscarPorId(usuarioId)
                .orElseThrow(UsuarioDesconocidoException::new);

        return new ResumenDto(
                solicitudes.nombreDePilaDe(usuarioId).orElse(null),
                usuario.correo().enmascarado(),
                usuario.estado().name());
    }

    @ExceptionHandler(UsuarioDesconocidoException.class)
    public ProblemDetail alNoExistir() {
        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problema.setTitle("No encontramos ese usuario");
        return problema;
    }

    /**
     * @param nombreDePila nulo si la solicitud no llego a declarar identidad, que es el
     *                     caso de los senuelos. La pantalla saluda sin nombre y ya esta.
     * @param correo enmascarado: la pantalla solo tiene que recordar a donde se envio el
     *               aviso, y para eso basta {@code a**@ejemplo.pe}
     */
    public record ResumenDto(String nombreDePila, String correo, String estado) {
    }

    static class UsuarioDesconocidoException extends RuntimeException {
        UsuarioDesconocidoException() {
            super("El usuario no existe.");
        }
    }
}
