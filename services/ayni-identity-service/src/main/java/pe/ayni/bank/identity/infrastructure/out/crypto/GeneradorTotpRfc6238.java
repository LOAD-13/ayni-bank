package pe.ayni.bank.identity.infrastructure.out.crypto;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import pe.ayni.bank.identity.domain.model.CodigoTotp;
import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.model.SecretoTotp;
import pe.ayni.bank.identity.domain.port.out.GeneradorDeTotpPort;

/**
 * TOTP segun RFC 6238, escrito a mano sobre {@code javax.crypto}.
 *
 * <p><strong>Por que sin biblioteca.</strong> El algoritmo entero cabe en este fichero: un
 * HMAC-SHA1 sobre el numero de intervalo, un truncado dinamico y un modulo. Traer una
 * dependencia para esto significaria anadir al arbol —y a los informes de Trivy— codigo de
 * terceros que hay que mantener al dia, a cambio de ahorrar cuarenta lineas que ademas
 * conviene tener a la vista, porque son las que deciden si alguien entra o no.
 *
 * <p><strong>Por que SHA-1.</strong> Es lo que fija la RFC 6238 por defecto y lo unico que
 * aceptan sin configurar Google Authenticator, Authy y el resto. Las debilidades conocidas
 * de SHA-1 son de colision, y aqui no se usa como firma sino dentro de un HMAC con una
 * clave secreta, donde no aplican. Cambiarlo a SHA-256 dejaria los codigos ilegibles para
 * la mayoria de aplicaciones.
 */
@Component
public class GeneradorTotpRfc6238 implements GeneradorDeTotpPort {

    /** Duracion de cada codigo. La pantalla aprobada muestra la cuenta atras sobre esto. */
    private static final long SEGUNDOS_POR_INTERVALO = 30;

    /**
     * Ventanas adicionales que se aceptan hacia atras y hacia delante.
     *
     * <p>Una sola. Entre que alguien lee el codigo y lo teclea pasan segundos, y los relojes
     * de los moviles no van sincronizados al milisegundo. Sin tolerancia, un codigo correcto
     * falla por poco y el usuario no entiende nada; con demasiada, el codigo deja de ser
     * temporal. Uno es lo que recomienda la propia RFC.
     */
    private static final int VENTANA = 1;

    private static final String ALGORITMO_HMAC = "HmacSHA1";
    private static final String ALFABETO_BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final String EMISOR = "Ayni Bank";

    private final SecureRandom aleatorio = new SecureRandom();

    @Override
    public SecretoTotp generarSecreto() {
        StringBuilder secreto = new StringBuilder(SecretoTotp.longitud());
        for (int i = 0; i < SecretoTotp.longitud(); i++) {
            secreto.append(ALFABETO_BASE32.charAt(aleatorio.nextInt(ALFABETO_BASE32.length())));
        }
        return new SecretoTotp(secreto.toString());
    }

    @Override
    public boolean verificar(SecretoTotp secreto, CodigoTotp codigo, Instant momento) {
        long intervalo = momento.getEpochSecond() / SEGUNDOS_POR_INTERVALO;
        byte[] clave = decodificarBase32(secreto.valor());

        for (int desplazamiento = -VENTANA; desplazamiento <= VENTANA; desplazamiento++) {
            String esperado = calcular(clave, intervalo + desplazamiento);
            // Comparacion en tiempo constante: comparar con equals() revela por donde
            // empezo a diferir el codigo, y con suficientes intentos eso se puede medir.
            if (MessageDigest.isEqual(esperado.getBytes(StandardCharsets.US_ASCII),
                    codigo.valor().getBytes(StandardCharsets.US_ASCII))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String uriDeAprovisionamiento(SecretoTotp secreto, CorreoElectronico correo) {
        String etiqueta = URLEncoder.encode(EMISOR + ":" + correo.valor(),
                StandardCharsets.UTF_8);
        String emisor = URLEncoder.encode(EMISOR, StandardCharsets.UTF_8);

        return "otpauth://totp/" + etiqueta
                + "?secret=" + secreto.valor()
                + "&issuer=" + emisor
                + "&algorithm=SHA1"
                + "&digits=" + CodigoTotp.DIGITOS
                + "&period=" + SEGUNDOS_POR_INTERVALO;
    }

    /** El nucleo de la RFC 6238: HMAC del intervalo, truncado dinamico y modulo. */
    private String calcular(byte[] clave, long intervalo) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO_HMAC);
            mac.init(new SecretKeySpec(clave, ALGORITMO_HMAC));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(intervalo).array());

            // Truncado dinamico: los cuatro bits bajos del ultimo byte dicen por donde
            // empezar a leer. Que el punto de partida dependa del propio hash es lo que
            // impide que siempre se usen los mismos bits.
            int desplazamiento = hash[hash.length - 1] & 0x0F;
            int binario = ((hash[desplazamiento] & 0x7F) << 24)
                    | ((hash[desplazamiento + 1] & 0xFF) << 16)
                    | ((hash[desplazamiento + 2] & 0xFF) << 8)
                    | (hash[desplazamiento + 3] & 0xFF);

            int modulo = (int) Math.pow(10, CodigoTotp.DIGITOS);
            return String.format("%0" + CodigoTotp.DIGITOS + "d", binario % modulo);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo calcular el codigo TOTP.", e);
        }
    }

    /**
     * Base32 sin relleno, segun RFC 4648.
     *
     * <p>Se acumulan los bits de cinco en cinco y se van sacando de ocho en ocho. Los bits
     * sobrantes del final se descartan: son relleno, no datos.
     */
    private static byte[] decodificarBase32(String texto) {
        byte[] salida = new byte[texto.length() * 5 / 8];
        int acumulador = 0;
        int bits = 0;
        int escritos = 0;

        for (char caracter : texto.toCharArray()) {
            int valor = ALFABETO_BASE32.indexOf(caracter);
            if (valor < 0) {
                throw new IllegalStateException("El secreto TOTP no es Base32 valido.");
            }
            acumulador = (acumulador << 5) | valor;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                salida[escritos++] = (byte) (acumulador >> bits);
            }
        }
        return salida;
    }
}
