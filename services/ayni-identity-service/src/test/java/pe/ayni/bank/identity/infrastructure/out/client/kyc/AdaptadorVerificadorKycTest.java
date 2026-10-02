package pe.ayni.bank.identity.infrastructure.out.client.kyc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import pe.ayni.bank.identity.domain.model.EvaluacionDeCaptura;
import pe.ayni.bank.identity.domain.model.FuenteDeLectura;
import pe.ayni.bank.identity.domain.model.KycServiceNoDisponibleException;
import pe.ayni.bank.identity.domain.model.LecturaDelDni;
import pe.ayni.bank.identity.domain.model.MotivoDeRechazoDeCaptura;
import pe.ayni.bank.identity.domain.model.TipoDeDocumentoKyc;
import pe.ayni.bank.identity.infrastructure.config.ReintentoAnteKycNoDisponible;
import pe.ayni.bank.identity.infrastructure.out.client.kyc.api.DocumentosApi;
import pe.ayni.bank.identity.infrastructure.out.client.kyc.model.DatosExtraidos;
import pe.ayni.bank.identity.infrastructure.out.client.kyc.model.EvaluacionRequest;
import pe.ayni.bank.identity.infrastructure.out.client.kyc.model.EvaluacionResponse;
import pe.ayni.bank.identity.infrastructure.out.client.kyc.model.ExtraccionRequest;
import pe.ayni.bank.identity.infrastructure.out.client.kyc.model.ExtraccionResponse;
import pe.ayni.bank.identity.infrastructure.out.client.kyc.model.MotivoRechazo;

/**
 * Carga solo la autoconfiguracion de AOP + Retry + CircuitBreaker (no
 * {@code @SpringBootTest}, sin datasource ni Flyway ni RabbitMQ): confirma
 * que las anotaciones {@code @CircuitBreaker}/{@code @Retry} de
 * AdaptadorVerificadorKyc estan realmente conectadas al bean real via proxy
 * AOP, no solo que la configuracion de Resilience4j en si es correcta.
 */
class AdaptadorVerificadorKycTest {

