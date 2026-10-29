package pe.ayni.bank.identity.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.cucumber.java.Before;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;
import pe.ayni.bank.identity.application.usecase.AprobarSolicitudService;
import pe.ayni.bank.identity.application.usecase.EntregarSelfieService;
import pe.ayni.bank.identity.application.usecase.GestionarFalloDeVerificacionKycService;
import pe.ayni.bank.identity.application.usecase.RechazarSolicitudService;
import pe.ayni.bank.identity.domain.model.Celular;
import pe.ayni.bank.identity.domain.model.Consentimiento;
import pe.ayni.bank.identity.domain.model.ContrasenaCifrada;
import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.model.DocumentoNoSubidoException;
import pe.ayni.bank.identity.domain.model.EstadoDelPasoKyc;
import pe.ayni.bank.identity.domain.model.EstadoUsuario;
import pe.ayni.bank.identity.domain.model.MetodoDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.MotivoDeRechazoDeSolicitud;
import pe.ayni.bank.identity.domain.model.SegundoFactorNoVerificadoException;
import pe.ayni.bank.identity.domain.model.TipoDeDocumentoKyc;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.Usuario;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeMetodoSegundoFactorPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSegundoFactorPort;

/** Pasos de {@code verificacion-biometrica-selfie.feature} · HU-03. */
public class PasosDeVerificacionBiometricaSelfie {

    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");

    private DoblesEnMemoria.Usuarios usuarios;
    private DoblesEnMemoria.Solicitudes solicitudes;
    private DoblesEnMemoria.DocumentosKyc documentos;
    private DoblesEnMemoria.Almacen almacen;
    private DoblesEnMemoria.NotificadorDeVerificacionKyc notificador;
    private DoblesEnMemoria.Publicador publicador;
    private RepositorioDeMetodoSegundoFactorPort metodosSegundoFactor;
    private RepositorioDeSegundoFactorPort segundosFactores;
    private EntregarSelfieService entregarSelfie;
    private AprobarSolicitudService aprobarSolicitud;
    private RechazarSolicitudService rechazarSolicitud;

    private Usuario mateo;
    private UUID solicitudId;
    private String claveSelfie;
    private EstadoDelPasoKyc estadoPaso;
    private Throwable fallo;

    @Before
    public void preparar() {
        usuarios = new DoblesEnMemoria.Usuarios();
        solicitudes = new DoblesEnMemoria.Solicitudes();
        documentos = new DoblesEnMemoria.DocumentosKyc();
        almacen = new DoblesEnMemoria.Almacen();
        notificador = new DoblesEnMemoria.NotificadorDeVerificacionKyc();
        publicador = new DoblesEnMemoria.Publicador();
        metodosSegundoFactor = mock(RepositorioDeMetodoSegundoFactorPort.class);
        segundosFactores = mock(RepositorioDeSegundoFactorPort.class);

        GestionarFalloDeVerificacionKycService fallos =
                new GestionarFalloDeVerificacionKycService(solicitudes, usuarios, notificador);
        entregarSelfie = new EntregarSelfieService(
                solicitudes, documentos, almacen, fallos, Clock.fixed(AHORA, ZoneOffset.UTC),
                metodosSegundoFactor, segundosFactores);
        aprobarSolicitud = new AprobarSolicitudService(solicitudes, usuarios, publicador);
        rechazarSolicitud = new RechazarSolicitudService(solicitudes, usuarios, publicador);
        fallo = null;
    }

    @Dado("que Mateo completó la captura de su DNI y tiene una solicitud de onboarding abierta")
    public void mateoConSolicitudAbierta() {
        mateo = Usuario.registrar(
                UUID.randomUUID(),
                new CorreoElectronico("mateo@example.pe"),
                new Celular("987654321"),
                new ContrasenaCifrada("$argon2id$loquesea"),
                Consentimiento.otorgar(true, AHORA, "v1"),
                AHORA);
        usuarios.guardar(mateo);
        solicitudId = solicitudes.abrirPara(mateo.id(), null);
        solicitudes.marcarDocumentoCargado(solicitudId);
    }

    @Dado("que Mateo verificó su segundo factor de autenticación")
    public void mateoVerificoSegundoFactor() {
        MetodoDeSegundoFactor verificado = MetodoDeSegundoFactor
                .inscribir(UUID.randomUUID(), mateo.id(), TipoDeSegundoFactor.CORREO_ELECTRONICO, "mateo@example.pe", AHORA)
                .confirmar(AHORA);
        when(metodosSegundoFactor.listarPorUsuario(mateo.id())).thenReturn(List.of(verificado));
    }

