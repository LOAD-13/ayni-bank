package pe.ayni.bank.core.infrastructure.out.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import pe.ayni.bank.core.domain.model.Pan;

/**
 * Cifra el PAN en reposo con AES-256-GCM y calcula su huella · HU-09.
 *
 * <p><strong>GCM y un IV aleatorio por cifrado</strong>, igual que el cifrado de datos
 * personales de identity: el criptograma se autentica (alterar un byte hace fallar el
 * descifrado) y dos tarjetas con el mismo PAN no producen el mismo texto. Por eso la columna
 * cifrada no sirve para imponer unicidad, y para eso esta la <strong>huella</strong>: un
 * HMAC-SHA256 del PAN, determinista, que no permite recuperarlo.
 *
 * <p><strong>Una clave maestra, tres claves derivadas.</strong> De {@code ayni.cifrado.clave}
 * se derivan, con HMAC y una etiqueta distinta, la de cifrado, la de la huella y la del CVV.
 * Usar la misma clave para cifrar y para firmar es un error clasico; asi cada uso tiene la
 * suya sin anadir secretos que custodiar.
 */
@Component
public class CifradorDePan {

    private static final String ESQUEMA = "v1:";
    private static final int LONGITUD_IV = 12;
    private static final int LONGITUD_ETIQUETA_BITS = 128;

    private final SecretKeySpec claveDeCifrado;
    private final SecretKeySpec claveDeHuella;
    private final byte[] claveDeCvv;
    private final SecureRandom aleatorio = new SecureRandom();

    public CifradorDePan(@Value("${ayni.cifrado.clave}") String claveMaestraBase64) {
        byte[] maestra = Base64.getDecoder().decode(claveMaestraBase64);
        if (maestra.length != 32) {
            throw new IllegalStateException("La clave de cifrado debe tener 32 bytes (256 bits) en Base64.");
        }
        this.claveDeCifrado = new SecretKeySpec(derivar(maestra, "ayni/tarjeta/pan"), "AES");
        this.claveDeHuella = new SecretKeySpec(derivar(maestra, "ayni/tarjeta/huella"), "HmacSHA256");
        this.claveDeCvv = derivar(maestra, "ayni/tarjeta/cvv");
    }

    public String cifrar(Pan pan) {
        try {
            byte[] iv = new byte[LONGITUD_IV];
            aleatorio.nextBytes(iv);
            Cipher cifrador = Cipher.getInstance("AES/GCM/NoPadding");
            cifrador.init(Cipher.ENCRYPT_MODE, claveDeCifrado, new GCMParameterSpec(LONGITUD_ETIQUETA_BITS, iv));
            byte[] criptograma = cifrador.doFinal(pan.valor().getBytes(StandardCharsets.UTF_8));
            byte[] juntos = new byte[iv.length + criptograma.length];
            System.arraycopy(iv, 0, juntos, 0, iv.length);
            System.arraycopy(criptograma, 0, juntos, iv.length, criptograma.length);
            return ESQUEMA + Base64.getEncoder().encodeToString(juntos);
        } catch (GeneralSecurityException e) {
            // Sin el PAN en el mensaje: esta excepcion acaba en un log.
            throw new IllegalStateException("No se pudo cifrar el numero de la tarjeta.", e);
        }
    }

    public Pan descifrar(String criptograma) {
        if (criptograma == null || !criptograma.startsWith(ESQUEMA)) {
            throw new IllegalStateException("Criptograma de tarjeta con un esquema desconocido.");
        }
        try {
            byte[] juntos = Base64.getDecoder().decode(criptograma.substring(ESQUEMA.length()));
            Cipher descifrador = Cipher.getInstance("AES/GCM/NoPadding");
            descifrador.init(Cipher.DECRYPT_MODE, claveDeCifrado,
                    new GCMParameterSpec(LONGITUD_ETIQUETA_BITS, juntos, 0, LONGITUD_IV));
            byte[] enClaro = descifrador.doFinal(juntos, LONGITUD_IV, juntos.length - LONGITUD_IV);
            return new Pan(new String(enClaro, StandardCharsets.UTF_8));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("No se pudo descifrar el numero de la tarjeta.", e);
        }
    }

    /** HMAC-SHA256 del PAN en hexadecimal: determinista, sirve para la unicidad. */
    public String huella(Pan pan) {
        return HexFormat.of().formatHex(hmac(claveDeHuella, pan.valor()));
    }

    /** La clave derivada para el CVV dinamico; la usa CalculadoraDeCvv. */
    public byte[] claveDeCvv() {
        return claveDeCvv.clone();
    }

    private static byte[] derivar(byte[] maestra, String proposito) {
        return hmac(new SecretKeySpec(maestra, "HmacSHA256"), proposito);
    }

    private static byte[] hmac(SecretKeySpec clave, String datos) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(clave);
            return mac.doFinal(datos.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 no disponible en esta JVM.", e);
        }
    }
}
