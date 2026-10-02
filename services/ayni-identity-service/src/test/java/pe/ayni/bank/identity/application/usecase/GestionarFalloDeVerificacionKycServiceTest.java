package pe.ayni.bank.identity.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.ayni.bank.identity.aceptacion.DoblesEnMemoria;
import pe.ayni.bank.identity.domain.model.Celular;
import pe.ayni.bank.identity.domain.model.Consentimiento;
import pe.ayni.bank.identity.domain.model.ContrasenaCifrada;
import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.model.ResultadoDelIntentoKyc;
import pe.ayni.bank.identity.domain.model.TipoDeDocumentoKyc;
import pe.ayni.bank.identity.domain.model.Usuario;

/**
 * AYNI-13: limite de tres intentos POR LADO y derivacion a revision manual (ADR-0021,
 * ADR-0028), y el aviso al solicitante cuando eso ocurre (ADR-0024).
 */
class GestionarFalloDeVerificacionKycServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-12T10:15:30Z");

    private DoblesEnMemoria.Solicitudes solicitudes;
    private DoblesEnMemoria.NotificadorDeVerificacionKyc notificador;
    private GestionarFalloDeVerificacionKycService servicio;
    private UUID solicitudId;
    private Usuario titular;

    @BeforeEach
    void prepararEscenario() {
        solicitudes = new DoblesEnMemoria.Solicitudes();
        DoblesEnMemoria.Usuarios usuarios = new DoblesEnMemoria.Usuarios();
        notificador = new DoblesEnMemoria.NotificadorDeVerificacionKyc();
        servicio = new GestionarFalloDeVerificacionKycService(solicitudes, usuarios, notificador);

        titular = Usuario.registrar(UUID.randomUUID(),
                new CorreoElectronico("ana.quispe@example.pe"), new Celular("987654321"),
                new ContrasenaCifrada("$argon2id$loquesea"),
                Consentimiento.otorgar(true, AHORA, "v1"), AHORA);
        usuarios.guardar(titular);
        solicitudId = solicitudes.abrirPara(titular.id(), null);
    }

    @Test
    @DisplayName("el primer y el segundo fallo dejan reintentar, con los intentos que quedan")
    void losPrimerosFallosPermitenReintentar() {
        assertThat(servicio.registrarFalloDeUsuario(solicitudId, TipoDeDocumentoKyc.ANVERSO))
                .isEqualTo(ResultadoDelIntentoKyc.puedeReintentar(2));
        assertThat(servicio.registrarFalloDeUsuario(solicitudId, TipoDeDocumentoKyc.ANVERSO))
                .isEqualTo(ResultadoDelIntentoKyc.puedeReintentar(1));

        assertThat(solicitudes.enRevisionManual).isEmpty();
        assertThat(notificador.avisadosDeRevisionManual).isEmpty();
    }

    @Test
    @DisplayName("el tercer fallo del mismo lado agota el limite, deriva y avisa al titular")
    void elTercerFalloDeriva() {
        servicio.registrarFalloDeUsuario(solicitudId, TipoDeDocumentoKyc.REVERSO);
        servicio.registrarFalloDeUsuario(solicitudId, TipoDeDocumentoKyc.REVERSO);

        ResultadoDelIntentoKyc resultado = servicio.registrarFalloDeUsuario(solicitudId, TipoDeDocumentoKyc.REVERSO);

        assertThat(resultado).isEqualTo(ResultadoDelIntentoKyc.derivada());
        assertThat(solicitudes.enRevisionManual).containsExactly(solicitudId);
        assertThat(notificador.avisadosDeRevisionManual).containsExactly("ana.quispe@example.pe");
    }

    @Test
    @DisplayName("los intentos se cuentan por lado: dos del anverso y dos del reverso no derivan")
    void losIntentosSonPorLado() {
        servicio.registrarFalloDeUsuario(solicitudId, TipoDeDocumentoKyc.ANVERSO);
        servicio.registrarFalloDeUsuario(solicitudId, TipoDeDocumentoKyc.ANVERSO);
        servicio.registrarFalloDeUsuario(solicitudId, TipoDeDocumentoKyc.REVERSO);
        ResultadoDelIntentoKyc cuarto = servicio.registrarFalloDeUsuario(solicitudId, TipoDeDocumentoKyc.REVERSO);

        assertThat(cuarto).isEqualTo(ResultadoDelIntentoKyc.puedeReintentar(1));
        assertThat(solicitudes.intentosDe(solicitudId, TipoDeDocumentoKyc.ANVERSO)).isEqualTo(2);
        assertThat(solicitudes.intentosDe(solicitudId, TipoDeDocumentoKyc.REVERSO)).isEqualTo(2);
        assertThat(solicitudes.enRevisionManual).isEmpty();
    }

    @Test
    @DisplayName("un fallo sobre una solicitud ya derivada no suma ni vuelve a avisar (escenario 4)")
    void unFalloTrasDerivarNoSumaNiAvisa() {
        for (int i = 0; i < 3; i++) {
            servicio.registrarFalloDeUsuario(solicitudId, TipoDeDocumentoKyc.ANVERSO);
        }

        ResultadoDelIntentoKyc cuarto = servicio.registrarFalloDeUsuario(solicitudId, TipoDeDocumentoKyc.ANVERSO);

        assertThat(cuarto).isEqualTo(ResultadoDelIntentoKyc.derivada());
        assertThat(solicitudes.intentosDe(solicitudId, TipoDeDocumentoKyc.ANVERSO)).isEqualTo(3);
        assertThat(notificador.avisadosDeRevisionManual).hasSize(1);
    }

    @Test
    @DisplayName("la caida de kyc-service deriva de inmediato, sin gastar intentos, y avisa al titular")
    void laCaidaDelServicioDerivaSinContarIntentos() {
        servicio.derivarPorServicioNoDisponible(solicitudId);

        assertThat(solicitudes.enRevisionManual).containsExactly(solicitudId);
        assertThat(solicitudes.intentosKyc).doesNotContainKey(solicitudId);
        assertThat(notificador.avisadosDeRevisionManual).containsExactly("ana.quispe@example.pe");
    }

    @Test
    @DisplayName("una discrepancia deriva una sola vez aunque se repita")
    void unaDiscrepanciaDerivaUnaSolaVez() {
        servicio.derivarPorDiscrepancia(solicitudId);
        servicio.derivarPorDiscrepancia(solicitudId);

        assertThat(solicitudes.enRevisionManual).containsExactly(solicitudId);
        assertThat(notificador.avisadosDeRevisionManual).hasSize(1);
    }

    @Test
    @DisplayName("un senuelo sin titular no genera ningun aviso")
    void unSenueloNoGeneraAviso() {
        UUID senuelo = solicitudes.abrirSenuelo();

        servicio.derivarPorServicioNoDisponible(senuelo);

        assertThat(solicitudes.enRevisionManual).containsExactly(senuelo);
        assertThat(notificador.avisadosDeRevisionManual).isEmpty();
    }
}
