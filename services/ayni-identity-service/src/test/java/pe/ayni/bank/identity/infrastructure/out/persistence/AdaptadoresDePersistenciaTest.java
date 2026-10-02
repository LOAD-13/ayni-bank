package pe.ayni.bank.identity.infrastructure.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import pe.ayni.bank.identity.domain.model.ControlDeAcceso;
import pe.ayni.bank.identity.domain.model.DatosDelDni;
import pe.ayni.bank.identity.domain.model.DocumentoKyc;
import pe.ayni.bank.identity.domain.model.FuenteDeLectura;
import pe.ayni.bank.identity.domain.model.HuellaDeCliente;
import pe.ayni.bank.identity.domain.model.LecturaDelDni;
import pe.ayni.bank.identity.domain.model.SecretoTotp;
import pe.ayni.bank.identity.domain.model.SegundoFactor;
import pe.ayni.bank.identity.domain.model.TipoDeDocumentoKyc;
import pe.ayni.bank.identity.domain.model.TipoDeEventoDeAcceso;
import pe.ayni.bank.identity.domain.model.TipoDocumento;
import pe.ayni.bank.identity.domain.port.out.CifradorDeDatosPort;

/**
 * Los adaptadores de persistencia, con los repositorios simulados.
 *
 * <p>Lo que se prueba aquí es el **mapeo**, que es donde vive la lógica: que el secreto TOTP
 * se cifre al bajar y se descifre al subir, que un usuario sin fila devuelva un control
 * limpio en lugar de vacío, y que la auditoría guarde lo que HU-04 exige. Que Spring Data
 * sepa hacer un `findById` no lo prueba nadie aquí; eso llegará con las pruebas de
 * integración contra PostgreSQL real.
 */
@ExtendWith(MockitoExtension.class)
class AdaptadoresDePersistenciaTest {

