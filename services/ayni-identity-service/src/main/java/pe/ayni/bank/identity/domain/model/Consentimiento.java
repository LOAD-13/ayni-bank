package pe.ayni.bank.identity.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Consentimiento explicito al tratamiento de datos personales, con el momento en que se
 * otorgo.
 *
 * <p>La Ley N.o 29733 no se conforma con que el usuario haya aceptado: exige poder
 * <strong>demostrar</strong> que acepto, cuando y para que. Un booleano suelto en la tabla
 * de usuario no prueba nada ante una reclamacion; una marca temporal si.
 *
 * <p>No existe forma de construir un consentimiento no otorgado. Un objeto que represente
 * «no acepto» no tiene sentido: si no acepta, no hay registro, y por tanto no hay nada que
 * guardar.
 */
public record Consentimiento(Instant otorgadoEn, String versionDeLosTerminos) {

    public Consentimiento {
        Objects.requireNonNull(otorgadoEn, "El momento del consentimiento es obligatorio.");
        Objects.requireNonNull(versionDeLosTerminos, "La version de los terminos es obligatoria.");
        if (versionDeLosTerminos.isBlank()) {
            throw new IllegalArgumentException("La version de los terminos no puede estar vacia.");
        }
    }

    /**
     * @param aceptado lo que marco la persona en el formulario
     * @throws ConsentimientoNoOtorgadoException si no acepto; el registro no continua
     */
    public static Consentimiento otorgar(boolean aceptado, Instant momento, String version) {
        if (!aceptado) {
            throw new ConsentimientoNoOtorgadoException();
        }
        return new Consentimiento(momento, version);
    }
}