    @Dado("que Mateo aún no ha verificado su segundo factor de autenticación")
    public void mateoSinSegundoFactor() {
        when(metodosSegundoFactor.listarPorUsuario(mateo.id())).thenReturn(List.of());
        when(segundosFactores.buscarPorUsuario(mateo.id())).thenReturn(Optional.empty());
    }

    @Cuando("Mateo sube su selfie válida en vivo y la entrega para verificación")
    public void subeYEntregaSelfieValida() {
        claveSelfie = "kyc/" + solicitudId + "/selfie-" + UUID.randomUUID() + ".jpg";
        almacen.subir(claveSelfie, "selfie-en-vivo-valida".getBytes(StandardCharsets.UTF_8));
        estadoPaso = entregarSelfie.entregar(solicitudId, claveSelfie);
    }

    @Cuando("Mateo intenta entregar su selfie para verificación")
    public void intentaEntregarSinSegundoFactor() {
        claveSelfie = "kyc/" + solicitudId + "/selfie-" + UUID.randomUUID() + ".jpg";
        almacen.subir(claveSelfie, "selfie-en-vivo".getBytes(StandardCharsets.UTF_8));
        fallo = catchThrowable(() -> entregarSelfie.entregar(solicitudId, claveSelfie));
    }

    @Cuando("Mateo sube un archivo de selfie que supera los 5 MB permitidos y lo entrega")
    public void subeSelfieDemasiadoGrande() {
        claveSelfie = "kyc/" + solicitudId + "/selfie-" + UUID.randomUUID() + ".jpg";
        byte[] gigante = new byte[(int) TipoDeDocumentoKyc.TAMANO_MAXIMO_BYTES + 1024];
        almacen.subir(claveSelfie, gigante);
        fallo = catchThrowable(() -> entregarSelfie.entregar(solicitudId, claveSelfie));
    }

    @Entonces("el sistema guarda la selfie con su hash SHA-256 y pasa la solicitud a revisión de cotejo facial")
    public void guardaSelfieConHashYPasaARevision() {
        assertThat(estadoPaso).isEqualTo(EstadoDelPasoKyc.EN_REVISION_MANUAL);
        var guardado = documentos.ultimoDe(solicitudId, TipoDeDocumentoKyc.SELFIE).orElseThrow();
        assertThat(guardado.hashSha256()).isEqualTo(almacen.calcularHash(claveSelfie));
        assertThat(solicitudes.estaEnRevisionManual(solicitudId)).isTrue();
    }

    @Cuando("el cotejo facial con su DNI resulta favorable y se aprueba la solicitud")
    public void cotejoFacialFavorable() {
        aprobarSolicitud.aprobar(solicitudId);
    }

    @Entonces("la solicitud de Mateo pasa a estado APROBADA, el usuario queda ACTIVO y se anuncia la apertura de su cuenta")
    public void solicitudAprobadaYUsuarioActivo() {
        assertThat(solicitudes.estadoDe(solicitudId)).contains("APROBADA");
        assertThat(usuarios.buscarPorId(mateo.id())).get()
                .extracting(Usuario::estado).isEqualTo(EstadoUsuario.ACTIVO);
        assertThat(publicador.aprobaciones).containsExactly(solicitudId);
    }

    @Entonces("el sistema bloquea la entrega con SegundoFactorNoVerificadoException")
    public void bloqueaPorSegundoFactorNoVerificado() {
        assertThat(fallo).isInstanceOf(SegundoFactorNoVerificadoException.class);
    }

    @Entonces("el sistema elimina el objeto del almacén y rechaza la entrega con DocumentoNoSubidoException")
    public void eliminaObjetoYRechaza() {
        assertThat(fallo).isInstanceOf(DocumentoNoSubidoException.class);
        assertThat(almacen.eliminados).contains(claveSelfie);
    }

    @Cuando("el cotejo facial determina que la selfie no coincide con el rostro del DNI")
    public void cotejoFacialRechaza() {
        rechazarSolicitud.rechazar(solicitudId, MotivoDeRechazoDeSolicitud.SELFIE_NO_COINCIDE);
    }

    @Entonces("la solicitud de Mateo queda RECHAZADA con el motivo {word} y no se anuncia ninguna aprobación")
    public void solicitudRechazadaPorSelfie(String motivo) {
        assertThat(solicitudes.estadoDe(solicitudId)).contains("RECHAZADA");
        assertThat(solicitudes.rechazadas.get(solicitudId))
                .isEqualTo(MotivoDeRechazoDeSolicitud.valueOf(motivo));
        assertThat(publicador.aprobaciones).isEmpty();
    }
}
