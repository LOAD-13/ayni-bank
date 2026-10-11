package pe.ayni.bank.notification.infrastructure.out.correo;

import org.springframework.web.util.HtmlUtils;

import pe.ayni.bank.notification.domain.model.MensajeRedactado;
import pe.ayni.bank.notification.domain.model.Titular;

/**
 * La version HTML y la de texto de un aviso, con el mismo aspecto que los correos de identity.
 * Todo lo variable se escapa antes de entrar en el HTML.
 */
final class CorreoHtml {

    private static final String PIE = "Ayni Bank · Banca 100 % digital. Proyecto academico (UTP).";
    private static final String AVISO =
            "Si no reconoces esta operacion, ingresa a tu banca y escribenos. Nunca te pediremos claves por correo.";

    private CorreoHtml() {
    }

    static String saludo(Titular titular) {
        return titular.nombrePila().isEmpty() ? "Hola," : "Hola " + titular.nombrePila() + ",";
    }

    static String texto(Titular titular, MensajeRedactado mensaje) {
        return saludo(titular) + "\n\n" + mensaje.texto() + "\n\n" + AVISO + "\n\n" + PIE;
    }

    static String html(Titular titular, MensajeRedactado mensaje) {
        return "<div style=\"font-family:Arial,sans-serif;max-width:520px;margin:auto;color:#1f2933\">"
                + "<p style=\"font-size:20px;font-weight:700;color:#064475\">AYNI Bank</p>"
                + "<h2 style=\"color:#0b2a4a\">" + HtmlUtils.htmlEscape(mensaje.asunto()) + "</h2>"
                + "<p>" + HtmlUtils.htmlEscape(saludo(titular)) + "</p>"
                + "<p style=\"line-height:1.6\">" + HtmlUtils.htmlEscape(mensaje.texto()) + "</p>"
                + "<p style=\"font-size:13px;color:#4b5563\">" + HtmlUtils.htmlEscape(AVISO) + "</p>"
                + "<p style=\"font-size:12px;color:#6b7785;margin-top:32px\">" + HtmlUtils.htmlEscape(PIE)
                + "</p></div>";
    }
}
