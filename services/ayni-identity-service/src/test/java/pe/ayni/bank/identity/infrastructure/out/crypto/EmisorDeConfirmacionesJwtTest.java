package pe.ayni.bank.identity.infrastructure.out.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import pe.ayni.bank.identity.domain.model.ConfirmacionDeOperacion;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;

/** El token que emite identity tiene exactamente lo que core-banking comprueba (ADR-0031). */
class EmisorDeConfirmacionesJwtTest {

    private static final byte[] CLAVE = "clave-de-confirmacion-para-test0".getBytes();
    private static final Instant AHORA = Instant.parse("2026-10-11T15:00:00Z");

    @Test
    @DisplayName("firma HS256 con emisor, audiencia, tipo, sujeto, huella, caducidad e id")
    void contenido() throws Exception {
        UUID usuario = UUID.randomUUID();
        ConfirmacionDeOperacion c = ConfirmacionDeOperacion
                .abrir(UUID.randomUUID(), usuario, "huella-de-la-op", TipoDeSegundoFactor.APP_AUTENTICADORA, null, AHORA)
                .usar(AHORA.plusSeconds(10));

        SignedJWT jwt = SignedJWT.parse(new EmisorDeConfirmacionesJwt(Base64.getEncoder().encodeToString(CLAVE)).emitir(c));
        JWTClaimsSet d = jwt.getJWTClaimsSet();

        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.HS256);
        assertThat(jwt.verify(new MACVerifier(CLAVE))).isTrue();
        assertThat(d.getIssuer()).isEqualTo("ayni-identity-service");
        assertThat(d.getAudience()).containsExactly("ayni-core-banking");
        assertThat(d.getStringClaim("tipo")).isEqualTo("confirmacion");
        assertThat(d.getSubject()).isEqualTo(usuario.toString());
        assertThat(d.getStringClaim("op")).isEqualTo("huella-de-la-op");
        assertThat(d.getExpirationTime().toInstant()).isEqualTo(c.expiraEn());
        assertThat(d.getJWTID()).isEqualTo(c.id().toString());
    }

    @Test
    @DisplayName("no arranca con una clave de menos de 32 bytes")
    void claveCorta() {
        String corta = Base64.getEncoder().encodeToString("corta".getBytes());
        assertThatThrownBy(() -> new EmisorDeConfirmacionesJwt(corta)).isInstanceOf(IllegalStateException.class);
    }
}
