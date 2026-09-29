package pe.ayni.bank.identity.infrastructure.in.web;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import pe.ayni.bank.identity.domain.model.MetodoDeSegundoFactor;
import pe.ayni.bank.identity.domain.port.in.SeleccionarSegundoFactorUseCase;

/** Endpoint para la elección y alta del método de segundo factor (HU-22 / AYNI-127). */
@RestController
@RequestMapping("/api/v1/usuarios/{usuarioId}/segundo-factor/metodo")
public class MetodoSegundoFactorController {

    private final SeleccionarSegundoFactorUseCase seleccionarSegundoFactor;

    public MetodoSegundoFactorController(SeleccionarSegundoFactorUseCase seleccionarSegundoFactor) {
        this.seleccionarSegundoFactor = seleccionarSegundoFactor;
    }

    @PostMapping
    public ResponseEntity<MetodoSegundoFactorDto> seleccionarMetodo(
            @PathVariable UUID usuarioId,
            @Valid @RequestBody SolicitudSeleccionMetodoDto cuerpo) {

        MetodoDeSegundoFactor metodo = seleccionarSegundoFactor.seleccionarMetodo(usuarioId, cuerpo.tipo());
        return ResponseEntity.ok(MetodoSegundoFactorDto.desde(metodo));
    }
}
