package pe.ayni.bank.notification.domain.model;

/** Un correo con los valores ya puestos, en texto plano. El HTML lo arma el adaptador. */
public record MensajeRedactado(String asunto, String texto) {
}
