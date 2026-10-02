package pe.ayni.bank.identity.infrastructure.out.correo;

/** Envio de un correo ya compuesto. La implementacion de produccion usa Amazon SES. */
public interface EnviadorDeCorreo {

    void enviar(String destinatario, String asunto, String html, String texto);
}
