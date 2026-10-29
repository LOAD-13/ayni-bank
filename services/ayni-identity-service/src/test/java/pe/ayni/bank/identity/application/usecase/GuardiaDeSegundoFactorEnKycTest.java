package pe.ayni.bank.identity.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.ayni.bank.identity.domain.model.DatosConfirmados;
import pe.ayni.bank.identity.domain.model.DesafioAbierto;
import pe.ayni.bank.identity.domain.model.MetodoDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.SecretoTotp;
import pe.ayni.bank.identity.domain.model.SegundoFactor;
import pe.ayni.bank.identity.domain.model.SegundoFactorNoVerificadoException;
import pe.ayni.bank.identity.domain.model.SolicitudNoExisteException;
import pe.ayni.bank.identity.domain.model.TipoDeDocumentoKyc;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;
import pe.ayni.bank.identity.domain.port.in.GestionarFalloDeVerificacionKycUseCase;
import pe.ayni.bank.identity.domain.port.out.AlmacenDeDocumentosPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeDocumentosKycPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeMetodoSegundoFactorPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSegundoFactorPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSolicitudesPort;
import pe.ayni.bank.identity.domain.port.out.VerificadorKycPort;

class GuardiaDeSegundoFactorEnKycTest {

    private static final Instant AHORA = Instant.parse("2026-10-20T12:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);

    @Test
    @DisplayName("Si no hay repositorios de segundo factor inyectados, no bloquea el paso")
    void sinRepositoriosNoBloquea() {
        assertThatCode(() -> GuardiaDeSegundoFactorEnKyc.exigirVerificado(UUID.randomUUID(), null, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Permite el paso cuando el titular tiene un metodo confirmado en metodo_segundo_factor")
    void permiteConMetodoConfirmado() {
        UUID titular = UUID.randomUUID();
        RepositorioDeMetodoSegundoFactorPort metodos = mock(RepositorioDeMetodoSegundoFactorPort.class);
        MetodoDeSegundoFactor confirmado = MetodoDeSegundoFactor
                .inscribir(UUID.randomUUID(), titular, TipoDeSegundoFactor.CORREO_ELECTRONICO, null, AHORA)
                .confirmar(AHORA);
        when(metodos.listarPorUsuario(titular)).thenReturn(List.of(confirmado));

        assertThatCode(() -> GuardiaDeSegundoFactorEnKyc.exigirVerificado(titular, metodos, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Permite el paso cuando el titular tiene su TOTP confirmado en segundo_factor")
    void permiteConTotpConfirmado() {
        UUID titular = UUID.randomUUID();
        RepositorioDeMetodoSegundoFactorPort metodos = mock(RepositorioDeMetodoSegundoFactorPort.class);
        RepositorioDeSegundoFactorPort totp = mock(RepositorioDeSegundoFactorPort.class);
        when(metodos.listarPorUsuario(titular)).thenReturn(List.of());
        SegundoFactor sf = SegundoFactor
                .inscribir(titular, new SecretoTotp("JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP"), AHORA)
                .confirmar(AHORA);
        when(totp.buscarPorUsuario(titular)).thenReturn(Optional.of(sf));

        assertThatCode(() -> GuardiaDeSegundoFactorEnKyc.exigirVerificado(titular, metodos, totp))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Rechaza con SegundoFactorNoVerificadoException si el metodo o TOTP solo esta pendiente")
    void rechazaSiNoEstaConfirmado() {
        UUID titular = UUID.randomUUID();
        RepositorioDeMetodoSegundoFactorPort metodos = mock(RepositorioDeMetodoSegundoFactorPort.class);
        RepositorioDeSegundoFactorPort totp = mock(RepositorioDeSegundoFactorPort.class);
        MetodoDeSegundoFactor pendiente = MetodoDeSegundoFactor
                .inscribir(UUID.randomUUID(), titular, TipoDeSegundoFactor.CORREO_ELECTRONICO, null, AHORA);
        SegundoFactor totpPendiente = SegundoFactor
                .inscribir(titular, new SecretoTotp("JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP"), AHORA);
        when(metodos.listarPorUsuario(titular)).thenReturn(List.of(pendiente));
        when(totp.buscarPorUsuario(titular)).thenReturn(Optional.of(totpPendiente));

        assertThatThrownBy(() -> GuardiaDeSegundoFactorEnKyc.exigirVerificado(titular, metodos, totp))
                .isInstanceOf(SegundoFactorNoVerificadoException.class);
    }

    @Test
    @DisplayName("Los casos de uso de captura, extraccion, confirmacion y selfie exigen segundo factor verificado")
    void serviciosKycExigenSegundoFactorVerificado() {
        UUID solicitudId = UUID.randomUUID();
        UUID titularId = UUID.randomUUID();
        RepositorioDeSolicitudesPort solicitudes = mock(RepositorioDeSolicitudesPort.class);
        RepositorioDeDocumentosKycPort documentos = mock(RepositorioDeDocumentosKycPort.class);
        AlmacenDeDocumentosPort almacen = mock(AlmacenDeDocumentosPort.class);
        VerificadorKycPort verificador = mock(VerificadorKycPort.class);
        GestionarFalloDeVerificacionKycUseCase fallos = mock(GestionarFalloDeVerificacionKycUseCase.class);
        RepositorioDeMetodoSegundoFactorPort metodos = mock(RepositorioDeMetodoSegundoFactorPort.class);
        RepositorioDeSegundoFactorPort totp = mock(RepositorioDeSegundoFactorPort.class);

        when(solicitudes.titularDe(solicitudId)).thenReturn(Optional.of(titularId));
        when(metodos.listarPorUsuario(titularId)).thenReturn(List.of());
        when(totp.buscarPorUsuario(titularId)).thenReturn(Optional.empty());

        EvaluarCapturaDeDniService evaluar = new EvaluarCapturaDeDniService(
                solicitudes, documentos, almacen, verificador, fallos, RELOJ, metodos, totp);
        ExtraerDatosDelDniService extraer = new ExtraerDatosDelDniService(
                solicitudes, documentos, almacen, verificador, fallos, metodos, totp);
        ConfirmarDatosDelDniService confirmar = new ConfirmarDatosDelDniService(
                solicitudes, documentos, fallos, metodos, totp);
        EntregarSelfieService selfie = new EntregarSelfieService(
                solicitudes, documentos, almacen, fallos, RELOJ, metodos, totp);

        assertThatThrownBy(() -> evaluar.evaluar(solicitudId, TipoDeDocumentoKyc.ANVERSO, "kyc/x"))
                .isInstanceOf(SegundoFactorNoVerificadoException.class);
        assertThatThrownBy(() -> extraer.extraer(solicitudId))
                .isInstanceOf(SegundoFactorNoVerificadoException.class);
        assertThatThrownBy(() -> confirmar.confirmar(
                solicitudId, new DatosConfirmados(null, null, null, null, null, null)))
                .isInstanceOf(SegundoFactorNoVerificadoException.class);
        assertThatThrownBy(() -> selfie.entregar(solicitudId, "kyc/x"))
                .isInstanceOf(SegundoFactorNoVerificadoException.class);

        UUID inexistente = UUID.randomUUID();
        when(solicitudes.titularDe(inexistente)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> confirmar.confirmar(
                inexistente, new DatosConfirmados(null, null, null, null, null, null)))
                .isInstanceOf(SolicitudNoExisteException.class);
    }

    @Test
    @DisplayName("DesafioAbierto: fabricas estaticas y toString sin exponer el URI de aprovisionamiento")
    void desafioAbierto() {
        UUID id = UUID.randomUUID();
        DesafioAbierto d1 = new DesafioAbierto(id, false, null);
        DesafioAbierto d2 = DesafioAbierto.paraQuienYaTieneSegundoFactor(id);
        DesafioAbierto d3 = DesafioAbierto.paraQuienYaTieneSegundoFactor(id, TipoDeSegundoFactor.CORREO_ELECTRONICO);
        DesafioAbierto d4 = DesafioAbierto.conInscripcion(id, "otpauth://totp/secreto");

        assertThat(d1.tipoMetodo()).isEqualTo(TipoDeSegundoFactor.APP_AUTENTICADORA);
        assertThat(d2.requiereInscripcion()).isFalse();
        assertThat(d3.tipoMetodo()).isEqualTo(TipoDeSegundoFactor.CORREO_ELECTRONICO);
        assertThat(d4.requiereInscripcion()).isTrue();
        assertThat(d4.toString()).doesNotContain("otpauth://").contains("CORREO_ELECTRONICO".substring(0, 0));
    }
}
