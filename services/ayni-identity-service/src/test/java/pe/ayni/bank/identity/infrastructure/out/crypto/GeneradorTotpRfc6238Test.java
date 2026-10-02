package pe.ayni.bank.identity.infrastructure.out.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.ayni.bank.identity.domain.model.CodigoTotp;
import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.model.SecretoTotp;

/** TOTP según RFC 6238. */
class GeneradorTotpRfc6238Test {

    private final GeneradorTotpRfc6238 generador = new GeneradorTotpRfc6238();

    /**
     * Vector de prueba del apéndice B de la RFC 6238: la clave ASCII «12345678901234567890»
     * en el instante 59 produce 94287082 con SHA-1, cuyos seis últimos dígitos son 287082.
     *
     * <p>Esa clave en Base32 son 32 caracteres, que es justo lo que exige {@link SecretoTotp}.
     * Comprobarlo contra el vector oficial es lo que distingue una implementación correcta
     * de uno que simplemente es consistente consigo mismo: sin esto, el código podría
     * generar números perfectamente estables que ninguna aplicación de autenticación
     * reconocería.
     */
    private static final SecretoTotp SECRETO_DE_LA_RFC =
            new SecretoTotp("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");

    @Test
    @DisplayName("reproduce el vector de prueba oficial de la RFC 6238")
    void coincideConElVectorDeLaRfc() {
        Instant momento = Instant.ofEpochSecond(59);

        assertThat(generador.verificar(SECRETO_DE_LA_RFC, new CodigoTotp("287082"), momento))
                .isTrue();
    }

    @Test
    void rechazaUnCodigoQueNoCorresponde() {
        assertThat(generador.verificar(SECRETO_DE_LA_RFC, new CodigoTotp("000000"),
                Instant.ofEpochSecond(59))).isFalse();
    }

    @Test
    @DisplayName("acepta la ventana anterior y la siguiente, no la de mas alla")
    void toleraUnaVentanaDeDesfase() {
        // Entre que alguien lee el código y lo teclea pasan segundos, y los relojes de los
        // móviles no van sincronizados. Sin tolerancia, un código correcto falla por poco.
        Instant momento = Instant.ofEpochSecond(59);

        assertThat(generador.verificar(SECRETO_DE_LA_RFC, new CodigoTotp("287082"),
                momento.plus(Duration.ofSeconds(30)))).isTrue();
        assertThat(generador.verificar(SECRETO_DE_LA_RFC, new CodigoTotp("287082"),
                momento.minus(Duration.ofSeconds(30)))).isTrue();
        assertThat(generador.verificar(SECRETO_DE_LA_RFC, new CodigoTotp("287082"),
                momento.plus(Duration.ofSeconds(120)))).isFalse();
    }

    @Test
    void generaSecretosBase32DeLaLongitudCorrecta() {
        SecretoTotp secreto = generador.generarSecreto();

        assertThat(secreto.valor())
                .hasSize(SecretoTotp.longitud())
                .matches("^[A-Z2-7]+$");
    }

    @Test
    void dosSecretosSeguidosNoSeParecen() {
        assertThat(generador.generarSecreto()).isNotEqualTo(generador.generarSecreto());
    }

    @Test
    @DisplayName("el URI de aprovisionamiento es el que entienden las aplicaciones")
    void construyeElUriDeAprovisionamiento() {
        String uri = generador.uriDeAprovisionamiento(
                SECRETO_DE_LA_RFC, new CorreoElectronico("ana.quispe@example.pe"));

        assertThat(uri)
                .startsWith("otpauth://totp/")
                .contains("secret=" + SECRETO_DE_LA_RFC.valor())
                .contains("issuer=Ayni+Bank")
                .contains("algorithm=SHA1")
                .contains("digits=6")
                .contains("period=30");
    }

    @Test
    @DisplayName("un codigo generado con un secreto no vale con otro")
    void losSecretosNoSonIntercambiables() {
        SecretoTotp otro = generador.generarSecreto();
        Instant momento = Instant.ofEpochSecond(59);

        assertThat(generador.verificar(otro, new CodigoTotp("287082"), momento)).isFalse();
    }
}
