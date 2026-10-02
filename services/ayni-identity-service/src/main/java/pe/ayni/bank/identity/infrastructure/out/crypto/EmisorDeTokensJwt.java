package pe.ayni.bank.identity.infrastructure.out.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import pe.ayni.bank.identity.domain.model.TokenDeRenovacion;
import pe.ayni.bank.identity.domain.model.Usuario;
import pe.ayni.bank.identity.domain.port.out.EmisorDeTokensDeAccesoPort;

/**
 * Token de acceso como JWT firmado con HS256, y tokens de renovacion aleatorios.
 *
 * <p><strong>Por que HS256 y no RS256.</strong> Hoy el unico que verifica la firma es el
 * gateway, y comparte despliegue y secretos con este servicio: una clave simetrica basta y
 * evita gestionar un par de claves y su rotacion. Cuando haya un tercero que deba verificar
 * sin poder firmar —una aplicacion movil de otro equipo, por ejemplo— habra que pasar a
 * RS256, y entonces cambia este adaptador y nada mas.
 *
 * <p><strong>Que lleva dentro y que no.</strong> El identificador del usuario, su estado y
 * poco mas. Ni el correo, ni el celular, ni el documento: un JWT no va cifrado, solo
 * firmado, y cualquiera que lo intercepte lee su contenido. Lo que sobra ahi es una fuga.
 *
 * <p>El estado si viaja, y es deliberado: el panel necesita saber si mostrar «verificacion
 * pendiente» sin tener que preguntar por cada carga de pagina. No es un permiso —quien
 * decide si se puede operar es el servicio, no el token—, es informacion de presentacion.
 */
@Component
public class EmisorDeTokensJwt implements EmisorDeTokensDeAccesoPort {

    /** Bytes de aleatoriedad del token de renovacion. 256 bits no se adivinan. */
    private static final int BYTES_DEL_TOKEN = 32;

    private static final String EMISOR = "ayni-identity-service";

    private final byte[] clave;
    private final SecureRandom aleatorio = new SecureRandom();

    public EmisorDeTokensJwt(@Value("${ayni.jwt.clave}") String claveBase64) {
        byte[] bytes = Base64.getDecoder().decode(claveBase64);
        // HS256 exige al menos 256 bits. Nimbus lo comprobaria al firmar, es decir en el
        // primer ingreso; comprobarlo aqui hace que el servicio no arranque siquiera.
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "La clave de firma del JWT debe tener al menos 32 bytes en Base64.");
        }
        this.clave = bytes;
    }

    @Override
    public String emitir(Usuario usuario, Instant momento, Instant expiraEn) {
        try {
            JWTClaimsSet declaraciones = new JWTClaimsSet.Builder()
                    .issuer(EMISOR)
                    .subject(usuario.id().toString())
                    .claim("estado", usuario.estado().name())
                    .issueTime(Date.from(momento))
                    .expirationTime(Date.from(expiraEn))
                    // Identificador unico del token. Permite revocarlo uno a uno cuando
                    // haga falta, sin tener que invalidar la clave de firma entera.
                    .jwtID(java.util.UUID.randomUUID().toString())
                    .build();

            SignedJWT jwt = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.HS256), declaraciones);
            jwt.sign(new MACSigner(clave));

            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("No se pudo firmar el token de acceso.", e);
        }
    }

    @Override
    public TokenDeRenovacion generarTokenDeRenovacion() {
        byte[] bytes = new byte[BYTES_DEL_TOKEN];
        aleatorio.nextBytes(bytes);
        // Sin relleno y con alfabeto seguro para URL: acaba en una cookie.
        String enClaro = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        return new TokenDeRenovacion(enClaro, huellaDe(enClaro));
    }

    /**
     * Huella SHA-256 del token.
     *
     * <p>Basta SHA-256 y no hace falta Argon2id: a diferencia de una contrasena, esto es un
     * valor aleatorio de 256 bits. No hay diccionario que lo adivine, asi que encarecer el
     * calculo solo serviria para hacer lenta cada renovacion.
     */
    @Override
    public String huellaDe(String tokenEnClaro) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256")
                    .digest(tokenEnClaro.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(resumen);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM.", e);
        }
    }
}
