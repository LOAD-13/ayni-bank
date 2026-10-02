package pe.ayni.bank.identity.domain.model;

import java.util.Objects;

/**
 * Documento de identidad declarado por la persona al registrarse.
 *
 * <p><strong>Declarado, no verificado.</strong> Este numero es el que alguien escribio en
 * un formulario: no prueba nada todavia. HU-02 lee el documento fisico con OCR y contrasta
 * lo extraido con lo que hay aqui. Si no coinciden, la solicitud se deriva a revision
 * manual. Esa doble lectura es justamente el motivo de pedir el dato dos veces.
 *
 * <p>El numero es dato personal sensible segun la Ley N.o 29733: se guarda cifrado y
 * {@link #toString()} nunca lo devuelve. Para mostrarlo o para trazarlo existen
 * {@link #ultimos4()} y {@link #enmascarado()}.
 */
public record DocumentoDeIdentidad(TipoDocumento tipo, String numero) {

    public DocumentoDeIdentidad {
        Objects.requireNonNull(tipo, "El tipo de documento es obligatorio.");
        if (numero == null || numero.isBlank()) {
            throw new IllegalArgumentException("El numero de documento es obligatorio.");
        }

        // Se quitan espacios y guiones antes de validar. Quien copia su documento de otro
        // sitio lo trae a menudo con separadores, y rechazarlo por eso es hostil sin
        // aportar seguridad: lo que se guarda es el numero limpio.
        numero = numero.replaceAll("[\\s-]", "");
        if (tipo.distingueMayusculas()) {
            numero = numero.toUpperCase();
        }

        if (!tipo.admite(numero)) {
            throw new IllegalArgumentException(tipo.mensajeDeError());
        }
    }

    /**
     * Los cuatro ultimos digitos, que son los que se guardan en claro para poder buscar y
     * mostrar sin descifrar el registro entero.
     */
    public String ultimos4() {
        return numero.substring(numero.length() - 4);
    }

    /** Apto para pantalla y para log: {@code ****5678}. */
    public String enmascarado() {
        return "*".repeat(numero.length() - 4) + ultimos4();
    }

    @Override
    public String toString() {
        return "DocumentoDeIdentidad[tipo=" + tipo + ", numero=" + enmascarado() + "]";
    }
}
