package pe.ayni.bank.identity.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

import pe.ayni.bank.identity.domain.model.CodigoDesafioInvalidoException;
import pe.ayni.bank.identity.domain.model.DesafioExpiradoException;
import pe.ayni.bank.identity.domain.model.DesafioPorCodigo;
import pe.ayni.bank.identity.domain.model.MaximoIntentosDesafioExcedidoException;
import pe.ayni.bank.identity.domain.model.MetodoDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.SolicitudVerificacionContacto;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeDesafioPorCodigoPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeMetodoSegundoFactorPort;

@ExtendWith(MockitoExtension.class)
class VerificarContactoRegistroServiceTest {

    @Mock
    private RepositorioDeDesafioPorCodigoPort repositorioDesafio;

    @Mock
    private RepositorioDeMetodoSegundoFactorPort repositorioMetodos;

    private VerificarContactoRegistroService service;

    private final UUID usuarioId = UUID.randomUUID();
    private final String codigoCorrecto = "654321";
    private String hashCorrecto;

    @BeforeEach
    void setUp() {
        service = new VerificarContactoRegistroService(repositorioDesafio, repositorioMetodos);
        hashCorrecto = GenerarDesafioCodigoService.calcularHashSha256(usuarioId, codigoCorrecto);
    }

    @Test
    @DisplayName("Verifica contacto exitosamente y confirma el método 2FA (nuevo método o existente)")
    void verificarContacto_Exito() {
        DesafioPorCodigo desafio = DesafioPorCodigo.generar(UUID.randomUUID(), usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, hashCorrecto, Instant.now());
        when(repositorioDesafio.buscarUltimoPendiente(usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO))
                .thenReturn(Optional.of(desafio));
        when(repositorioMetodos.buscarPorUsuarioYTipo(usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO))
                .thenReturn(Optional.empty());
        when(repositorioMetodos.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        SolicitudVerificacionContacto solicitud = new SolicitudVerificacionContacto(
                usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, codigoCorrecto);

        boolean resultado = service.verificarContacto(solicitud);

        assertThat(resultado).isTrue();
        verify(repositorioMetodos).guardar(any(MetodoDeSegundoFactor.class));
    }

    @Test
    @DisplayName("Verifica contacto cuando el método 2FA ya existía en el repositorio")
    void verificarContacto_MetodoExistente() {
        DesafioPorCodigo desafio = DesafioPorCodigo.generar(UUID.randomUUID(), usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, hashCorrecto, Instant.now());
        MetodoDeSegundoFactor existente = MetodoDeSegundoFactor.inscribir(UUID.randomUUID(), usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, null, Instant.now());
        when(repositorioDesafio.buscarUltimoPendiente(usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO))
                .thenReturn(Optional.of(desafio));
        when(repositorioMetodos.buscarPorUsuarioYTipo(usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO))
                .thenReturn(Optional.of(existente));
        when(repositorioMetodos.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        SolicitudVerificacionContacto solicitud = new SolicitudVerificacionContacto(
                usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, codigoCorrecto);

        boolean resultado = service.verificarContacto(solicitud);
        assertThat(resultado).isTrue();
    }

    @Test
    @DisplayName("Lanza CodigoDesafioInvalidoException si no hay desafío pendiente")
    void verificarContacto_SinDesafio() {
        when(repositorioDesafio.buscarUltimoPendiente(usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO))
                .thenReturn(Optional.empty());

        SolicitudVerificacionContacto solicitud = new SolicitudVerificacionContacto(
                usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, codigoCorrecto);

        assertThatThrownBy(() -> service.verificarContacto(solicitud))
                .isInstanceOf(CodigoDesafioInvalidoException.class);
    }

    @Test
    @DisplayName("Lanza DesafioExpiradoException si el desafío ya expiró")
    void verificarContacto_Expirado() {
        Instant pasado = Instant.now().minusSeconds(700);
        DesafioPorCodigo expirado = DesafioPorCodigo.reconstituir(UUID.randomUUID(), usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, hashCorrecto, 0, pasado, pasado.plusSeconds(300), null);
        when(repositorioDesafio.buscarUltimoPendiente(usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO))
                .thenReturn(Optional.of(expirado));

        SolicitudVerificacionContacto solicitud = new SolicitudVerificacionContacto(
                usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, codigoCorrecto);

        assertThatThrownBy(() -> service.verificarContacto(solicitud))
                .isInstanceOf(DesafioExpiradoException.class);
    }

    @Test
    @DisplayName("Lanza MaximoIntentosDesafioExcedidoException si ya había alcanzado el máximo de intentos")
    void verificarContacto_MaximoIntentosPrevio() {
        DesafioPorCodigo maxIntentos = DesafioPorCodigo.reconstituir(UUID.randomUUID(), usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, hashCorrecto, 3, Instant.now(), Instant.now().plusSeconds(600), null);
        when(repositorioDesafio.buscarUltimoPendiente(usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO))
                .thenReturn(Optional.of(maxIntentos));

        SolicitudVerificacionContacto solicitud = new SolicitudVerificacionContacto(
                usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, codigoCorrecto);

        assertThatThrownBy(() -> service.verificarContacto(solicitud))
                .isInstanceOf(MaximoIntentosDesafioExcedidoException.class);
    }

    @Test
    @DisplayName("Lanza MaximoIntentosDesafioExcedidoException al fallar el 3er intento")
    void verificarContacto_AlcanzaMaximoIntentosEnEsteFallo() {
        DesafioPorCodigo con2Intentos = DesafioPorCodigo.reconstituir(UUID.randomUUID(), usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, hashCorrecto, 2, Instant.now(), Instant.now().plusSeconds(600), null);
        when(repositorioDesafio.buscarUltimoPendiente(usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO))
                .thenReturn(Optional.of(con2Intentos));

        SolicitudVerificacionContacto solicitud = new SolicitudVerificacionContacto(
                usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, "000000");

        assertThatThrownBy(() -> service.verificarContacto(solicitud))
                .isInstanceOf(MaximoIntentosDesafioExcedidoException.class);
    }

    @Test
    @DisplayName("Lanza CodigoDesafioInvalidoException ante código erróneo")
    void verificarContacto_CodigoErroneo() {
        DesafioPorCodigo desafio = DesafioPorCodigo.generar(UUID.randomUUID(), usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, hashCorrecto, Instant.now());
        when(repositorioDesafio.buscarUltimoPendiente(usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO))
                .thenReturn(Optional.of(desafio));

        SolicitudVerificacionContacto solicitud = new SolicitudVerificacionContacto(
                usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, "000000");

        assertThatThrownBy(() -> service.verificarContacto(solicitud))
                .isInstanceOf(CodigoDesafioInvalidoException.class);
    }

    @Test
    @DisplayName("Verifica TOTP en el registro y sincroniza segundo_factor, rechazando codigos invalidos")
    void verificarTotpEnRegistro_ExitoYFallos() {
        pe.ayni.bank.identity.domain.port.out.GeneradorDeTotpPort generadorTotp = org.mockito.Mockito.mock(
                pe.ayni.bank.identity.domain.port.out.GeneradorDeTotpPort.class);
        pe.ayni.bank.identity.domain.port.out.RepositorioDeSegundoFactorPort segundosFactores = org.mockito.Mockito.mock(
                pe.ayni.bank.identity.domain.port.out.RepositorioDeSegundoFactorPort.class);
        pe.ayni.bank.identity.domain.port.out.RepositorioDeSolicitudesPort solicitudes = org.mockito.Mockito.mock(
                pe.ayni.bank.identity.domain.port.out.RepositorioDeSolicitudesPort.class);

        VerificarContactoRegistroService completo = new VerificarContactoRegistroService(
                repositorioDesafio, repositorioMetodos, java.time.Clock.systemUTC(),
                generadorTotp, segundosFactores, solicitudes);

        UUID solicitudId = UUID.randomUUID();
        when(solicitudes.titularDe(solicitudId)).thenReturn(Optional.of(usuarioId));

        // 1. Codigo con formato invalido (no 6 digitos)
        assertThatThrownBy(() -> completo.verificarContacto(
                new SolicitudVerificacionContacto(solicitudId, TipoDeSegundoFactor.APP_AUTENTICADORA, "abc")))
                .isInstanceOf(CodigoDesafioInvalidoException.class);

        // 2. Sin metodo inscrito en repositorio
        when(repositorioMetodos.buscarPorUsuarioYTipo(usuarioId, TipoDeSegundoFactor.APP_AUTENTICADORA))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> completo.verificarContacto(
                new SolicitudVerificacionContacto(solicitudId, TipoDeSegundoFactor.APP_AUTENTICADORA, "123456")))
                .isInstanceOf(CodigoDesafioInvalidoException.class);

        // 3. Metodo inscrito: fallo de verificacion TOTP y luego exito
        MetodoDeSegundoFactor inscrito = MetodoDeSegundoFactor.inscribir(
                UUID.randomUUID(), usuarioId, TipoDeSegundoFactor.APP_AUTENTICADORA,
                "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP", Instant.now());
        when(repositorioMetodos.buscarPorUsuarioYTipo(usuarioId, TipoDeSegundoFactor.APP_AUTENTICADORA))
                .thenReturn(Optional.of(inscrito));
        when(generadorTotp.verificar(any(), any(), any())).thenReturn(false, true);
        when(segundosFactores.buscarPorUsuario(usuarioId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> completo.verificarContacto(
                new SolicitudVerificacionContacto(solicitudId, TipoDeSegundoFactor.APP_AUTENTICADORA, "123456")))
                .isInstanceOf(CodigoDesafioInvalidoException.class);

        assertThat(completo.verificarContacto(
                new SolicitudVerificacionContacto(solicitudId, TipoDeSegundoFactor.APP_AUTENTICADORA, "123456")))
                .isTrue();
        verify(segundosFactores).guardar(any());
    }
}