    private static final Instant AHORA = Instant.parse("2026-08-30T10:15:30Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);
    private static final String SECRETO = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    private final UUID usuario = UUID.randomUUID();

    /** Cifrador de mentira: envuelve y desenvuelve, que es todo lo que el mapeo necesita. */
    private static final CifradorDeDatosPort CIFRADOR = new CifradorDeDatosPort() {
        @Override
        public String cifrar(String enClaro) {
            return enClaro == null ? null : "cifrado(" + enClaro + ")";
        }

        @Override
        public String descifrar(String criptograma) {
            return criptograma == null
                    ? null
                    : criptograma.replace("cifrado(", "").replace(")", "");
        }
    };

    @Nested
    @DisplayName("Segundo factor")
    class SegundosFactores {

        @Mock
        private SegundoFactorJpaRepository repositorio;

        @Test
        @DisplayName("el secreto se cifra al guardar: en claro seria como guardar la contraseña")
        void cifraAlGuardar() {
            var adaptador = new AdaptadorRepositorioDeSegundoFactor(repositorio, CIFRADOR);

            adaptador.guardar(SegundoFactor.inscribir(
                    usuario, new SecretoTotp(SECRETO), AHORA));

            // Lo que se comprueba es que el adaptador pase por el cifrador antes de
            // guardar. Que el resultado sea irreconocible lo prueba `CifradorAes256GcmTest`
            // sobre el cifrador de verdad; aquí el doble solo envuelve el valor.
            var fila = ArgumentCaptor.forClass(SegundoFactorEntity.class);
            verify(repositorio).save(fila.capture());
            assertThat(fila.getValue().getSecreto())
                    .isEqualTo("cifrado(" + SECRETO + ")")
                    .isNotEqualTo(SECRETO);
        }

        @Test
        void descifraAlLeer() {
            when(repositorio.findById(usuario)).thenReturn(Optional.of(
                    new SegundoFactorEntity(usuario, "cifrado(" + SECRETO + ")", AHORA, AHORA)));
            var adaptador = new AdaptadorRepositorioDeSegundoFactor(repositorio, CIFRADOR);

            var encontrado = adaptador.buscarPorUsuario(usuario);

            assertThat(encontrado).isPresent();
            assertThat(encontrado.get().secreto().valor()).isEqualTo(SECRETO);
            assertThat(encontrado.get().estaConfirmado()).isTrue();
        }

        @Test
        void unUsuarioSinSegundoFactorDevuelveVacio() {
            when(repositorio.findById(usuario)).thenReturn(Optional.empty());
            var adaptador = new AdaptadorRepositorioDeSegundoFactor(repositorio, CIFRADOR);

            assertThat(adaptador.buscarPorUsuario(usuario)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Control de acceso")
    class Controles {

        @Mock
        private ControlDeAccesoJpaRepository repositorio;

        @Test
        @DisplayName("quien nunca ha fallado no tiene fila, y aun asi se carga limpio")
        void sinFilaDevuelveLimpio() {
            // No se crea la fila al leer: un ingreso correcto no tiene por que provocar
            // una escritura.
            when(repositorio.findById(usuario)).thenReturn(Optional.empty());
            var adaptador = new AdaptadorRepositorioDeControlDeAcceso(repositorio, RELOJ);

            ControlDeAcceso control = adaptador.cargar(usuario);

            assertThat(control.fallosConsecutivos()).isZero();
            assertThat(control.estaBloqueado(AHORA)).isFalse();
            verify(repositorio, never()).save(any());
        }

        @Test
        void reconstruyeElBloqueoGuardado() {
            when(repositorio.findById(usuario)).thenReturn(Optional.of(
                    new ControlDeAccesoEntity(usuario, (short) 6, AHORA.plusSeconds(300), AHORA)));
            var adaptador = new AdaptadorRepositorioDeControlDeAcceso(repositorio, RELOJ);

            ControlDeAcceso control = adaptador.cargar(usuario);

            assertThat(control.fallosConsecutivos()).isEqualTo(6);
            assertThat(control.estaBloqueado(AHORA)).isTrue();
            assertThat(control.esperaRestante(AHORA).toSeconds()).isEqualTo(300);
        }

        @Test
        void guardaLosFallosYElBloqueo() {
            var adaptador = new AdaptadorRepositorioDeControlDeAcceso(repositorio, RELOJ);

            adaptador.guardar(ControlDeAcceso
                    .reconstituir(usuario, 6, AHORA.plusSeconds(300)));

            var fila = ArgumentCaptor.forClass(ControlDeAccesoEntity.class);
            verify(repositorio).save(fila.capture());
            assertThat(fila.getValue().getFallosConsecutivos()).isEqualTo((short) 6);
            assertThat(fila.getValue().getBloqueadoHasta()).isEqualTo(AHORA.plusSeconds(300));
        }
    }

    @Nested
    @DisplayName("Solicitudes · limite de intentos KYC (AYNI-13 subtarea 11)")
    class LimiteDeIntentosKyc {

        @Mock
        private SolicitudJpaRepository repositorio;

        @Mock
        private CifradorDeDatosPort cifradorDeDatos;

        private final SolicitudOnboardingEntity solicitud = new SolicitudOnboardingEntity(
                UUID.randomUUID(), usuario, "DOCUMENTO_CARGADO", (short) 3,
                AHORA, AHORA, AHORA.plusSeconds(3600));

        @Test
        @DisplayName("registrar un fallo suma un intento y lo devuelve, sin tocar el estado")
        void registrarUnFalloSumaUnIntento() {
            when(repositorio.findById(solicitud.getId())).thenReturn(Optional.of(solicitud));
            var adaptador = new AdaptadorRepositorioDeSolicitudes(repositorio, cifradorDeDatos, RELOJ);

            int intentos = adaptador.registrarIntentoFallidoDeKyc(solicitud.getId(), TipoDeDocumentoKyc.ANVERSO);

            assertThat(intentos).isEqualTo(1);
            assertThat(solicitud.getEstado()).isEqualTo("DOCUMENTO_CARGADO");
        }

        @Test
        @DisplayName("cada lado del DNI lleva su propio contador")
        void cadaLadoCuentaPorSeparado() {
            when(repositorio.findById(solicitud.getId())).thenReturn(Optional.of(solicitud));
            var adaptador = new AdaptadorRepositorioDeSolicitudes(repositorio, cifradorDeDatos, RELOJ);

            adaptador.registrarIntentoFallidoDeKyc(solicitud.getId(), TipoDeDocumentoKyc.ANVERSO);
            adaptador.registrarIntentoFallidoDeKyc(solicitud.getId(), TipoDeDocumentoKyc.ANVERSO);
            int reverso = adaptador.registrarIntentoFallidoDeKyc(solicitud.getId(), TipoDeDocumentoKyc.REVERSO);

            assertThat(reverso).isEqualTo(1);
            assertThat(solicitud.getIntentosKycAnverso()).isEqualTo((short) 2);
            assertThat(solicitud.getIntentosKycReverso()).isEqualTo((short) 1);
        }

        @Test
        @DisplayName("el contador nunca pasa de 3: la restriccion de la tabla lo prohibe")
        void elContadorNoPasaDeTres() {
            when(repositorio.findById(solicitud.getId())).thenReturn(Optional.of(solicitud));
            var adaptador = new AdaptadorRepositorioDeSolicitudes(repositorio, cifradorDeDatos, RELOJ);

            for (int i = 0; i < 5; i++) {
                adaptador.registrarIntentoFallidoDeKyc(solicitud.getId(), TipoDeDocumentoKyc.REVERSO);
            }

            assertThat(solicitud.getIntentosKycReverso()).isEqualTo((short) 3);
        }

        @Test
        @DisplayName("sabe si la solicitud ya esta en revision manual, y una inexistente no lo esta")
        void sabeSiEstaEnRevisionManual() {
            when(repositorio.findById(solicitud.getId())).thenReturn(Optional.of(solicitud));
            var adaptador = new AdaptadorRepositorioDeSolicitudes(repositorio, cifradorDeDatos, RELOJ);

            assertThat(adaptador.estaEnRevisionManual(solicitud.getId())).isFalse();
            adaptador.marcarEnRevisionManual(solicitud.getId());
            assertThat(adaptador.estaEnRevisionManual(solicitud.getId())).isTrue();
            assertThat(adaptador.estaEnRevisionManual(UUID.randomUUID())).isFalse();
        }

        @Test
        @DisplayName("los datos declarados vuelven con el documento descifrado; un senuelo no tiene")
        void devuelveLosDatosDeclaradosDescifrados() {
            solicitud.declarar("Ana Lucia", "Quispe Mamani", "DNI", "cifrado(44556677)", "6677",
                    java.time.LocalDate.of(1990, 5, 15));
            when(repositorio.findById(solicitud.getId())).thenReturn(Optional.of(solicitud));
            var adaptador = new AdaptadorRepositorioDeSolicitudes(repositorio, CIFRADOR, RELOJ);

            var datos = adaptador.datosDeclaradosDe(solicitud.getId()).orElseThrow();

            assertThat(datos.numeroDocumento()).isEqualTo("44556677");
            assertThat(datos.tipoDocumento()).isEqualTo(TipoDocumento.DNI);
            assertThat(datos.apellidos()).isEqualTo("Quispe Mamani");

            SolicitudOnboardingEntity senuelo = new SolicitudOnboardingEntity(
                    UUID.randomUUID(), null, "INICIADA", (short) 1, AHORA, AHORA, AHORA.plusSeconds(3600));
            when(repositorio.findById(senuelo.getId())).thenReturn(Optional.of(senuelo));
            assertThat(adaptador.datosDeclaradosDe(senuelo.getId())).isEmpty();
        }

        @Test
        @DisplayName("marcar el documento cargado cambia el estado")
        void marcarDocumentoCargado() {
            SolicitudOnboardingEntity iniciada = new SolicitudOnboardingEntity(
                    UUID.randomUUID(), usuario, "INICIADA", (short) 2, AHORA, AHORA, AHORA.plusSeconds(3600));
            when(repositorio.findById(iniciada.getId())).thenReturn(Optional.of(iniciada));
            var adaptador = new AdaptadorRepositorioDeSolicitudes(repositorio, cifradorDeDatos, RELOJ);

            adaptador.marcarDocumentoCargado(iniciada.getId());

            assertThat(iniciada.getEstado()).isEqualTo("DOCUMENTO_CARGADO");
        }

        @Test
        @DisplayName("marcar en revision manual cambia el estado")
        void marcarEnRevisionManualCambiaElEstado() {
            when(repositorio.findById(solicitud.getId())).thenReturn(Optional.of(solicitud));
            var adaptador = new AdaptadorRepositorioDeSolicitudes(repositorio, cifradorDeDatos, RELOJ);

            adaptador.marcarEnRevisionManual(solicitud.getId());

            assertThat(solicitud.getEstado()).isEqualTo("EN_REVISION_MANUAL");
        }

        @Test
        @DisplayName("registrar un fallo sobre una solicitud inexistente falla en vez de crear una fila")
        void registrarFalloSobreSolicitudInexistente() {
            UUID inexistente = UUID.randomUUID();
            when(repositorio.findById(inexistente)).thenReturn(Optional.empty());
            var adaptador = new AdaptadorRepositorioDeSolicitudes(repositorio, cifradorDeDatos, RELOJ);

            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> adaptador.registrarIntentoFallidoDeKyc(inexistente, TipoDeDocumentoKyc.ANVERSO))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("Documentos y lecturas del DNI · HU-02")
    class DocumentosKyc {

        @Mock
        private DocumentoKycJpaRepository documentos;

        @Mock
        private LecturaDniJpaRepository lecturas;

        private final UUID solicitudId = UUID.randomUUID();

        private final DatosDelDni datos = new DatosDelDni("44556677", "ANA LUCIA", "QUISPE MAMANI",
                java.time.LocalDate.of(1990, 5, 15), "F", java.time.LocalDate.of(2021, 8, 20));

        @Test
        @DisplayName("guarda la referencia, el hash, el tipo y el tamano del documento, nunca la imagen")
        void guardaLaReferenciaDelDocumento() {
            var adaptador = new AdaptadorRepositorioDeDocumentosKyc(documentos, lecturas, CIFRADOR, RELOJ);
            String hash = "a".repeat(64);

            adaptador.guardar(new DocumentoKyc(UUID.randomUUID(), solicitudId, TipoDeDocumentoKyc.REVERSO,
                    "kyc/x/reverso-y.jpg", hash, "image/jpeg", 2048L, AHORA));

            var fila = ArgumentCaptor.forClass(DocumentoKycEntity.class);
            verify(documentos).save(fila.capture());
            assertThat(fila.getValue().getTipoDocumento()).isEqualTo("REVERSO");
            assertThat(fila.getValue().getObjectKey()).isEqualTo("kyc/x/reverso-y.jpg");
            assertThat(fila.getValue().getHashSha256()).isEqualTo(hash);
            assertThat(fila.getValue().getMimeType()).isEqualTo("image/jpeg");
            assertThat(fila.getValue().getTamanoBytes()).isEqualTo(2048L);
        }

        @Test
        @DisplayName("el ultimo documento de un lado vuelve como objeto de dominio")
        void devuelveElUltimoDocumento() {
            var adaptador = new AdaptadorRepositorioDeDocumentosKyc(documentos, lecturas, CIFRADOR, RELOJ);
            when(documentos.findFirstBySolicitudIdAndTipoDocumentoOrderBySubidoEnDesc(solicitudId, "ANVERSO"))
                    .thenReturn(Optional.of(new DocumentoKycEntity(UUID.randomUUID(), solicitudId, "ANVERSO",
                            "kyc/x/anverso-y.png", "b".repeat(64), "image/png", 10L, AHORA)));

            var documento = adaptador.ultimoDe(solicitudId, TipoDeDocumentoKyc.ANVERSO).orElseThrow();

            assertThat(documento.tipo()).isEqualTo(TipoDeDocumentoKyc.ANVERSO);
            assertThat(documento.claveDeObjeto()).isEqualTo("kyc/x/anverso-y.png");
        }

        @Test
        @DisplayName("el numero del DNI se cifra al guardar la lectura; solo quedan en claro los 4 ultimos")
        void cifraElNumeroDeLaLectura() {
            var adaptador = new AdaptadorRepositorioDeDocumentosKyc(documentos, lecturas, CIFRADOR, RELOJ);

            adaptador.guardarLectura(solicitudId, new LecturaDelDni(datos, FuenteDeLectura.MRZ, true));

            var fila = ArgumentCaptor.forClass(LecturaDniEntity.class);
            verify(lecturas).save(fila.capture());
            assertThat(fila.getValue().getNumeroCifrado()).isEqualTo("cifrado(44556677)");
            assertThat(fila.getValue().getFuente()).isEqualTo("MRZ");
            assertThat(fila.getValue().toString()).doesNotContain("QUISPE").doesNotContain("44556677");
        }

        @Test
        @DisplayName("la ultima lectura del OCR se busca solo entre MRZ y anverso, y vuelve descifrada")
        void devuelveLaUltimaLecturaDelOcr() {
            var adaptador = new AdaptadorRepositorioDeDocumentosKyc(documentos, lecturas, CIFRADOR, RELOJ);
            when(lecturas.findFirstBySolicitudIdAndFuenteInOrderByLeidaEnDesc(
                    solicitudId, java.util.List.of("MRZ", "HEURISTICA_ANVERSO")))
                    .thenReturn(Optional.of(new LecturaDniEntity(UUID.randomUUID(), solicitudId, "MRZ", true,
                            "cifrado(44556677)", "6677", "ANA LUCIA", "QUISPE MAMANI",
                            java.time.LocalDate.of(1990, 5, 15), "F", null, AHORA)));

            var lectura = adaptador.ultimaLecturaOcrDe(solicitudId).orElseThrow();

            assertThat(lectura.datos().numero()).isEqualTo("44556677");
            assertThat(lectura.fuente()).isEqualTo(FuenteDeLectura.MRZ);
            assertThat(lectura.confiable()).isTrue();
            assertThat(lectura.datos().fechaEmision()).isNull();
        }
    }

    @Nested
    @DisplayName("Pista de auditoría")
    class Auditoria {

        @Mock
        private EventoAuditoriaJpaRepository repositorio;

        @Test
        @DisplayName("registra el tipo, la IP y el agente que exige HU-04")
        void registraLoQueExigeLaHistoria() {
            var metricas = new SimpleMeterRegistry();
            var adaptador = new AdaptadorPistaDeAuditoria(repositorio, RELOJ, metricas);

            adaptador.registrar(TipoDeEventoDeAcceso.INGRESO_EXITOSO, usuario,
                    new HuellaDeCliente("190.12.4.7", "Mozilla/5.0"));

            verify(repositorio).save(any(EventoAuditoriaEntity.class));
            assertThat(metricas.counter("ayni.acceso.eventos", "tipo", "INGRESO_EXITOSO").count())
                    .isEqualTo(1.0);
        }

        @Test
        @DisplayName("un intento sobre un correo desconocido tambien se anota, sin usuario")
        void anotaTambienLoQueNoTieneTitular() {
            // Registrar el intento importa aunque no se sepa contra quien iba.
            var adaptador = new AdaptadorPistaDeAuditoria(repositorio, RELOJ, new SimpleMeterRegistry());

            adaptador.registrar(TipoDeEventoDeAcceso.CREDENCIALES_INVALIDAS, null,
                    new HuellaDeCliente(null, null));

            verify(repositorio).save(any(EventoAuditoriaEntity.class));
        }
    }
}
