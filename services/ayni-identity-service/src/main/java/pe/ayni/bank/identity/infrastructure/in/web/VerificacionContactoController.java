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
import pe.ayni.bank.identity.domain.model.SolicitudVerificacionContacto;
import pe.ayni.bank.identity.domain.port.in.GenerarDesafioCodigoUseCase;
import pe.ayni.bank.identity.domain.port.in.VerificarContactoRegistroUseCase;

/** Endpoint REST para reenvío y verificación de códigos de contacto en el registro (HU-22 / AYNI-131). */
@RestController
@RequestMapping("/api/v1/registro")
public class VerificacionContactoController {

    private final GenerarDesafioCodigoUseCase generarDesafioUseCase;
    private final VerificarContactoRegistroUseCase verificarContactoUseCase;

    public VerificacionContactoController(GenerarDesafioCodigoUseCase generarDesafioUseCase,
                                          VerificarContactoRegistroUseCase verificarContactoUseCase) {
        this.generarDesafioUseCase = generarDesafioUseCase;
        this.verificarContactoUseCase = verificarContactoUseCase;
    }

    @PostMapping("/{usuarioId}/codigo/reenviar")
    public ResponseEntity<DesafioCodigoDto> reenviarCodigo(
            @PathVariable UUID usuarioId,
            @Valid @RequestBody SolicitudReenvioCodigoDto cuerpo) {

        ResultadoGeneracionDesafio resultado = generarDesafioUseCase.generar(usuarioId, cuerpo.tipoFactor());
        return ResponseEntity.ok(DesafioCodigoDto.desde(resultado.desafio()));
    }

    @PostMapping("/{usuarioId}/codigo/verificar")
    public ResponseEntity<Void> verificarCodigo(
            @PathVariable UUID usuarioId,
            @Valid @RequestBody SolicitudVerificarCodigoRegistroDto cuerpo) {

        verificarContactoUseCase.verificarContacto(new SolicitudVerificacionContacto(
                usuarioId, cuerpo.tipoFactor(), cuerpo.codigo()));
        return ResponseEntity.ok().build();
    }
}
