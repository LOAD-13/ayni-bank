package pe.ayni.bank.core.infrastructure.reclamaciones;

import java.math.BigDecimal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.ayni.bank.core.infrastructure.reclamaciones.LibroDeReclamaciones.Bien;
import pe.ayni.bank.core.infrastructure.reclamaciones.LibroDeReclamaciones.Constancia;
import pe.ayni.bank.core.infrastructure.reclamaciones.LibroDeReclamaciones.Hoja;
import pe.ayni.bank.core.infrastructure.reclamaciones.LibroDeReclamaciones.Tipo;

/**
 * Libro de reclamaciones virtual. Es publico a proposito: cualquier consumidor puede reclamar,
 * sea o no cliente, asi que no lee la cabecera del titular. El gateway le pone un limite de
 * tasa propio. No hay consulta por codigo: devolveria datos personales a quien adivine uno.
 */
@RestController
@RequestMapping("/api/v1/reclamaciones")
public class LibroDeReclamacionesController {

    private final LibroDeReclamaciones libro;

    public LibroDeReclamacionesController(LibroDeReclamaciones libro) {
        this.libro = libro;
    }

    public record Solicitud(
            @NotNull Tipo tipo,
            @NotBlank @Size(max = 120) String nombre,
            @NotBlank @Pattern(regexp = "DNI|CE") String tipoDocumento,
            @NotBlank @Pattern(regexp = "\\d{8,12}", message = "Documento no valido") String numeroDocumento,
            @NotBlank @Email @Size(max = 160) String correo,
            @Pattern(regexp = "(\\d{9})?", message = "Celular no valido") String telefono,
            @NotNull Bien bien,
            @NotBlank @Size(max = 140) String descripcionDelBien,
            @Pattern(regexp = "(\\d{1,9}(\\.\\d{1,2})?)?", message = "Importe no valido") String montoReclamado,
            @NotBlank @Size(min = 20, max = 2000) String detalle,
            @NotBlank @Size(max = 1000) String pedido) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Constancia registrar(@Valid @RequestBody Solicitud s) {
        BigDecimal monto = s.montoReclamado() == null || s.montoReclamado().isBlank()
                ? null : new BigDecimal(s.montoReclamado());
        String telefono = s.telefono() == null || s.telefono().isBlank() ? null : s.telefono();
        return libro.registrar(new Hoja(s.tipo(), s.nombre(), s.tipoDocumento(), s.numeroDocumento(),
                s.correo(), telefono, s.bien(), s.descripcionDelBien(), monto, s.detalle(), s.pedido()));
    }
}
