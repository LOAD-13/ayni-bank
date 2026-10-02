package pe.ayni.bank.identity.domain.model;

import java.util.Objects;

/**
 * El resultado del OCR: los datos, de donde salieron y si se pueden dar por buenos.
 *
 * <p>{@code confiable} solo es cierto si vienen del MRZ con todos sus digitos verificadores
 * validos (ADR-0015, ADR-0016).
 */
public record LecturaDelDni(DatosDelDni datos, FuenteDeLectura fuente, boolean confiable) {

    public LecturaDelDni {
        Objects.requireNonNull(datos, "Faltan los datos leidos.");
        Objects.requireNonNull(fuente, "Falta la fuente de la lectura.");
    }
}
