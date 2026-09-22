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
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeDesafioPorCodigoPort;

@ExtendWith(MockitoExtension.class)
class VerificarDesafioCodigoServiceTest {

    @Mock
    private RepositorioDeDesafioPorCodigoPort repositorioDesafio;

    private VerificarDesafioCodigoService service;

    private final UUID desafioId = UUID.randomUUID();
    private final UUID usuarioId = UUID.randomUUID();
    private final String codigoCorrecto = "123456";
    private String hashCorrecto;

    @BeforeEach
    void setUp() {
        service = new VerificarDesafioCodigoService(repositorioDesafio);
        hashCorrecto = GenerarDesafioCodigoService.calcularHashSha256(codigoCorrecto);
    }

    @Test
    @DisplayName("Verifica exitosamente con el código correcto")
    void verificar_CodigoCorrecto_Exito() {
        DesafioPorCodigo desafio = DesafioPorCodigo.generar(desafioId, usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, hashCorrecto, Instant.now());
        when(repositorioDesafio.buscarPorId(desafioId)).thenReturn(Optional.of(desafio));
        when(repositorioDesafio.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        boolean resultado = service.verificar(desafioId, codigoCorrecto);

        assertThat(resultado).isTrue();
        verify(repositorioDesafio).guardar(any());
    }

    @Test
    @DisplayName("Lanza CodigoDesafioInvalidoException ante un código incorrecto")
    void verificar_CodigoIncorrecto_LanzaExcepcion() {
        DesafioPorCodigo desafio = DesafioPorCodigo.generar(desafioId, usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, hashCorrecto, Instant.now());
        when(repositorioDesafio.buscarPorId(desafioId)).thenReturn(Optional.of(desafio));
        when(repositorioDesafio.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.verificar(desafioId, "654321"))
                .isInstanceOf(CodigoDesafioInvalidoException.class);
    }

    @Test
    @DisplayName("Lanza MaximoIntentosDesafioExcedidoException al superar 3 intentos fallidos")
    void verificar_ExcedeMaximoIntentos_LanzaExcepcion() {
        DesafioPorCodigo desafio3Intentos = DesafioPorCodigo.reconstituir(
                desafioId, usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, hashCorrecto, 3,
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(500), null);

        when(repositorioDesafio.buscarPorId(desafioId)).thenReturn(Optional.of(desafio3Intentos));

        assertThatThrownBy(() -> service.verificar(desafioId, codigoCorrecto))
                .isInstanceOf(MaximoIntentosDesafioExcedidoException.class);
    }

    @Test
    @DisplayName("Lanza DesafioExpiradoException si han pasado más de 10 minutos")
    void verificar_DesafioExpirado_LanzaExcepcion() {
        Instant hace15Minutos = Instant.now().minusSeconds(15 * 60);
        DesafioPorCodigo desafioExpirado = DesafioPorCodigo.reconstituir(
                desafioId, usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, hashCorrecto, 0,
                hace15Minutos, hace15Minutos.plus(DesafioPorCodigo.VIGENCIA), null);

        when(repositorioDesafio.buscarPorId(desafioId)).thenReturn(Optional.of(desafioExpirado));

        assertThatThrownBy(() -> service.verificar(desafioId, codigoCorrecto))
                .isInstanceOf(DesafioExpiradoException.class);
    }

    @Test
    @DisplayName("Lanza CodigoDesafioInvalidoException si no existe el desafioId")
    void verificar_DesafioNoExistente() {
        when(repositorioDesafio.buscarPorId(desafioId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verificar(desafioId, codigoCorrecto))
                .isInstanceOf(CodigoDesafioInvalidoException.class);
    }

    @Test
    @DisplayName("Lanza MaximoIntentosDesafioExcedidoException si este fallo es el 3er intento")
    void verificar_AlcanzaMaximoIntentosEnEsteFallo() {
        DesafioPorCodigo desafio2Intentos = DesafioPorCodigo.reconstituir(
                desafioId, usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, hashCorrecto, 2,
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(500), null);

        when(repositorioDesafio.buscarPorId(desafioId)).thenReturn(Optional.of(desafio2Intentos));
        when(repositorioDesafio.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.verificar(desafioId, "000000"))
                .isInstanceOf(MaximoIntentosDesafioExcedidoException.class);
    }
}
