package pe.ayni.bank.core.domain.model;

/**
 * Monedas que Ayni admite. Coincide con la restriccion {@code ck_cuenta_moneda} de la
 * migracion V2: anadir una aqui exige una migracion que amplie esa restriccion.
 */
public enum Moneda {

    PEN("S/"),
    USD("US$");

    private final String simbolo;

    Moneda(String simbolo) {
        this.simbolo = simbolo;
    }

    public String simbolo() {
        return simbolo;
    }
}
