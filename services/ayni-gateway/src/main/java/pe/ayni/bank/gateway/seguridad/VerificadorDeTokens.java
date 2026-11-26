package pe.ayni.bank.gateway.seguridad;

import java.text.ParseException;
import java.time.Clock;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    /**
     * Lo que el cliente podia hacer con un token emitido antes de que existieran los roles
     * (AYNI-281): todos eran clientes. Se aplica solo a los tokens sin el claim {@code permisos}, que
     * duran quince minutos, para que un despliegue no cierre la sesion de nadie. Un token nuevo
     * siempre lleva el claim, aunque vaya vacio.
     */
    static final Set<String> PERMISOS_DE_UN_TOKEN_ANTIGUO = Set.of(
            "cuenta:leer", "transferencia:iniciar", "tarjeta:administrar", "tarjeta:revelar_pan");

    /** Quien es y que puede hacer, tal como lo acredita un token valido. */
    public record Identidad(String usuario, Set<String> permisos) {
    }

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
        return identidadDe(token).map(Identidad::usuario);
    }

    /** El usuario y sus permisos si el token es valido; vacio en cualquier otro caso. */
    public Optional<Identidad> identidadDe(String token) {
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
            List<String> permisos = declaraciones.getStringListClaim("permisos");
            return Optional.of(new Identidad(sujeto,
                    permisos == null ? PERMISOS_DE_UN_TOKEN_ANTIGUO : Set.copyOf(permisos)));
        } catch (ParseException | JOSEException e) {
            return Optional.empty();
        }
    }
}
