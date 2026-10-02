package pe.ayni.bank.identity.infrastructure.out.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import pe.ayni.bank.identity.domain.port.out.CifradorDeDatosPort;

/**
 * Adaptador de {@link CifradorDeDatosPort} sobre AES-256 en modo GCM.
 *
 * <p><strong>Por que GCM y no CBC.</strong> GCM es cifrado autenticado: junto al
 * criptograma produce una etiqueta que se verifica al descifrar. Si alguien con acceso a la
 * base de datos altera un byte del numero de documento, el descifrado falla en lugar de
 * devolver basura silenciosamente. CBC no detecta esa manipulacion, y en un sistema
 * financiero eso no es un detalle.
 *
 * <p><strong>El vector de inicializacion.</strong> Se genera uno aleatorio de doce bytes en
 * cada cifrado y viaja delante del criptograma. Reutilizar un IV con GCM no filtra un
 * mensaje: rompe el modo entero y permite recuperar la clave de autenticacion. Por eso
 * nunca es fijo ni deriva del dato.
 *
 * <p>La consecuencia practica es que dos personas con el mismo documento producen
 * criptogramas distintos, asi que <strong>la columna cifrada no sirve para buscar ni para
 * imponer unicidad</strong>. Para eso estan los ultimos cuatro digitos en claro y, cuando
 * HU-02 verifique la identidad, la comprobacion de duplicados sobre {@code persona}.
 *
 * <p>El prefijo {@code v1:} identifica el esquema. Cuando haya que rotar la clave o cambiar
 * el algoritmo, el criptograma dice con que se cifro y se puede descifrar lo antiguo
 * mientras se escribe lo nuevo, sin una migracion a ciegas.
 */
@Component
public class CifradorAes256Gcm implements CifradorDeDatosPort {

    private static final String ESQUEMA = "v1:";
    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int LONGITUD_IV = 12;
    private static final int LONGITUD_ETIQUETA_BITS = 128;
    private static final int LONGITUD_CLAVE_BYTES = 32;

    private final SecretKeySpec clave;
    private final SecureRandom aleatorio = new SecureRandom();

    public CifradorAes256Gcm(@Value("${ayni.cifrado.clave}") String claveBase64) {
        byte[] bytes = Base64.getDecoder().decode(claveBase64);
        if (bytes.length != LONGITUD_CLAVE_BYTES) {
            // Falla al arrancar y no al primer registro. Una clave corta produciria un
            // servicio que parece sano hasta que alguien intenta darse de alta.
            throw new IllegalStateException(
                    "La clave de cifrado debe tener 32 bytes (256 bits) en Base64.");
        }
        this.clave = new SecretKeySpec(bytes, "AES");
    }

    @Override
    public String cifrar(String enClaro) {
        if (enClaro == null) {
            return null;
        }
        try {
            byte[] iv = new byte[LONGITUD_IV];
            aleatorio.nextBytes(iv);

            Cipher cifrador = Cipher.getInstance(ALGORITMO);
            cifrador.init(Cipher.ENCRYPT_MODE, clave,
                    new GCMParameterSpec(LONGITUD_ETIQUETA_BITS, iv));
            byte[] criptograma = cifrador.doFinal(enClaro.getBytes(StandardCharsets.UTF_8));

            byte[] juntos = new byte[iv.length + criptograma.length];
            System.arraycopy(iv, 0, juntos, 0, iv.length);
            System.arraycopy(criptograma, 0, juntos, iv.length, criptograma.length);

            return ESQUEMA + Base64.getEncoder().encodeToString(juntos);
        } catch (GeneralSecurityException e) {
            // Sin el dato en el mensaje: esta excepcion acaba en un log.
            throw new IllegalStateException("No se pudo cifrar el dato personal.", e);
        }
    }

    @Override
    public String descifrar(String criptograma) {
        if (criptograma == null) {
            return null;
        }
        if (!criptograma.startsWith(ESQUEMA)) {
            throw new IllegalStateException("El criptograma no declara un esquema conocido.");
        }
        try {
            byte[] juntos = Base64.getDecoder().decode(criptograma.substring(ESQUEMA.length()));

            Cipher descifrador = Cipher.getInstance(ALGORITMO);
            descifrador.init(Cipher.DECRYPT_MODE, clave,
                    new GCMParameterSpec(LONGITUD_ETIQUETA_BITS, juntos, 0, LONGITUD_IV));

            byte[] claro = descifrador.doFinal(juntos, LONGITUD_IV, juntos.length - LONGITUD_IV);
            return new String(claro, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo descifrar el dato personal.", e);
        }
    }
}