    private static final String ANVERSO = "kyc/0f8fad5b-d9cb-469f-a165-70867728950e/anverso-1.jpg";
    private static final String REVERSO = "kyc/0f8fad5b-d9cb-469f-a165-70867728950e/reverso-2.jpg";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AopAutoConfiguration.class,
                    CircuitBreakerAutoConfiguration.class,
                    RetryAutoConfiguration.class))
            // Duraciones cortas a proposito: solo se prueba el COMPORTAMIENTO
            // (numero de intentos, apertura del circuito), no los tiempos de
            // produccion (esos viven en application.yml, ver ADR-0020).
            .withPropertyValues(
                    // Orden explicito: CircuitBreaker afuera, Retry adentro. Ver ADR-0020.
                    "resilience4j.circuitbreaker.circuit-breaker-aspect-order=1",
                    "resilience4j.retry.retry-aspect-order=2",
                    "resilience4j.retry.instances.kycService.max-attempts=3",
                    "resilience4j.retry.instances.kycService.wait-duration=1ms",
                    // Igual que application.yml: solo el predicado, sin retry-exceptions.
                    "resilience4j.retry.instances.kycService.retry-exception-predicate="
                            + ReintentoAnteKycNoDisponible.class.getName(),
                    "resilience4j.circuitbreaker.instances.kycService.sliding-window-size=4",
                    "resilience4j.circuitbreaker.instances.kycService.minimum-number-of-calls=2",
                    "resilience4j.circuitbreaker.instances.kycService.failure-rate-threshold=50",
                    "resilience4j.circuitbreaker.instances.kycService.wait-duration-in-open-state=1m",
                    "resilience4j.circuitbreaker.instances.kycService.record-exceptions[0]="
                            + "org.springframework.web.client.HttpServerErrorException");

    private static HttpServerErrorException errorDelServidor() {
        return HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "", null, null, null);
    }

    @Test
    @DisplayName("reintenta 3 veces ante un error 5xx y termina en KycServiceNoDisponibleException")
    void reintentaTresVecesAnteFallosDelServidor() {
        DocumentosApiFalsa fake = DocumentosApiFalsa.evaluando(() -> { throw errorDelServidor(); });

        conAdaptador(fake, adaptador -> {
            assertThatThrownBy(() -> adaptador.evaluar(ANVERSO, TipoDeDocumentoKyc.ANVERSO))
                    .isInstanceOf(KycServiceNoDisponibleException.class)
                    .hasCauseInstanceOf(HttpServerErrorException.class);

            assertThat(fake.llamadas).isEqualTo(3);
        });
    }

    @Test
    @DisplayName("un timeout de lectura NO se reintenta: el solicitante no espera 3 x 10 s")
    void noReintentaUnTimeoutDeLectura() {
        DocumentosApiFalsa fake = DocumentosApiFalsa.evaluando(() -> {
            throw new ResourceAccessException("Read timed out", new SocketTimeoutException("Read timed out"));
        });

        conAdaptador(fake, adaptador -> {
            assertThatThrownBy(() -> adaptador.evaluar(ANVERSO, TipoDeDocumentoKyc.ANVERSO))
                    .isInstanceOf(KycServiceNoDisponibleException.class);

            assertThat(fake.llamadas).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("una conexion rechazada si se reintenta: falla al instante")
    void reintentaUnaConexionRechazada() {
        DocumentosApiFalsa fake = DocumentosApiFalsa.evaluando(() -> {
            throw new ResourceAccessException("Connection refused", new ConnectException("refused"));
        });

        conAdaptador(fake, adaptador -> {
            assertThatThrownBy(() -> adaptador.evaluar(ANVERSO, TipoDeDocumentoKyc.ANVERSO))
                    .isInstanceOf(KycServiceNoDisponibleException.class);

            assertThat(fake.llamadas).isEqualTo(3);
        });
    }

    @Test
    @DisplayName("un error 4xx no dispara reintento ni se convierte en 'no disponible'")
    void noReintentaAnteUnErrorDeCliente() {
        DocumentosApiFalsa fake = DocumentosApiFalsa.evaluando(() -> {
            throw HttpClientErrorException.create(HttpStatus.NOT_FOUND, "", null, null, null);
        });

        conAdaptador(fake, adaptador -> {
            assertThatThrownBy(() -> adaptador.evaluar(ANVERSO, TipoDeDocumentoKyc.ANVERSO))
                    .isInstanceOf(HttpClientErrorException.class);

            assertThat(fake.llamadas).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("el circuito se abre tras agotar el umbral de fallos y deja de llamar al servicio")
    void elCircuitoSeAbreTrasFallosRepetidos() {
        DocumentosApiFalsa fake = DocumentosApiFalsa.evaluando(() -> { throw errorDelServidor(); });

        conAdaptador(fake, adaptador -> {
            // 2 llamadas (minimum-number-of-calls), cada una agota sus 3
            // reintentos: 100% de fallo, supera el 50% de umbral -> circuito ABIERTO.
            for (int i = 0; i < 2; i++) {
                assertThatThrownBy(() -> adaptador.evaluar(ANVERSO, TipoDeDocumentoKyc.ANVERSO))
                        .isInstanceOf(KycServiceNoDisponibleException.class);
            }
            int llamadasAntesDeAbrir = fake.llamadas;

            // Con el circuito abierto, ni siquiera se llama al servicio real. Y afecta a las
            // dos operaciones: comparten la instancia "kycService".
            assertThatThrownBy(() -> adaptador.extraer(ANVERSO, REVERSO))
                    .isInstanceOf(KycServiceNoDisponibleException.class);
            assertThat(fake.llamadas).isEqualTo(llamadasAntesDeAbrir);
        });
    }

    @Test
    @DisplayName("traduce una evaluacion rechazada al motivo del dominio")
    void traduceElMotivoDeRechazo() {
        DocumentosApiFalsa fake = DocumentosApiFalsa.evaluando(() -> new EvaluacionResponse()
                .aceptada(false).esDni(true).nitida(false).sinReflejos(true).bienEncuadrada(true)
                .bienIluminada(true).motivoRechazo(MotivoRechazo.DESENFOQUE));

        conAdaptador(fake, adaptador -> {
            assertThat(adaptador.evaluar(ANVERSO, TipoDeDocumentoKyc.ANVERSO))
                    .isEqualTo(EvaluacionDeCaptura.rechazada(MotivoDeRechazoDeCaptura.DESENFOQUE));
            assertThat(fake.ultimaEvaluacion.getDocumentKey()).isEqualTo(ANVERSO);
            assertThat(fake.ultimaEvaluacion.getLado().getValue()).isEqualTo("ANVERSO");
        });
    }

    @Test
    @DisplayName("una evaluacion aceptada llega sin pasar por el fallback")
    void unaEvaluacionAceptadaLlegaSinFallback() {
        DocumentosApiFalsa fake = DocumentosApiFalsa.evaluando(() -> new EvaluacionResponse()
                .aceptada(true).esDni(true).nitida(true).sinReflejos(true).bienEncuadrada(true)
                .bienIluminada(true));

        conAdaptador(fake, adaptador -> {
            assertThat(adaptador.evaluar(REVERSO, TipoDeDocumentoKyc.REVERSO).aceptada()).isTrue();
            assertThat(fake.llamadas).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("traduce los datos extraidos a una lectura del dominio")
    void traduceLosDatosExtraidos() {
        DocumentosApiFalsa fake = DocumentosApiFalsa.extrayendo(() -> new ExtraccionResponse()
                .legible(true)
                .datos(new DatosExtraidos().dni("44556677").nombres("ANA LUCIA").apellidos("QUISPE MAMANI")
                        .fechaNacimiento(LocalDate.of(1990, 5, 15)).sexo("F")
                        .fechaEmision(LocalDate.of(2021, 8, 20))
                        .fuente(DatosExtraidos.FuenteEnum.MRZ).confiable(true)));

        conAdaptador(fake, adaptador -> {
            Optional<LecturaDelDni> lectura = adaptador.extraer(ANVERSO, REVERSO);

            assertThat(lectura).isPresent();
            assertThat(lectura.get().datos().numero()).isEqualTo("44556677");
            assertThat(lectura.get().datos().fechaEmision()).isEqualTo(LocalDate.of(2021, 8, 20));
            assertThat(lectura.get().fuente()).isEqualTo(FuenteDeLectura.MRZ);
            assertThat(lectura.get().confiable()).isTrue();
        });
    }

    @Test
    @DisplayName("un documento ilegible, o datos que violan las reglas del dominio, son una lectura vacia")
    void unaLecturaIlegibleEsVacia() {
        DocumentosApiFalsa ilegible = DocumentosApiFalsa.extrayendo(() -> new ExtraccionResponse().legible(false));
        conAdaptador(ilegible, adaptador -> assertThat(adaptador.extraer(ANVERSO, REVERSO)).isEmpty());

        // Un sexo "<" (el MRZ no lo informa) no es un DNI valido para el dominio.
        DocumentosApiFalsa invalida = DocumentosApiFalsa.extrayendo(() -> new ExtraccionResponse()
                .legible(true)
                .datos(new DatosExtraidos().dni("44556677").nombres("ANA").apellidos("QUISPE")
                        .fechaNacimiento(LocalDate.of(1990, 5, 15)).sexo("<")
                        .fuente(DatosExtraidos.FuenteEnum.MRZ).confiable(true)));
        conAdaptador(invalida, adaptador -> assertThat(adaptador.extraer(ANVERSO, REVERSO)).isEmpty());
    }

    @Test
    @DisplayName("la lectura por OCR va por su propio cliente (timeout largo); la evaluacion, por el de 10 s")
    void cadaOperacionUsaSuCliente() {
        DocumentosApiFalsa evaluacion = DocumentosApiFalsa.evaluando(() -> new EvaluacionResponse()
                .aceptada(true).esDni(true).nitida(true).sinReflejos(true).bienEncuadrada(true)
                .bienIluminada(true));
        DocumentosApiFalsa extraccion = DocumentosApiFalsa.extrayendo(() -> new ExtraccionResponse().legible(false));
        AdaptadorVerificadorKyc adaptador = new AdaptadorVerificadorKyc(evaluacion, extraccion);

        adaptador.evaluar(ANVERSO, TipoDeDocumentoKyc.ANVERSO);
        adaptador.extraer(ANVERSO, REVERSO);

        assertThat(evaluacion.llamadas).isEqualTo(1);
        assertThat(extraccion.llamadas).isEqualTo(1);
    }

    private void conAdaptador(DocumentosApiFalsa fake, Consumer<AdaptadorVerificadorKyc> prueba) {
        contextRunner
                .withBean(DocumentosApi.class, () -> fake)
                .withBean(AdaptadorVerificadorKyc.class, () -> new AdaptadorVerificadorKyc(fake, fake))
                .run(contexto -> prueba.accept(contexto.getBean(AdaptadorVerificadorKyc.class)));
    }

    /** Sustituye a DocumentosApi (clase concreta generada, no una interfaz). */
    private static final class DocumentosApiFalsa extends DocumentosApi {
        private final Supplier<EvaluacionResponse> evaluacion;
        private final Supplier<ExtraccionResponse> extraccion;
        private EvaluacionRequest ultimaEvaluacion;
        int llamadas = 0;

        private DocumentosApiFalsa(Supplier<EvaluacionResponse> evaluacion, Supplier<ExtraccionResponse> extraccion) {
            this.evaluacion = evaluacion;
            this.extraccion = extraccion;
        }

        static DocumentosApiFalsa evaluando(Supplier<EvaluacionResponse> comportamiento) {
            return new DocumentosApiFalsa(comportamiento, () -> {
                throw new IllegalStateException("No usado en esta prueba");
            });
        }

        static DocumentosApiFalsa extrayendo(Supplier<ExtraccionResponse> comportamiento) {
            return new DocumentosApiFalsa(() -> {
                throw new IllegalStateException("No usado en esta prueba");
            }, comportamiento);
        }

        @Override
        public EvaluacionResponse evaluarCaptura(EvaluacionRequest evaluacionRequest) {
            llamadas++;
            ultimaEvaluacion = evaluacionRequest;
            return evaluacion.get();
        }

        @Override
        public ExtraccionResponse extraerDatos(ExtraccionRequest extraccionRequest) {
            llamadas++;
            return extraccion.get();
        }
    }
}
