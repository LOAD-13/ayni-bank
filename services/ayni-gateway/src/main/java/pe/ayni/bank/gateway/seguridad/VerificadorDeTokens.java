package pe.ayni.bank.gateway.seguridad;

import java.text.ParseException;
import java.time.Clock;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Comprueba el token de acceso que emite identity-service y devuelve a quien pertenece.
 *
 * <p>Rechaza todo lo que no sea exactamente lo esperado: algoritmo distinto de HS256
 * (incluido {@code none}, el ataque clasico de degradacion), firma invalida, emisor
 * distinto, token caducado o sin sujeto. No lanza excepciones hacia fuera: un token malo
 * no es un error del sistema, es una peticion que no pasa.
 */
public class VerificadorDeTokens {

    static final String EMISOR = "ayni-identity-service";

    private final MACVerifier verificador;
    private final Clock reloj;

    public VerificadorDeTokens(String claveBase64, Clock reloj) {
        byte[] clave = Base64.getDecoder().decode(claveBase64);
        if (clave.length < 32) {
            throw new IllegalStateException(
                    "La clave del JWT debe tener al menos 32 bytes en Base64.");
        }
        try {
            this.verificador = new MACVerifier(clave);
        } catch (JOSEException e) {
            throw new IllegalStateException("Clave de verificacion del JWT invalida.", e);
        }
        this.reloj = reloj;
    }

    /** El identificador del usuario si el token es valido; vacio en cualquier otro caso. */
    public Optional<String> usuarioDe(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())
                    || !jwt.verify(verificador)) {
                return Optional.empty();
            }
            JWTClaimsSet declaraciones = jwt.getJWTClaimsSet();
            Date expira = declaraciones.getExpirationTime();
            boolean vigente = expira != null && expira.toInstant().isAfter(reloj.instant());
            boolean nuestro = EMISOR.equals(declaraciones.getIssuer());
            String sujeto = declaraciones.getSubject();

            if (!vigente || !nuestro || sujeto == null || sujeto.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(sujeto);
        } catch (ParseException | JOSEException e) {
            return Optional.empty();
        }
    }
}
