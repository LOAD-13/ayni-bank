package pe.ayni.bank.core.infrastructure.out.seguridad;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;

import pe.ayni.bank.core.domain.model.ConfirmacionInvalidaException;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.OperacionAConfirmar;

/** HU-07 · ADR-0031: el token de confirmacion se acepta solo si cumple todo. */
class VerificadorDeConfirmacionJwtTest {

    private static final Instant AHORA = Instant.parse("2026-10-11T15:00:00Z");
    private static final byte[] CLAVE = "clave-de-confirmacion-para-test0".getBytes();
    private static final byte[] OTRA_CLAVE = "otra-clave-que-no-es-la-correcta".getBytes();

    private final UUID usuario = UUID.randomUUID();
    private final OperacionAConfirmar operacion =
            new OperacionAConfirmar("00111000000001", Dinero.de("200.00", Moneda.PEN), UUID.randomUUID());
    private final VerificadorDeConfirmacionJwt verificador = new VerificadorDeConfirmacionJwt(
            Base64.getEncoder().encodeToString(CLAVE), Clock.fixed(AHORA, ZoneOffset.UTC));

    private JWTClaimsSet.Builder validas() {
        return new JWTClaimsSet.Builder()
                .issuer("ayni-identity-service")
                .audience("ayni-core-banking")
                .subject(usuario.toString())
                .claim("tipo", "confirmacion")
                .claim("op", operacion.huella())
                .expirationTime(Date.from(AHORA.plusSeconds(300)));
    }

    private String firmado(UnaryOperator<JWTClaimsSet.Builder> cambio, byte[] clave) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), cambio.apply(validas()).build());
        jwt.sign(new MACSigner(clave));
        return jwt.serialize();
    }

    private void rechaza(String token) {
        assertThatThrownBy(() -> verificador.verificar(token, usuario, operacion))
                .isInstanceOf(ConfirmacionInvalidaException.class);
    }

    @Test
    @DisplayName("acepta un token valido de este usuario para esta operacion")
    void valido() throws Exception {
        String token = firmado(b -> b, CLAVE);
        assertThatCode(() -> verificador.verificar(token, usuario, operacion)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rechaza la falta de token y lo que no es un JWT")
    void ausente() {
        rechaza(null);
        rechaza(" ");
        rechaza("esto-no-es-un-jwt");
    }

    @Test
    @DisplayName("rechaza un token firmado con otra clave, como la del token de acceso")
    void otraClave() throws Exception {
        rechaza(firmado(b -> b, OTRA_CLAVE));
    }

    @Test
    @DisplayName("rechaza un token sin firmar (alg none)")
    void sinFirma() {
        rechaza(new PlainJWT(validas().build()).serialize());
    }

    @Test
    @DisplayName("rechaza un token caducado")
    void caducado() throws Exception {
        rechaza(firmado(b -> b.expirationTime(Date.from(AHORA)), CLAVE));
        rechaza(firmado(b -> b.expirationTime(null), CLAVE));
    }

    @Test
    @DisplayName("rechaza un token de otro usuario")
    void otroUsuario() throws Exception {
        rechaza(firmado(b -> b.subject(UUID.randomUUID().toString()), CLAVE));
    }

    @Test
    @DisplayName("rechaza un token que confirma otra operacion")
    void otraOperacion() throws Exception {
        rechaza(firmado(b -> b.claim("op", "otra-huella"), CLAVE));
    }

    @Test
    @DisplayName("rechaza un token de otro emisor, otra audiencia u otro tipo")
    void emisorAudienciaTipo() throws Exception {
        rechaza(firmado(b -> b.issuer("otro"), CLAVE));
        rechaza(firmado(b -> b.audience("ayni-gateway"), CLAVE));
        rechaza(firmado(b -> b.audience((String) null), CLAVE));
        rechaza(firmado(b -> b.claim("tipo", "acceso"), CLAVE));
    }

    @Test
    @DisplayName("no arranca con una clave de menos de 32 bytes")
    void claveCorta() {
        String corta = Base64.getEncoder().encodeToString("corta".getBytes());
        Clock reloj = Clock.systemUTC();
        assertThatThrownBy(() -> new VerificadorDeConfirmacionJwt(corta, reloj))
                .isInstanceOf(IllegalStateException.class);
    }
}
