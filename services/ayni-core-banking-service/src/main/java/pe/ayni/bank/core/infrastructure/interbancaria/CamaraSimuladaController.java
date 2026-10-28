
package pe.ayni.bank.core.infrastructure.interbancaria;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Simulador de la camara de compensacion interbancaria (HU-11), conforme a
 * {@code contracts/camara-compensacion.openapi.yaml}. Hace el papel del sistema externo en el
 * entorno academico; el gateway no lo publica, solo lo llama {@link CamaraHttpAdapter}.
 *
 * <p>La respuesta depende del codigo de entidad del CCI, como las tarjetas de prueba de una
 * pasarela de pagos: {@value #ENTIDAD_QUE_RECHAZA} rechaza, {@value #ENTIDAD_QUE_NO_RESPONDE}
 * nunca decide (sirve para ver la reversion por plazo vencido) y cualquier otra acepta. La
 * cabecera {@code X-Escenario-Simulador} fuerza un resultado concreto en las pruebas.
 */
@RestController
@RequestMapping("/simulador/camara/v1/transferencias")
public class CamaraSimuladaController {

    static final String ENTIDAD_QUE_RECHAZA = "097";
    static final String ENTIDAD_QUE_NO_RESPONDE = "098";

    private final ConcurrentHashMap<UUID, EstadoInterbancario>
        estados = new ConcurrentHashMap<>();

    public record Envio(
        UUID id,
        String cci,
        String importe
    ) {}

    public record Respuesta(
        UUID id,
        EstadoInterbancario estado
    ) {}

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Respuesta enviar(
        @RequestBody Envio envio,
        @RequestHeader(
            value = "X-Escenario-Simulador",
            required = false
        ) String escenario
    ) {

        if (envio == null || envio.id() == null
            || envio.cci() == null
            || !envio.cci().matches("\\d{20}")) {

            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Identificador o CCI invalido"
            );
        }

        try {
            if (new BigDecimal(envio.importe())
                    .compareTo(BigDecimal.ZERO) <= 0) {
                throw new NumberFormatException();
            }
        } catch (NullPointerException | NumberFormatException ex) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Importe invalido"
            );
        }

        EstadoInterbancario estado;

        try {
            estado = escenario == null
                ? porEntidad(envio.cci())
                : EstadoInterbancario.valueOf(escenario);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Escenario no reconocido"
            );
        }

        EstadoInterbancario almacenado =
            estados.computeIfAbsent(
                envio.id(),
                ignored -> estado
            );

        return new Respuesta(envio.id(), almacenado);
    }

    @GetMapping("/{id}")
    public Respuesta consultar(@PathVariable UUID id) {

        EstadoInterbancario estado = estados.get(id);

        if (estado == null) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Transferencia no encontrada"
            );
        }

        return new Respuesta(id, estado);
    }

    private static EstadoInterbancario porEntidad(String cci) {
        if (cci.startsWith(ENTIDAD_QUE_RECHAZA)) {
            return EstadoInterbancario.RECHAZADA;
        }
        return cci.startsWith(ENTIDAD_QUE_NO_RESPONDE)
            ? EstadoInterbancario.PENDIENTE
            : EstadoInterbancario.COMPLETADA;
    }
}
