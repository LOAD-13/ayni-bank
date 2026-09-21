package pe.ayni.bank.identity.infrastructure.in.web;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import pe.ayni.bank.identity.domain.model.ResultadoGeneracionDesafio;
import pe.ayni.bank.identity.domain.port.in.GenerarDesafioCodigoUseCase;
import pe.ayni.bank.identity.domain.port.in.VerificarDesafioCodigoUseCase;

/** Endpoint REST para la generación y verificación de desafíos OTP (HU-22 / AYNI-128). */
@RestController
@RequestMapping("/api/v1/segundo-factor/desafio")
public class DesafioCodigoController {

    private final GenerarDesafioCodigoUseCase generarDesafioUseCase;
    private final VerificarDesafioCodigoUseCase verificarDesafioUseCase;

    public DesafioCodigoController(GenerarDesafioCodigoUseCase generarDesafioUseCase,
                                   VerificarDesafioCodigoUseCase verificarDesafioUseCase) {
        this.generarDesafioUseCase = generarDesafioUseCase;
        this.verificarDesafioUseCase = verificarDesafioUseCase;
    }

    @PostMapping("/usuario/{usuarioId}/generar")
    public ResponseEntity<DesafioCodigoDto> generarDesafio(
            @PathVariable UUID usuarioId,
            @Valid @RequestBody SolicitudGenerarDesafioDto cuerpo) {

        ResultadoGeneracionDesafio resultado = generarDesafioUseCase.generar(usuarioId, cuerpo.tipoFactor());
        return ResponseEntity.ok(DesafioCodigoDto.desde(resultado.desafio()));
    }

    @PostMapping("/{desafioId}/verificar")
    public ResponseEntity<Void> verificarDesafio(
            @PathVariable UUID desafioId,
            @Valid @RequestBody SolicitudVerificarDesafioDto cuerpo) {

        verificarDesafioUseCase.verificar(desafioId, cuerpo.codigo());
        return ResponseEntity.ok().build();
    }
}
