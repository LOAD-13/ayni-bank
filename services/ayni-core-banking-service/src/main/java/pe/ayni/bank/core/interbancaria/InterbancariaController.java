
package pe.ayni.bank.core.interbancaria;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

import pe.ayni.bank.core.application.interbancaria.ValidadorCci;

@RestController
@RequestMapping("/api/v1/interbancarias")
public class InterbancariaController {

    private final TransferenciaInterbancariaService service;

    public InterbancariaController(
        TransferenciaInterbancariaService service
    ) {
        this.service = service;
    }

    public record Solicitud(
        UUID usuarioId,
        UUID clave,
        String cci,
        BigDecimal importe,
        String concepto
    ) {}

    public record NuevoBeneficiario(
        UUID usuarioId,
        String alias,
        String cci
    ) {}

    @GetMapping("/validar-cci/{cci}")
    public boolean validar(@PathVariable String cci) {
        return ValidadorCci.esValido(cci);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TransferenciaInterbancariaService.Resultado iniciar(
        @RequestBody Solicitud solicitud
    ) {

        var resultado = service.iniciar(
            new TransferenciaInterbancariaService.Solicitud(
                solicitud.usuarioId(),
                solicitud.clave(),
                solicitud.cci(),
                solicitud.importe(),
                solicitud.concepto()
            )
        );

        service.despachar(resultado.id());

        return service.consultar(
            resultado.id(),
            solicitud.usuarioId()
        );
    }

    @GetMapping("/{id}")
    public TransferenciaInterbancariaService.Resultado consultar(
        @PathVariable UUID id,
        @RequestParam UUID usuarioId
    ) {
        return service.consultar(id, usuarioId);
    }

    @GetMapping("/beneficiarios")
    public List<TransferenciaInterbancariaService.Beneficiario>
        beneficiarios(@RequestParam UUID usuarioId) {

        return service.beneficiarios(usuarioId);
    }

    @PostMapping("/beneficiarios")
    public TransferenciaInterbancariaService.Beneficiario
        crearBeneficiario(@RequestBody NuevoBeneficiario s) {

        return service.guardarBeneficiario(
            s.usuarioId(),
            s.alias(),
            s.cci()
        );
    }

    @DeleteMapping("/beneficiarios/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrarBeneficiario(
        @PathVariable UUID id,
        @RequestParam UUID usuarioId
    ) {
        service.borrarBeneficiario(usuarioId, id);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail error(
        IllegalArgumentException ex
    ) {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            ex.getMessage()
        );
    }
}
