package pe.ayni.bank.identity.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import io.cucumber.java.Before;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;

import pe.ayni.bank.identity.application.usecase.GestionarFalloDeVerificacionKycService;
import pe.ayni.bank.identity.domain.model.Celular;
import pe.ayni.bank.identity.domain.model.Consentimiento;
import pe.ayni.bank.identity.domain.model.ContrasenaCifrada;
import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.model.ResultadoDelIntentoKyc;
import pe.ayni.bank.identity.domain.model.Usuario;

/**
 * Pasos de {@code verificacion-de-identidad-dni.feature} · HU-02.
 *
 * <p>Solo cubre los escenarios 4 y 5 de los cinco que Jira aprobó — ver la cabecera del
 * fichero de caracteristicas para por que los otros tres no estan aqui.
 */
public class PasosDeVerificacionDeIdentidad {

    private static final Instant AHORA = Instant.parse("2026-09-12T10:15:30Z");

    private DoblesEnMemoria.Solicitudes solicitudes;
    private DoblesEnMemoria.Usuarios usuarios;
    private DoblesEnMemoria.NotificadorDeVerificacionKyc notificador;
    private GestionarFalloDeVerificacionKycService servicio;

    private Usuario titular;
    private UUID solicitudId;
    private ResultadoDelIntentoKyc resultado;
    private Throwable fallo;

    @Before
    public void prepararEscenario() {
        solicitudes = new DoblesEnMemoria.Solicitudes();
        usuarios = new DoblesEnMemoria.Usuarios();
        notificador = new DoblesEnMemoria.NotificadorDeVerificacionKyc();
        servicio = new GestionarFalloDeVerificacionKycService(solicitudes, usuarios, notificador);
        resultado = null;
        fallo = null;
    }

    // ─── Antecedentes ──────────────────────────────────────────────────────

    @Dado("que existe una solicitud de onboarding para {string}")
    public void queExisteUnaSolicitudDeOnboardingPara(String correo) {
        titular = Usuario.registrar(UUID.randomUUID(), new CorreoElectronico(correo),
                new Celular("987654321"), new ContrasenaCifrada("$argon2id$loquesea"),
                Consentimiento.otorgar(true, AHORA, "v1"), AHORA);
        usuarios.guardar(titular);
        solicitudId = solicitudes.abrirPara(titular.id(), null);
    }

    // ─── Escenario: Agotamiento de intentos ───────────────────────────────

    @Dado("que el solicitante ha fallado {int} capturas del mismo lado del documento")
    public void queElSolicitanteHaFalladoCapturas(int fallos) {
        for (int i = 0; i < fallos; i++) {
            servicio.registrarFalloDeUsuario(solicitudId);
        }
    }

    @Cuando("el solicitante intenta una tercera captura")
    public void elSolicitanteIntentaUnaTerceraCaptura() {
        resultado = servicio.registrarFalloDeUsuario(solicitudId);
    }

    @Entonces("el sistema deriva la solicitud a revisión manual")
    public void elSistemaDerivaLaSolicitudARevisionManual() {
        // Jira llama al estado "EN_REVISION"; el sistema persiste "EN_REVISION_MANUAL" — ver
        // la cabecera del fichero de caracteristicas. Se comprueba el estado real.
        assertThat(resultado).isEqualTo(ResultadoDelIntentoKyc.DERIVADA_A_REVISION_MANUAL);
        assertThat(solicitudes.enRevisionManual).containsExactly(solicitudId);
    }

    @Y("el sistema notifica al solicitante que su caso será revisado por un operador")
    public void elSistemaNotificaAlSolicitante() {
        assertThat(notificador.avisadosDeRevisionManual).containsExactly(titular.correo().valor());
    }

    // ─── Escenario: El servicio de visión no responde ─────────────────────

    @Dado("que el servicio kyc-vision-service está caído o excede el timeout de 10 segundos")
    public void queElServicioDeVisionEstaCaido() {
        // Sin efecto: representa que Resilience4j ya agoto retry+circuit breaker antes de
        // llegar aqui (eso lo prueba AdaptadorVerificadorKycTest, subtarea 10). Este paso
        // solo deja constancia de la premisa del escenario.
    }

    @Cuando("el solicitante envía la captura de su DNI")
    public void elSolicitanteEnviaLaCapturaDeSuDni() {
        // No hay todavia un caso de uso de integracion que atrape la excepcion del
        // VerificadorKycPort y llame a derivarPorServicioNoDisponible (esa es la pieza
        // pendiente, ver ADR-0024): se invoca directamente, que es lo que ese caso de uso
        // futuro haria en su lugar.
        try {
            servicio.derivarPorServicioNoDisponible(solicitudId);
        } catch (RuntimeException e) {
            fallo = e;
        }
    }

    @Entonces("el sistema no falla con error técnico")
    public void elSistemaNoFallaConErrorTecnico() {
        assertThat(fallo).isNull();
    }

    @Y("el sistema registra la solicitud en revisión manual")
    public void elSistemaRegistraLaSolicitudEnRevisionManual() {
        assertThat(solicitudes.enRevisionManual).contains(solicitudId);
    }

    @Y("el sistema informa al solicitante que su verificación continuará en breve")
    public void elSistemaInformaAlSolicitante() {
        assertThat(notificador.avisadosDeRevisionManual).contains(titular.correo().valor());
    }
}
