package pe.ayni.bank.core.infrastructure.out.seguridad;

import java.text.ParseException;
import java.time.Clock;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import pe.ayni.bank.core.domain.model.ConfirmacionInvalidaException;
import pe.ayni.bank.core.domain.model.OperacionAConfirmar;
import pe.ayni.bank.core.domain.port.out.VerificadorDeConfirmacionPort;

/**
 * Verifica el token de confirmacion que emite identity tras comprobar el segundo factor
 * (HU-07, ADR-0031).
 *
 * <p>Se exige todo, y en este orden: que exista, que sea HS256 firmado con la clave de
 * confirmacion (no la del token de acceso), que lo emita identity para core, que sea de tipo
 * confirmacion, que no haya caducado, que sea del mismo usuario que hace la peticion y que
 * confirme exactamente esta operacion. Cualquier fallo da la misma excepcion.
 */
@Component
public class VerificadorDeConfirmacionJwt implements VerificadorDeConfirmacionPort {

    static final String EMISOR = "ayni-identity-service";
    static final String AUDIENCIA = "ayni-core-banking";
    static final String TIPO = "confirmacion";

    private static final Logger log = LoggerFactory.getLogger(VerificadorDeConfirmacionJwt.class);

    private final MACVerifier verificador;
    private final Clock reloj;

    public VerificadorDeConfirmacionJwt(@Value("${ayni.confirmacion.clave}") String claveBase64, Clock reloj) {
        byte[] clave = Base64.getDecoder().decode(claveBase64);
        if (clave.length < 32) {
            throw new IllegalStateException("La clave de confirmacion debe tener al menos 32 bytes en Base64.");
        }
        try {
            this.verificador = new MACVerifier(clave);
        } catch (JOSEException e) {
            throw new IllegalStateException("Clave de confirmacion invalida.", e);
        }
        this.reloj = reloj;
    }

    @Override
    public void verificar(String confirmacion, UUID usuarioId, OperacionAConfirmar operacion) {
        if (confirmacion == null || confirmacion.isBlank()) {
            throw new ConfirmacionInvalidaException();
        }
        try {
            SignedJWT jwt = SignedJWT.parse(confirmacion);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(verificador)) {
                throw rechazo("firma");
            }
            JWTClaimsSet d = jwt.getJWTClaimsSet();
            Date expira = d.getExpirationTime();
            if (!EMISOR.equals(d.getIssuer()) || d.getAudience() == null || !d.getAudience().contains(AUDIENCIA)
                    || !TIPO.equals(d.getStringClaim("tipo"))) {
                throw rechazo("emisor/audiencia/tipo");
            }
            if (expira == null || !expira.toInstant().isAfter(reloj.instant())) {
                throw rechazo("caducada");
            }
            if (!usuarioId.toString().equals(d.getSubject())) {
                throw rechazo("otro usuario");
            }
            if (!operacion.huella().equals(d.getStringClaim("op"))) {
                throw rechazo("otra operacion");
            }
        } catch (ParseException | JOSEException e) {
            throw rechazo("no es un JWT");
        }
    }

    /** El motivo va al log para diagnosticar; al cliente siempre llega el mismo mensaje. */
    private static ConfirmacionInvalidaException rechazo(String motivo) {
        log.info("Confirmacion de transferencia rechazada. motivo={}", motivo);
        return new ConfirmacionInvalidaException();
    }
}
