package pe.ayni.bank.identity.infrastructure.out.correo;

import org.springframework.web.util.HtmlUtils;

/**
 * Las plantillas de los correos al cliente: asunto, version HTML y version de texto.
 *
 * <p>Cada una recibe un solo dato variable —un enlace o un codigo—, que se escapa siempre
 * antes de entrar en el HTML. Ningun correo pide contrasenas ni codigos de vuelta: es lo
 * primero que se le ensena al cliente para que reconozca un correo falso.
 */
public enum PlantillaDeCorreo {

    BIENVENIDA("Continua la apertura de tu cuenta Ayni",
            "Ya casi esta. Para abrir tu cuenta solo falta verificar tu identidad con tu DNI y una selfie.",
            "Continuar con la verificacion", true),
    INTENTO_DE_REGISTRO("Alguien intento registrarse con tu correo",
            "Se intento abrir una cuenta Ayni con este correo, que ya tiene una. Si fuiste tu, solo ingresa. "
                    + "Si no, no tienes que hacer nada: tu cuenta sigue protegida.",
            "Ir a mi banca", true),
    CODIGO_DE_VERIFICACION("Tu codigo de verificacion Ayni",
            "Usa este codigo para continuar. Vence en 10 minutos. Nadie de Ayni Bank te lo pedira por telefono, "
                    + "correo ni mensaje.",
            null, false),
    INGRESO_PAUSADO("Pausamos el ingreso a tu cuenta",
            "Detectamos varios intentos fallidos y pausamos el ingreso unos minutos para protegerte.",
            "Ir a mi banca", true),
    SESION_CERRADA("Cerramos tu sesion por seguridad",
            "Detectamos el uso de una sesion que ya no era valida y cerramos todas por seguridad. "
                    + "Vuelve a ingresar con tu contrasena y tu segundo factor.",
            "Volver a ingresar", true),
    REVISION_MANUAL("Estamos revisando tu identidad",
            "Tu verificacion de identidad paso a revision manual. Te avisaremos en cuanto termine.",
            "Ir a Ayni Bank", true);

    private final String asunto;
    private final String cuerpo;
    private final String textoDelBoton;
    private final boolean datoEsEnlace;

    PlantillaDeCorreo(String asunto, String cuerpo, String textoDelBoton, boolean datoEsEnlace) {
        this.asunto = asunto;
        this.cuerpo = cuerpo;
        this.textoDelBoton = textoDelBoton;
        this.datoEsEnlace = datoEsEnlace;
    }

    public String asunto() {
        return asunto;
    }

    public String texto(String dato) {
        return cuerpo + "\n\n" + dato + "\n\nAyni Bank · Banca 100 % digital. Proyecto academico (UTP).";
    }

    public String html(String dato) {
        String seguro = HtmlUtils.htmlEscape(dato);
        String accion = datoEsEnlace
                ? "<a href=\"" + seguro + "\" style=\"display:inline-block;background:#064475;color:#ffffff;"
                    + "padding:12px 24px;border-radius:999px;text-decoration:none;font-weight:600\">"
                    + HtmlUtils.htmlEscape(textoDelBoton) + "</a>"
                : "<p style=\"font-size:32px;letter-spacing:8px;font-weight:700;color:#064475;margin:8px 0\">"
                    + seguro + "</p>";
        return "<div style=\"font-family:Arial,sans-serif;max-width:520px;margin:auto;color:#1f2933\">"
                + "<p style=\"font-size:20px;font-weight:700;color:#064475\">AYNI Bank</p>"
                + "<h2 style=\"color:#0b2a4a\">" + HtmlUtils.htmlEscape(asunto) + "</h2>"
                + "<p style=\"line-height:1.6\">" + HtmlUtils.htmlEscape(cuerpo) + "</p>"
                + accion
                + "<p style=\"font-size:12px;color:#6b7785;margin-top:32px\">Ayni Bank · Banca 100 % digital. "
                + "Proyecto academico de la Universidad Tecnologica del Peru.</p></div>";
    }
}
