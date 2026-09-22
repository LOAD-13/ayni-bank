package pe.ayni.bank.identity.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pe.ayni.bank.identity.domain.model.Celular;
import pe.ayni.bank.identity.domain.model.Consentimiento;
import pe.ayni.bank.identity.domain.model.ContrasenaCifrada;
import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.model.DesafioPorCodigo;
import pe.ayni.bank.identity.domain.model.ResultadoGeneracionDesafio;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.Usuario;
import pe.ayni.bank.identity.domain.port.out.NotificadorDeSegundoFactorPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeDesafioPorCodigoPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeUsuariosPort;

@ExtendWith(MockitoExtension.class)
class GenerarDesafioCodigoServiceTest {

    @Mock
    private RepositorioDeDesafioPorCodigoPort repositorioDesafio;

    @Mock
    private RepositorioDeUsuariosPort repositorioUsuarios;

    @Mock
    private NotificadorDeSegundoFactorPort notificador;

    private GenerarDesafioCodigoService service;

    @BeforeEach
    void setUp() {
        service = new GenerarDesafioCodigoService(repositorioDesafio, repositorioUsuarios, notificador);
    }

    private static Usuario usuarioDePrueba(UUID id) {
        return Usuario.reconstituir(id, new CorreoElectronico("ana.quispe@ejemplo.pe"),
                new Celular("987654321"), new ContrasenaCifrada("$argon2id$v=19$m=16,t=2,p=1$c2FsdA$aGFzaA"),
                pe.ayni.bank.identity.domain.model.EstadoUsuario.ACTIVO,
                Consentimiento.otorgar(true, Instant.now(), "v1"), Instant.now());
    }

    @Test
    @DisplayName("Genera correctamente un código OTP de 6 dígitos con hash SHA-256 y lo despacha por correo")
    void generar_CodigoOTPExitoso() {
        UUID usuarioId = UUID.randomUUID();
        when(repositorioDesafio.guardar(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repositorioUsuarios.buscarPorId(usuarioId)).thenReturn(Optional.of(usuarioDePrueba(usuarioId)));

        ResultadoGeneracionDesafio resultado = service.generar(usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO);

        assertThat(resultado.codigo6Digitos()).matches("^\\d{6}$");
        assertThat(resultado.desafio()).isNotNull();
        assertThat(resultado.desafio().usuarioId()).isEqualTo(usuarioId);
        assertThat(resultado.desafio().tipoFactor()).isEqualTo(TipoDeSegundoFactor.CORREO_ELECTRONICO);
        assertThat(resultado.desafio().hashCodigo()).hasSize(64); // SHA-256 hex string

        verify(repositorioDesafio).guardar(any(DesafioPorCodigo.class));
        verify(notificador).enviarCodigoPorCorreo(eq(new CorreoElectronico("ana.quispe@ejemplo.pe")),
                eq(resultado.codigo6Digitos()));
    }

    @Test
    @DisplayName("Despacha el código por SMS cuando el método elegido es SMS")
    void generar_DespachaPorSms() {
        UUID usuarioId = UUID.randomUUID();
        when(repositorioDesafio.guardar(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repositorioUsuarios.buscarPorId(usuarioId)).thenReturn(Optional.of(usuarioDePrueba(usuarioId)));

        service.generar(usuarioId, TipoDeSegundoFactor.SMS);

        verify(notificador).enviarCodigoPorSms(eq(new Celular("987654321")), any());
        verify(notificador, never()).enviarCodigoPorCorreo(any(), any());
    }

    @Test
    @DisplayName("No despacha nada para APP_AUTENTICADORA: el código lo genera la app, no este servicio")
    void generar_NoDespachaParaAppAutenticadora() {
        UUID usuarioId = UUID.randomUUID();
        when(repositorioDesafio.guardar(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.generar(usuarioId, TipoDeSegundoFactor.APP_AUTENTICADORA);

        verify(repositorioUsuarios, never()).buscarPorId(any());
        verify(notificador, never()).enviarCodigoPorCorreo(any(), any());
        verify(notificador, never()).enviarCodigoPorSms(any(), any());
    }

    @Test
    @DisplayName("Invalida desafío previo no verificado al generar uno nuevo")
    void generar_InvalidaDesafioPrevio() {
        UUID usuarioId = UUID.randomUUID();
        DesafioPorCodigo previo = DesafioPorCodigo.generar(
                UUID.randomUUID(), usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, "hashViejo", java.time.Instant.now());

        when(repositorioDesafio.buscarUltimoPendiente(usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO))
                .thenReturn(java.util.Optional.of(previo));
        when(repositorioDesafio.guardar(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repositorioUsuarios.buscarPorId(usuarioId)).thenReturn(Optional.of(usuarioDePrueba(usuarioId)));

        ResultadoGeneracionDesafio resultado = service.generar(usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO);

        assertThat(resultado.desafio()).isNotNull();
        verify(repositorioDesafio, org.mockito.Mockito.atLeast(2)).guardar(any(DesafioPorCodigo.class));
    }
}
