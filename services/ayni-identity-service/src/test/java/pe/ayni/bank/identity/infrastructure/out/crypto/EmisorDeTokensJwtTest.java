package pe.ayni.bank.identity.infrastructure.out.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.text.ParseException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.ayni.bank.identity.domain.model.Celular;
import pe.ayni.bank.identity.domain.model.Consentimiento;
import pe.ayni.bank.identity.domain.model.ContrasenaCifrada;
import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.model.Usuario;

/** Emisión y firma de los tokens de sesión · HU-04. */
class EmisorDeTokensJwtTest {

    private static final Instant AHORA = Instant.parse("2026-08-30T10:15:30Z");
    private static final byte[] CLAVE = "ayni-firma-de-pruebas-32-bytes!!".getBytes();

    private final EmisorDeTokensJwt emisor =
            new EmisorDeTokensJwt(Base64.getEncoder().encodeToString(CLAVE));

    private final Usuario ana = Usuario.registrar(
            UUID.randomUUID(),
            new CorreoElectronico("ana.quispe@example.pe"),
            new Celular("987654321"),
            new ContrasenaCifrada("$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$loquesea"),
            Consentimiento.otorgar(true, AHORA, "v1"),
            AHORA);

    private SignedJWT emitirYLeer() throws ParseException {
        return SignedJWT.parse(emisor.emitir(ana, AHORA, AHORA.plusSeconds(900)));
    }

    @Test
    @DisplayName("el token va firmado y la firma se verifica con la misma clave")
    void firmaVerificable() throws ParseException, JOSEException {
        assertThat(emitirYLeer().verify(new MACVerifier(CLAVE))).isTrue();
    }

    @Test
    @DisplayName("un token firmado con otra clave no se acepta")
    void noSeAceptaOtraFirma() throws ParseException, JOSEException {
        byte[] otraClave = "otra-clave-distinta-de-32-bytes!".getBytes();

        assertThat(emitirYLeer().verify(new MACVerifier(otraClave))).isFalse();
    }

    @Test
    void llevaElUsuarioSuEstadoYLaCaducidad() throws ParseException {
        SignedJWT jwt = emitirYLeer();

        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(ana.id().toString());
        assertThat(jwt.getJWTClaimsSet().getStringClaim("estado"))
                .isEqualTo("PENDIENTE_VERIFICACION");
        assertThat(jwt.getJWTClaimsSet().getExpirationTime().toInstant())
                .isEqualTo(AHORA.plusSeconds(900));
        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("ayni-identity-service");
    }

    @Test
    @DisplayName("no lleva ni correo ni celular: un JWT va firmado, no cifrado")
    void noLlevaDatosPersonales() throws ParseException {
        // Cualquiera que intercepte el token lee su contenido. Lo que sobre ahí es una fuga.
        String serializado = emisor.emitir(ana, AHORA, AHORA.plusSeconds(900));

        assertThat(emitirYLeer().getJWTClaimsSet().getClaims())
                .doesNotContainKeys("correo", "celular", "documento");
        assertThat(new String(Base64.getUrlDecoder().decode(serializado.split("\\.")[1])))
                .doesNotContain("ana.quispe")
                .doesNotContain("987654321");
    }

    @Test
    @DisplayName("cada token lleva su identificador, para poder revocarlo uno a uno")
    void cadaTokenEsUnico() throws ParseException {
        String uno = SignedJWT.parse(emisor.emitir(ana, AHORA, AHORA.plusSeconds(900)))
                .getJWTClaimsSet().getJWTID();
        String otro = SignedJWT.parse(emisor.emitir(ana, AHORA, AHORA.plusSeconds(900)))
                .getJWTClaimsSet().getJWTID();

        assertThat(uno).isNotEqualTo(otro);
    }

    @Test
    @DisplayName("el token de renovación es aleatorio y su huella no lo revela")
    void tokenDeRenovacion() {
        var uno = emisor.generarTokenDeRenovacion();
        var otro = emisor.generarTokenDeRenovacion();

        assertThat(uno.enClaro()).isNotEqualTo(otro.enClaro());
        assertThat(uno.huella()).isNotEqualTo(uno.enClaro());
        assertThat(uno.huella()).isEqualTo(emisor.huellaDe(uno.enClaro()));
        // Sin relleno ni caracteres que haya que escapar: acaba en una cookie.
        assertThat(uno.enClaro()).doesNotContain("=").doesNotContain("+").doesNotContain("/");
    }

    @Test
    @DisplayName("la misma entrada da siempre la misma huella")
    void laHuellaEsEstable() {
        assertThat(emisor.huellaDe("token-de-prueba"))
                .isEqualTo(emisor.huellaDe("token-de-prueba"))
                .isNotEqualTo(emisor.huellaDe("token-de-prueba-distinto"));
    }

    @Test
    @DisplayName("una clave corta impide arrancar, no falla en el primer ingreso")
    void exigeUnaClaveDe256Bits() {
        String corta = Base64.getEncoder().encodeToString("demasiado-corta".getBytes());

        assertThatThrownBy(() -> new EmisorDeTokensJwt(corta))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void elTokenDeRenovacionNoSeImprime() {
        assertThat(emisor.generarTokenDeRenovacion().toString()).contains("oculto");
    }
}
