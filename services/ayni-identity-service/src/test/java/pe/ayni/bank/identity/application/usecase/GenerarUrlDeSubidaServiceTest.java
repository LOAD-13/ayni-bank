package pe.ayni.bank.identity.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import pe.ayni.bank.identity.domain.model.DatosDeclarados;
import pe.ayni.bank.identity.domain.model.IdentidadDeclarada;
import pe.ayni.bank.identity.domain.model.ObjetoAlmacenado;
import pe.ayni.bank.identity.domain.model.SolicitudNoExisteException;
import pe.ayni.bank.identity.domain.model.TipoDeDocumentoKyc;
import pe.ayni.bank.identity.domain.model.UrlDeSubida;
import pe.ayni.bank.identity.domain.port.out.AlmacenDeDocumentosPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSolicitudesPort;

/** AYNI-13 subtarea 7: genera la URL con la que el navegador sube un documento KYC. */
class GenerarUrlDeSubidaServiceTest {

    private SolicitudesFalsas solicitudes;
    private AlmacenFalso almacen;
    private GenerarUrlDeSubidaService servicio;
    private UUID solicitudId;

    @BeforeEach
    void prepararEscenario() {
        solicitudes = new SolicitudesFalsas();
        almacen = new AlmacenFalso();
        servicio = new GenerarUrlDeSubidaService(solicitudes, almacen);

        solicitudId = UUID.randomUUID();
        solicitudes.titulares.put(solicitudId, UUID.randomUUID());
    }

    @Test
    @DisplayName("pide al almacen una URL con una clave de objeto que incluye la solicitud y el tipo")
    void generaLaClaveDeObjetoEsperada() {
        UrlDeSubida resultado = servicio.generar(solicitudId, TipoDeDocumentoKyc.ANVERSO, "jpg");

        assertThat(resultado).isSameAs(almacen.urlDevuelta);
        assertThat(almacen.ultimaClaveDeObjeto)
                .startsWith("kyc/" + solicitudId + "/anverso-")
                .endsWith(".jpg");
        assertThat(almacen.ultimoTipoDeContenido).isEqualTo("image/jpeg");
        // El limite de 5 MB viaja a la politica de subida: lo impone el almacen, no el navegador.
        assertThat(almacen.ultimoTamanoMaximo).isEqualTo(5L * 1024 * 1024);
    }

    @Test
    @DisplayName("cada llamada genera una clave de objeto distinta, aunque sea el mismo tipo")
    void lasClavesDeObjetoNoSeRepiten() {
        servicio.generar(solicitudId, TipoDeDocumentoKyc.REVERSO, "png");
        String primeraClave = almacen.ultimaClaveDeObjeto;

        servicio.generar(solicitudId, TipoDeDocumentoKyc.REVERSO, "png");

        assertThat(almacen.ultimaClaveDeObjeto).isNotEqualTo(primeraClave);
    }

    @Test
    @DisplayName("una solicitud inexistente no genera ninguna URL")
    void rechazaUnaSolicitudInexistente() {
        assertThatThrownBy(() ->
                servicio.generar(UUID.randomUUID(), TipoDeDocumentoKyc.SELFIE, "jpg"))
                .isInstanceOf(SolicitudNoExisteException.class);

        assertThat(almacen.ultimaClaveDeObjeto).isNull();
    }

