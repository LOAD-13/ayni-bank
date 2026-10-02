package pe.ayni.bank.identity.infrastructure.in.web;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonInclude;

import pe.ayni.bank.identity.domain.model.DatosDelDni;
import pe.ayni.bank.identity.domain.model.EstadoDelPasoKyc;
import pe.ayni.bank.identity.domain.model.ResultadoDeExtraccion;

/**
 * Lo que se muestra al titular para que confirme sus datos.
 *
 * <p>El numero va enmascarado: esta respuesta viaja a cualquiera que conozca el identificador
 * de la solicitud (el endpoint aun no exige sesion, ver T-12). Si el titular dice que el
 * numero esta mal, lo escribe entero; si no, se conserva el leido.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResultadoDeExtraccionDto(String estado, DatosLeidosDto datos, Integer intentosRestantes) {

    static ResultadoDeExtraccionDto desde(ResultadoDeExtraccion resultado) {
        DatosLeidosDto datos = resultado.lectura() == null
                ? null
                : DatosLeidosDto.desde(resultado.lectura().datos(), resultado.lectura().confiable());
        Integer restantes = resultado.estado() == EstadoDelPasoKyc.RECHAZADO ? resultado.intentosRestantes() : null;
        return new ResultadoDeExtraccionDto(resultado.estado().name(), datos, restantes);
    }

    /** @param confiable si vienen del MRZ con sus digitos verificadores validos */
    public record DatosLeidosDto(String numeroEnmascarado, String nombres, String apellidos,
                                 LocalDate fechaNacimiento, String sexo, LocalDate fechaEmision,
                                 boolean confiable) {

        static DatosLeidosDto desde(DatosDelDni datos, boolean confiable) {
            return new DatosLeidosDto(datos.numeroEnmascarado(), datos.nombres(), datos.apellidos(),
                    datos.fechaNacimiento(), datos.sexo(), datos.fechaEmision(), confiable);
        }

        @Override
        public String toString() {
            return "DatosLeidosDto[oculto]";
        }
    }
}
