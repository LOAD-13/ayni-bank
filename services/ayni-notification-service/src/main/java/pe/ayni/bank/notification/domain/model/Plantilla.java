package pe.ayni.bank.notification.domain.model;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Una plantilla del catalogo, con marcadores entre llaves dobles: {@code {{importe}}}. */
public record Plantilla(String asunto, String cuerpo) {

    private static final Pattern MARCADOR = Pattern.compile("\\{\\{(\\w+)}}");

    /**
     * Pone los valores en su sitio.
     *
     * @throws IllegalArgumentException si falta algun valor: un correo con {{saldo}} a la
     *                                  vista es peor que un correo que no sale
     */
    public MensajeRedactado rellenar(Map<String, String> valores) {
        return new MensajeRedactado(sustituir(asunto, valores), sustituir(cuerpo, valores));
    }

    private static String sustituir(String texto, Map<String, String> valores) {
        Matcher m = MARCADOR.matcher(texto);
        StringBuilder salida = new StringBuilder();
        while (m.find()) {
            String valor = valores.get(m.group(1));
            if (valor == null) {
                throw new IllegalArgumentException("Falta el valor de " + m.group(1));
            }
            m.appendReplacement(salida, Matcher.quoteReplacement(valor));
        }
        m.appendTail(salida);
        return salida.toString();
    }
}
