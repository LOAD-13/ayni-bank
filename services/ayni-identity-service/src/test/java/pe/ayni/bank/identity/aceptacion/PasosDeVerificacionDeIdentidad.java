package pe.ayni.bank.identity.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import io.cucumber.java.Before;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;

import pe.ayni.bank.identity.application.usecase.ConfirmarDatosDelDniService;
import pe.ayni.bank.identity.application.usecase.EvaluarCapturaDeDniService;
import pe.ayni.bank.identity.application.usecase.ExtraerDatosDelDniService;
import pe.ayni.bank.identity.application.usecase.GestionarFalloDeVerificacionKycService;
import pe.ayni.bank.identity.domain.model.Celular;
import pe.ayni.bank.identity.domain.model.Consentimiento;
import pe.ayni.bank.identity.domain.model.ContrasenaCifrada;
import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.model.DatosConfirmados;
import pe.ayni.bank.identity.domain.model.DatosDelDni;
import pe.ayni.bank.identity.domain.model.DocumentoDeIdentidad;
import pe.ayni.bank.identity.domain.model.EstadoDelPasoKyc;
import pe.ayni.bank.identity.domain.model.EvaluacionDeCaptura;
import pe.ayni.bank.identity.domain.model.FechaDeNacimiento;
import pe.ayni.bank.identity.domain.model.FuenteDeLectura;
import pe.ayni.bank.identity.domain.model.IdentidadDeclarada;
import pe.ayni.bank.identity.domain.model.LecturaDelDni;
import pe.ayni.bank.identity.domain.model.MotivoDeRechazoDeCaptura;
import pe.ayni.bank.identity.domain.model.ResultadoDeCaptura;
import pe.ayni.bank.identity.domain.model.ResultadoDeExtraccion;
import pe.ayni.bank.identity.domain.model.TipoDeDocumentoKyc;
import pe.ayni.bank.identity.domain.model.TipoDocumento;
import pe.ayni.bank.identity.domain.model.Usuario;

/** Pasos de {@code verificacion-de-identidad-dni.feature} · HU-02. */
public class PasosDeVerificacionDeIdentidad {

    private static final Instant AHORA = Instant.parse("2026-09-12T10:15:30Z");
    private static final LocalDate NACIMIENTO = LocalDate.of(1990, 5, 15);
    private static final LocalDate EMISION = LocalDate.of(2021, 8, 20);

    private DoblesEnMemoria.Solicitudes solicitudes;
    private DoblesEnMemoria.NotificadorDeVerificacionKyc notificador;
    private DoblesEnMemoria.DocumentosKyc documentos;
    private DoblesEnMemoria.Almacen almacen;
    private DoblesEnMemoria.VerificadorKyc verificador;
    private EvaluarCapturaDeDniService evaluar;
    private ExtraerDatosDelDniService extraer;
    private ConfirmarDatosDelDniService confirmar;

    private Usuario titular;
    private String numeroDeclarado;
    private UUID solicitudId;
    private String ultimaClave;
    private ResultadoDeCaptura resultadoDeCaptura;
    private ResultadoDeCaptura resultadoAnverso;
    private ResultadoDeCaptura resultadoReverso;
    private ResultadoDeExtraccion resultadoDeExtraccion;
    private EstadoDelPasoKyc resultadoDeConfirmacion;
    private Throwable fallo;

    @Before
    public void prepararEscenario() {
        solicitudes = new DoblesEnMemoria.Solicitudes();
        DoblesEnMemoria.Usuarios usuarios = new DoblesEnMemoria.Usuarios();
        notificador = new DoblesEnMemoria.NotificadorDeVerificacionKyc();
        documentos = new DoblesEnMemoria.DocumentosKyc();
        almacen = new DoblesEnMemoria.Almacen();
        verificador = new DoblesEnMemoria.VerificadorKyc();

        GestionarFalloDeVerificacionKycService fallos =
                new GestionarFalloDeVerificacionKycService(solicitudes, usuarios, notificador);
        evaluar = new EvaluarCapturaDeDniService(solicitudes, documentos, almacen, verificador, fallos,
                Clock.fixed(AHORA, ZoneOffset.UTC));
        extraer = new ExtraerDatosDelDniService(solicitudes, documentos, almacen, verificador, fallos);
        confirmar = new ConfirmarDatosDelDniService(solicitudes, documentos, fallos);

        titular = Usuario.registrar(UUID.randomUUID(), new CorreoElectronico("ana.quispe@example.pe"),
                new Celular("987654321"), new ContrasenaCifrada("$argon2id$loquesea"),
                Consentimiento.otorgar(true, AHORA, "v1"), AHORA);
        usuarios.guardar(titular);
        fallo = null;
    }

    private String subir(TipoDeDocumentoKyc lado) {
        ultimaClave = "kyc/" + solicitudId + "/" + lado.prefijoDeObjeto() + UUID.randomUUID() + ".jpg";
        almacen.subir(ultimaClave, ("foto " + ultimaClave).getBytes(StandardCharsets.UTF_8));
        return ultimaClave;
    }

