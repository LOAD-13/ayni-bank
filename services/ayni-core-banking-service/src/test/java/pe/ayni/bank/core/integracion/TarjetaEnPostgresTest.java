package pe.ayni.bank.core.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pe.ayni.bank.core.domain.model.Pan;
import pe.ayni.bank.core.domain.model.Tarjeta;
import pe.ayni.bank.core.infrastructure.out.crypto.CifradorDePan;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorRepositorioDeTarjetas;

/**
 * HU-09 contra un PostgreSQL de verdad, con la migracion V10: el PAN nunca llega en claro a la
 * base, es unico aunque el criptograma no lo sea, y la tarjeta nace con sus controles.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AdaptadorRepositorioDeTarjetas.class, CifradorDePan.class})
@TestPropertySource(properties = "ayni.cifrado.clave=YXluaS1jbGF2ZS1kZS1kZXNhcnJvbGxvLTMyYnl0ZXM=")
class TarjetaEnPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    /** Cuenta tecnica de fondeo que siembra V3: una cuenta valida para la clave foranea. */
    private static final UUID CUENTA = UUID.fromString("f0de0000-0000-4000-8000-000000000001");
    private static final Pan PAN = new Pan("4999990000000007");

    @Autowired
    private AdaptadorRepositorioDeTarjetas tarjetas;
    @Autowired
    private CifradorDePan cifrador;
    @Autowired
    private JdbcTemplate jdbc;

    private Tarjeta emitida(Pan pan) {
        Tarjeta tarjeta = Tarjeta.emitir(CUENTA, UUID.randomUUID(), pan, Instant.parse("2026-10-28T15:00:00Z"));
        tarjetas.guardar(tarjeta);
        return tarjeta;
    }

    @Test
    @DisplayName("El PAN se guarda cifrado con AES-256-GCM y se recupera intacto")
    void cifradoEnReposo() {
        Tarjeta tarjeta = emitida(PAN);

        String enLaBase = jdbc.queryForObject("SELECT pan_cifrado FROM core.tarjeta WHERE id = ?",
                String.class, tarjeta.id());
        assertThat(enLaBase).startsWith("v1:").doesNotContain(PAN.valor());
        assertThat(jdbc.queryForObject("SELECT ultimos4 FROM core.tarjeta WHERE id = ?", String.class, tarjeta.id()))
                .isEqualTo("0007");
        assertThat(tarjetas.deTitular(tarjeta.usuarioId())).hasValueSatisfying(leida -> {
            assertThat(leida.pan()).isEqualTo(PAN);
            assertThat(leida.venceEn()).isEqualTo(tarjeta.venceEn());
        });
    }

    @Test
    @DisplayName("El mismo PAN cifrado dos veces da dos criptogramas: la unicidad la impone la huella")
    void unicidadPorHuella() {
        assertThat(cifrador.cifrar(PAN)).isNotEqualTo(cifrador.cifrar(PAN));
        Pan otro = new Pan("4539578763621486");
        emitida(otro);

        assertThat(tarjetas.existePan(otro)).isTrue();
        assertThat(tarjetas.existePan(new Pan("4111111111111111"))).isFalse();
        assertThatThrownBy(() -> emitida(otro)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("La tarjeta nace con sus controles: limite diario de S/ 500.00 y compras por internet")
    void controlesPorDefecto() {
        Tarjeta tarjeta = emitida(new Pan("4716108999716531"));

        BigDecimal limite = jdbc.queryForObject("SELECT limite_diario FROM core.control_tarjeta WHERE tarjeta_id = ?",
                BigDecimal.class, tarjeta.id());
        Boolean internet = jdbc.queryForObject(
                "SELECT compras_por_internet FROM core.control_tarjeta WHERE tarjeta_id = ?", Boolean.class, tarjeta.id());
        assertThat(limite).isEqualByComparingTo("500.00");
        assertThat(internet).isTrue();
    }

    @Test
    @DisplayName("Un criptograma alterado o de otro esquema no se descifra: GCM detecta la manipulacion")
    void manipulacionDetectada() {
        String criptograma = cifrador.cifrar(PAN);
        // Se altera un caracter del centro: sus 6 bits son datos. Junto al relleno «=» algunos bits
        // no se usan, y cambiarlos dejaba el criptograma intacto en ~6 % de las ejecuciones.
        int medio = criptograma.length() / 2;
        char original = criptograma.charAt(medio);
        String alterado = criptograma.substring(0, medio) + (original == 'A' ? 'g' : 'A')
                + criptograma.substring(medio + 1);

        assertThatThrownBy(() -> cifrador.descifrar(alterado)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cifrador.descifrar("v0:abc")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CifradorDePan("Y29ydGE=")).isInstanceOf(IllegalStateException.class);
        assertThat(cifrador.claveDeCvv()).hasSize(32);
    }
}
