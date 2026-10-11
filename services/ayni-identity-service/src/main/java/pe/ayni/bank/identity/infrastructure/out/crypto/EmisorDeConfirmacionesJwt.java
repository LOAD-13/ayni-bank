package pe.ayni.bank.identity.infrastructure.out.crypto;

import java.util.Base64;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import pe.ayni.bank.identity.domain.model.ConfirmacionDeOperacion;
import pe.ayni.bank.identity.domain.port.out.EmisorDeConfirmacionesPort;

/**
 * Firma el token de confirmacion de una operacion (HU-07, ADR-0031).
 *
 * <p>Usa una clave <strong>distinta</strong> de la del token de acceso: asi un token de
 * acceso no sirve como confirmacion ni al reves, y rotar una no obliga a rotar la otra. La
 * audiencia es core-banking, el unico que lo acepta.
 */
@Component
public class EmisorDeConfirmacionesJwt implements EmisorDeConfirmacionesPort {

    static final String EMISOR = "ayni-identity-service";
    static final String AUDIENCIA = "ayni-core-banking";

    private final byte[] clave;

    public EmisorDeConfirmacionesJwt(@Value("${ayni.confirmacion.clave}") String claveBase64) {
        byte[] bytes = Base64.getDecoder().decode(claveBase64);
        if (bytes.length < 32) {
            throw new IllegalStateException("La clave de confirmacion debe tener al menos 32 bytes en Base64.");
        }
        this.clave = bytes;
    }

    @Override
    public String emitir(ConfirmacionDeOperacion confirmacion) {
        try {
            JWTClaimsSet declaraciones = new JWTClaimsSet.Builder()
                    .issuer(EMISOR)
                    .audience(AUDIENCIA)
                    .subject(confirmacion.usuarioId().toString())
                    .claim("tipo", "confirmacion")
                    .claim("op", confirmacion.huella())
                    .issueTime(Date.from(confirmacion.usadaEn() != null ? confirmacion.usadaEn() : confirmacion.creadaEn()))
                    .expirationTime(Date.from(confirmacion.expiraEn()))
                    .jwtID(confirmacion.id().toString())
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), declaraciones);
            jwt.sign(new MACSigner(clave));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("No se pudo firmar el token de confirmacion.", e);
        }
    }
}