    private ResultadoDeCaptura capturar(TipoDeDocumentoKyc lado) {
        return evaluar.evaluar(solicitudId, lado, subir(lado));
    }

    private DatosDelDni datosDelDni(String nombres) {
        return new DatosDelDni(numeroDeclarado, nombres, "QUISPE MAMANI", NACIMIENTO, "F", EMISION);
    }

    // ─── Antecedentes ──────────────────────────────────────────────────────

    @Dado("que {string} completó su registro declarando el DNI {string}")
    public void queCompletoSuRegistro(String correo, String numero) {
        assertThat(titular.correo().valor()).isEqualTo(correo);
        numeroDeclarado = numero;
        solicitudId = solicitudes.abrirPara(titular.id(), new IdentidadDeclarada("Ana Lucía", "Quispe Mamani",
                new DocumentoDeIdentidad(TipoDocumento.DNI, numero),
                FechaDeNacimiento.de(NACIMIENTO, LocalDate.of(2026, 9, 12))));
    }

    // ─── Escenario 1: Captura correcta de ambos lados ─────────────────────

    @Dado("que el solicitante accede al paso de verificación de identidad")
    public void queAccedeAlPasoDeVerificacion() {
        assertThat(solicitudes.estaEnRevisionManual(solicitudId)).isFalse();
    }

    @Cuando("el solicitante captura el anverso y el reverso de su DNI con calidad suficiente")
    public void capturaAmbosLados() {
        resultadoAnverso = capturar(TipoDeDocumentoKyc.ANVERSO);
        resultadoReverso = capturar(TipoDeDocumentoKyc.REVERSO);
        verificador.lectura = Optional.of(new LecturaDelDni(datosDelDni("ANA LUCIA"), FuenteDeLectura.MRZ, true));
        resultadoDeExtraccion = extraer.extraer(solicitudId);
    }

    @Entonces("el sistema detecta que ambas imágenes corresponden a un DNI peruano")
    public void detectaAmbasImagenes() {
        assertThat(resultadoAnverso).isEqualTo(ResultadoDeCaptura.aceptada());
        assertThat(resultadoReverso).isEqualTo(ResultadoDeCaptura.aceptada());
    }

    @Y("el sistema extrae los datos mediante OCR y los muestra para confirmación")
    public void extraeLosDatos() {
        assertThat(resultadoDeExtraccion.estado()).isEqualTo(EstadoDelPasoKyc.ACEPTADO);
        assertThat(resultadoDeExtraccion.lectura().datos().numero()).isEqualTo(numeroDeclarado);
    }

    @Y("el sistema almacena ambas imágenes en MinIO registrando su hash SHA-256")
    public void almacenaAmbasImagenes() {
        for (TipoDeDocumentoKyc lado : new TipoDeDocumentoKyc[] {TipoDeDocumentoKyc.ANVERSO, TipoDeDocumentoKyc.REVERSO}) {
            var documento = documentos.ultimoDe(solicitudId, lado).orElseThrow();
            assertThat(almacen.objetos).containsKey(documento.claveDeObjeto());
            assertThat(documento.hashSha256()).isEqualTo(almacen.calcularHash(documento.claveDeObjeto()));
        }
    }

    // ─── Escenarios 2 y 3: la foto se rechaza ─────────────────────────────

    @Dado("que el solicitante captura una fotografía de un objeto que no es un DNI")
    public void capturaUnObjetoQueNoEsUnDni() {
        verificador.evaluaciones.add(EvaluacionDeCaptura.rechazada(MotivoDeRechazoDeCaptura.NO_ES_DNI));
    }

    @Dado("^que el solicitante captura el DNI con (desenfoque|reflejos|encuadre incompleto|poca luz)$")
    public void capturaElDniConUnProblema(String problema) {
        MotivoDeRechazoDeCaptura motivo = switch (problema) {
            case "desenfoque" -> MotivoDeRechazoDeCaptura.DESENFOQUE;
            case "reflejos" -> MotivoDeRechazoDeCaptura.REFLEJO;
            case "encuadre incompleto" -> MotivoDeRechazoDeCaptura.ENCUADRE;
            default -> MotivoDeRechazoDeCaptura.ILUMINACION;
        };
        verificador.evaluaciones.add(EvaluacionDeCaptura.rechazada(motivo));
    }

    @Cuando("el sistema analiza la imagen")
    public void elSistemaAnalizaLaImagen() {
        resultadoDeCaptura = capturar(TipoDeDocumentoKyc.ANVERSO);
    }

    @Cuando("el sistema evalúa la calidad de la imagen")
    public void elSistemaEvaluaLaCalidad() {
        resultadoDeCaptura = capturar(TipoDeDocumentoKyc.ANVERSO);
    }

    @Entonces("el sistema rechaza la captura")
    public void rechazaLaCaptura() {
        assertThat(resultadoDeCaptura.estado()).isEqualTo(EstadoDelPasoKyc.RECHAZADO);
    }

    @Y("el sistema no almacena la imagen")
    public void noAlmacenaLaImagen() {
        assertThat(almacen.objetos).doesNotContainKey(ultimaClave);
        assertThat(documentos.guardados).isEmpty();
    }

