package pe.ayni.bank.identity.domain.model;

/**
 * El enlace de recuperacion no sirve: no existe, ya se uso, caduco o lo anulo uno mas nuevo.
 *
 * <p>Los cuatro casos dan el mismo mensaje a proposito. Distinguirlos le diria a quien
 * prueba enlaces al azar cuales llegaron a existir, y al titular no le aporta nada: en
 * todos tiene que hacer lo mismo, pedir uno nuevo.
 */
public class EnlaceDeRecuperacionInvalidoException extends RuntimeException {

    public EnlaceDeRecuperacionInvalidoException() {
        super("El enlace ya no es valido. Pide uno nuevo para cambiar tu contrasena.");
    }
}
