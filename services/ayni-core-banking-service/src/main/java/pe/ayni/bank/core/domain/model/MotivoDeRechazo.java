package pe.ayni.bank.core.domain.model;

/**
 * Por que se rechaza una operacion monetaria.
 *
 * <p>Cada motivo lleva el texto que vera el cliente. Vive en el dominio porque las reglas
 * son del dominio; el adaptador web solo decide el codigo HTTP.
 */
public enum MotivoDeRechazo {

    SALDO_INSUFICIENTE("Saldo insuficiente",
            "El importe supera el saldo disponible de tu cuenta."),
    MISMA_CUENTA("Cuenta de destino no valida",
            "No puedes transferirte a tu misma cuenta."),
    CUENTA_DESTINO_INEXISTENTE("Cuenta de destino no encontrada",
            "Revisa el numero: no corresponde a ninguna cuenta Ayni activa."),
    CUENTA_NO_OPERATIVA("Cuenta no operativa",
            "Una de las cuentas no admite movimientos en este momento."),
    MONEDA_DISTINTA("Monedas distintas",
            "Las dos cuentas deben estar en la misma moneda."),
    IMPORTE_FUERA_DE_LIMITE("Importe fuera de limite",
            "El importe esta fuera de los limites permitidos para esta operacion."),
    SIN_CUENTA("Todavia no tienes cuenta",
            "Tu cuenta aun no esta abierta.");

    private final String titulo;
    private final String detalle;

    MotivoDeRechazo(String titulo, String detalle) {
        this.titulo = titulo;
        this.detalle = detalle;
    }

    public String titulo() {
        return titulo;
    }

    public String detalle() {
        return detalle;
    }
}
