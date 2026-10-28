
package pe.ayni.bank.core.interbancaria;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/simulador/camara/v1/transferencias")
public class CamaraSimuladaController {

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
            defaultValue = "COMPLETADA"
        ) String escenario
    ) {

        if (envio == null || envio.id() == null
            || envio.cci() == null
            || !envio.cci().matches("[0-9]{20}")) {

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
            estado = EstadoInterbancario.valueOf(escenario);
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
}