    @Y("el sistema indica al solicitante que debe fotografiar su DNI")
    public void indicaQueDebeFotografiarSuDni() {
        assertThat(resultadoDeCaptura.motivo()).isEqualTo(MotivoDeRechazoDeCaptura.NO_ES_DNI);
    }

    @Y("el sistema indica el motivo concreto {string} para que el solicitante repita")
    public void indicaElMotivoConcreto(String motivo) {
        assertThat(resultadoDeCaptura.motivo()).isEqualTo(MotivoDeRechazoDeCaptura.valueOf(motivo));
        assertThat(resultadoDeCaptura.intentosRestantes()).isEqualTo(2);
    }

    // ─── Escenario 4: Agotamiento de intentos ─────────────────────────────

    @Dado("que el solicitante ha fallado {int} capturas del mismo lado del documento")
    public void haFalladoCapturas(int fallos) {
        for (int i = 0; i < fallos; i++) {
            verificador.evaluaciones.add(EvaluacionDeCaptura.rechazada(MotivoDeRechazoDeCaptura.DESENFOQUE));
            capturar(TipoDeDocumentoKyc.REVERSO);
        }
    }

    @Cuando("el solicitante intenta una cuarta captura")
    public void intentaUnaCuartaCaptura() {
        int evaluacionesAntes = verificador.evaluacionesPedidas;
        resultadoDeCaptura = capturar(TipoDeDocumentoKyc.REVERSO);
        // Derivada, la cuarta foto ni siquiera se envia a kyc-service.
        assertThat(verificador.evaluacionesPedidas).isEqualTo(evaluacionesAntes);
    }

    @Entonces("el sistema deriva la solicitud a revisión manual con estado EN_REVISION_MANUAL")
    public void derivaARevisionManual() {
        assertThat(resultadoDeCaptura).isEqualTo(ResultadoDeCaptura.enRevisionManual());
        assertThat(solicitudes.estaEnRevisionManual(solicitudId)).isTrue();
    }

    @Y("el sistema notifica al solicitante que su caso será revisado por un operador")
    public void notificaAlSolicitante() {
        // Una sola vez: la cuarta captura no repite el aviso.
        assertThat(notificador.avisadosDeRevisionManual).containsExactly(titular.correo().valor());
    }

    // ─── Escenario 5: El servicio de visión no responde ───────────────────

    @Dado("que el servicio kyc-vision-service está caído o excede el timeout de 10 segundos")
    public void queElServicioDeVisionEstaCaido() {
        verificador.caido = true;
    }

    @Cuando("el solicitante envía la captura de su DNI")
    public void enviaLaCapturaDeSuDni() {
        try {
            resultadoDeCaptura = capturar(TipoDeDocumentoKyc.ANVERSO);
        } catch (RuntimeException e) {
            fallo = e;
        }
    }

    @Entonces("el sistema no falla con error técnico")
    public void noFallaConErrorTecnico() {
        assertThat(fallo).isNull();
    }

    @Y("el sistema registra la solicitud en estado EN_REVISION_MANUAL")
    public void registraLaSolicitudEnRevisionManual() {
        assertThat(solicitudes.estaEnRevisionManual(solicitudId)).isTrue();
    }

    @Y("el sistema informa al solicitante que su verificación continuará en breve")
    public void informaQueContinuaraEnBreve() {
        assertThat(resultadoDeCaptura).isEqualTo(ResultadoDeCaptura.diferida());
        assertThat(notificador.avisadosDeRevisionManual).contains(titular.correo().valor());
    }

    // ─── Corrección manual antes de confirmar ─────────────────────────────

    @Dado("que el OCR leyó del anverso el nombre {string} en lugar de {string}")
    public void queElOcrLeyoMalElNombre(String leido, String correcto) {
        assertThat(leido).isNotEqualTo(correcto);
        documentos.guardarLectura(solicitudId,
                new LecturaDelDni(datosDelDni(leido), FuenteDeLectura.HEURISTICA_ANVERSO, false));
    }

    @Cuando("el solicitante corrige el nombre a {string} y confirma sus datos")
    public void corrigeElNombreYConfirma(String nombre) {
        resultadoDeConfirmacion = confirmar.confirmar(solicitudId,
                new DatosConfirmados(null, nombre, "Quispe Mamani", NACIMIENTO, "F", EMISION));
    }

    @Entonces("el sistema acepta los datos confirmados")
    public void aceptaLosDatosConfirmados() {
        assertThat(resultadoDeConfirmacion).isEqualTo(EstadoDelPasoKyc.ACEPTADO);
        assertThat(solicitudes.documentosCargados).containsExactly(solicitudId);
    }

    @Y("el sistema conserva tanto lo que leyó el OCR como lo que confirmó el solicitante")
    public void conservaAmbasLecturas() {
        assertThat(documentos.lecturasDe(solicitudId))
                .extracting(LecturaDelDni::fuente)
                .containsExactly(FuenteDeLectura.HEURISTICA_ANVERSO, FuenteDeLectura.TITULAR);
        assertThat(documentos.lecturasDe(solicitudId).get(1).datos().nombres()).isEqualTo("Ana Lucía");
    }
}
