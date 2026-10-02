package pe.ayni.bank.identity.infrastructure.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import pe.ayni.bank.identity.domain.model.ComandoDeRegistro;
import pe.ayni.bank.identity.domain.model.ResultadoDeRegistro;
import pe.ayni.bank.identity.domain.port.in.RegistrarVisitanteUseCase;

/** HU-01 · Registro de usuario. Implementa {@code contracts/identity-service.openapi.yaml}. */
@RestController
@RequestMapping("/api/v1/registro")
public class RegistroController {

    private final RegistrarVisitanteUseCase registrarVisitante;

    public RegistroController(RegistrarVisitanteUseCase registrarVisitante) {
        this.registrarVisitante = registrarVisitante;
    }

    /**
     * 202 y no 201 porque el alta no termina aqui: el usuario existe pero no puede operar
     * hasta superar la verificacion de identidad. Un 201 afirmaria que el recurso esta
     * completo y listo, y no lo esta.
     */
    @PostMapping
    public ResponseEntity<RespuestaDeRegistroDto> registrar(
            @Valid @RequestBody SolicitudDeRegistroDto solicitud) {

        ResultadoDeRegistro resultado = registrarVisitante.registrar(new ComandoDeRegistro(
                solicitud.nombres(),
                solicitud.apellidos(),
                solicitud.tipoDocumento(),
                solicitud.numeroDocumento(),
                solicitud.fechaNacimiento(),
                solicitud.correo(),
                solicitud.celular(),
                solicitud.contrasena(),
                solicitud.aceptaTerminos()));

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(RespuestaDeRegistroDto.desde(resultado));
    }
}
