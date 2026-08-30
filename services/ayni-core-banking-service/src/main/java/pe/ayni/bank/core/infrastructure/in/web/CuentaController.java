package pe.ayni.bank.core.infrastructure.in.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.port.out.RepositorioDeCuentasPort;

/**
 * Consulta de la cuenta de ahorro. Alimenta la pantalla final del onboarding, la que
 * muestra el CCI recien creado.
 *
 * <p><strong>Toma el titular de la ruta y no del token, y eso es provisional.</strong> La
 * validacion del JWT vive en el gateway y llega con HU-07; hasta entonces este endpoint
 * confia en quien le pregunta, cosa que solo es aceptable porque el servicio no esta
 * publicado fuera de la red interna del compose. Queda anotado aqui y no escondido.
 */
@RestController
@RequestMapping("/api/v1/cuentas")
public class CuentaController {

    private final RepositorioDeCuentasPort cuentas;

    public CuentaController(RepositorioDeCuentasPort cuentas) {
        this.cuentas = cuentas;
    }

    @GetMapping("/titular/{usuarioId}")
    public CuentaDto deTitular(@PathVariable UUID usuarioId) {
        Cuenta cuenta = cuentas.buscarActivaDe(usuarioId, Moneda.PEN)
                .orElseThrow(CuentaTodaviaNoAbiertaException::new);

        return CuentaDto.desde(
                cuenta,
                cuenta.saldo(cuentas.asientosDe(cuenta.id())),
                cuentas.treaVigenteDe(cuenta.productoId()).orElse(null));
    }

    /**
     * La cuenta aun no existe: el evento sigue en la cola o el KYC no ha aprobado.
     *
     * <p>Es 404 y no un error: preguntar por una cuenta que todavia no se ha abierto es lo
     * normal durante los segundos que tarda el evento en recorrer el camino, y la pantalla
     * lo unico que tiene que hacer es volver a preguntar.
     */
    @ExceptionHandler(CuentaTodaviaNoAbiertaException.class)
    public ProblemDetail alNoExistirTodavia() {
        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problema.setTitle("La cuenta todavia no esta lista");
        problema.setDetail("Estamos terminando de abrirla. Vuelve a intentarlo en unos segundos.");
        return problema;
    }

    static class CuentaTodaviaNoAbiertaException extends RuntimeException {
        CuentaTodaviaNoAbiertaException() {
            super("La cuenta aun no existe.");
        }
    }
}