    @Test
    @DisplayName("una solicitud señuelo (sin titular) tampoco genera URL")
    void rechazaUnSenuelo() {
        UUID senuelo = UUID.randomUUID();
        solicitudes.titulares.put(senuelo, null);

        assertThatThrownBy(() -> servicio.generar(senuelo, TipoDeDocumentoKyc.ANVERSO, "jpg"))
                .isInstanceOf(SolicitudNoExisteException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("una extension nula, vacia o en blanco cae por defecto a jpg")
    void usaJpgPorDefectoCuandoNoHayExtension(String extension) {
        servicio.generar(solicitudId, TipoDeDocumentoKyc.ANVERSO, extension);

        assertThat(almacen.ultimaClaveDeObjeto).endsWith(".jpg");
        assertThat(almacen.ultimoTipoDeContenido).isEqualTo("image/jpeg");
    }

    @ParameterizedTest
    @CsvSource({
        "PNG, png",
        " jpg , jpg",
        "P.N.G, png",
    })
    @DisplayName("limpia mayusculas, espacios y caracteres invalidos antes de validar la extension")
    void saneaLaExtensionAntesDeValidarla(String extensionRecibida, String extensionEsperada) {
        servicio.generar(solicitudId, TipoDeDocumentoKyc.ANVERSO, extensionRecibida);

        assertThat(almacen.ultimaClaveDeObjeto).endsWith("." + extensionEsperada);
    }

    @ParameterizedTest
    @ValueSource(strings = {"exe", "svg", "php", "jpgx", "pdf"})
    @DisplayName("una extension fuera del catalogo permitido se rechaza, PDF incluido")
    void rechazaUnaExtensionNoPermitida(String extensionInvalida) {
        // PDF fuera: kyc-service analiza con OpenCV, que no lee PDF, y lo rechazaria siempre
        // como "no es un DNI" sin que el solicitante supiera por que.
        assertThatThrownBy(() ->
                servicio.generar(solicitudId, TipoDeDocumentoKyc.ANVERSO, extensionInvalida))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jpg, jpeg, png o webp");

        assertThat(almacen.ultimaClaveDeObjeto).isNull();
    }

    // ─── Dobles ────────────────────────────────────────────────────────────

    private static final class SolicitudesFalsas implements RepositorioDeSolicitudesPort {
        private final Map<UUID, UUID> titulares = new HashMap<>();

        @Override
        public UUID abrirPara(UUID usuarioId, IdentidadDeclarada identidad) {
            UUID id = UUID.randomUUID();
            titulares.put(id, usuarioId);
            return id;
        }

        @Override
        public UUID abrirSenuelo() {
            UUID id = UUID.randomUUID();
            titulares.put(id, null);
            return id;
        }

        @Override
        public Optional<UUID> titularDe(UUID solicitudId) {
            return Optional.ofNullable(titulares.get(solicitudId));
        }

        @Override
        public void marcarAprobada(UUID solicitudId) {
        }

        @Override
        public int registrarIntentoFallidoDeKyc(UUID solicitudId, TipoDeDocumentoKyc lado) {
            throw new UnsupportedOperationException("No usado en estas pruebas");
        }

        @Override
        public boolean estaEnRevisionManual(UUID solicitudId) {
            throw new UnsupportedOperationException("No usado en estas pruebas");
        }

        @Override
        public Optional<DatosDeclarados> datosDeclaradosDe(UUID solicitudId) {
            throw new UnsupportedOperationException("No usado en estas pruebas");
        }

        @Override
        public void marcarDocumentoCargado(UUID solicitudId) {
            throw new UnsupportedOperationException("No usado en estas pruebas");
        }

        @Override
        public void marcarEnRevisionManual(UUID solicitudId) {
            throw new UnsupportedOperationException("No usado en estas pruebas");
        }

        @Override
        public Optional<String> nombreDePilaDe(UUID usuarioId) {
            return Optional.of("Ana");
        }
    }

    private static final class AlmacenFalso implements AlmacenDeDocumentosPort {
        private String ultimaClaveDeObjeto;
        private String ultimoTipoDeContenido;
        private long ultimoTamanoMaximo;
        private final UrlDeSubida urlDevuelta = new UrlDeSubida("https://minio.local/ayni-kyc-documentos",
                Map.of("policy", "p"), "kyc/x/anverso-y.jpg", Instant.parse("2026-09-06T10:05:00Z"));

        @Override
        public UrlDeSubida generarUrlDeSubida(String claveDeObjeto, String tipoDeContenido, long tamanoMaximoBytes) {
            ultimaClaveDeObjeto = claveDeObjeto;
            ultimoTipoDeContenido = tipoDeContenido;
            ultimoTamanoMaximo = tamanoMaximoBytes;
            return urlDevuelta;
        }

        @Override
        public String calcularHash(String claveDeObjeto) {
            throw new UnsupportedOperationException("No usado en estas pruebas");
        }

        @Override
        public Optional<ObjetoAlmacenado> describir(String claveDeObjeto) {
            throw new UnsupportedOperationException("No usado en estas pruebas");
        }

        @Override
        public void eliminar(String claveDeObjeto) {
            throw new UnsupportedOperationException("No usado en estas pruebas");
        }
    }
}
