package pe.ayni.bank.identity.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pe.ayni.bank.identity.domain.model.DesafioPorCodigo;
import pe.ayni.bank.identity.domain.model.ResultadoGeneracionDesafio;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeDesafioPorCodigoPort;

@ExtendWith(MockitoExtension.class)
class GenerarDesafioCodigoServiceTest {

    @Mock
    private RepositorioDeDesafioPorCodigoPort repositorioDesafio;

    private GenerarDesafioCodigoService service;

    @BeforeEach
    void setUp() {
        service = new GenerarDesafioCodigoService(repositorioDesafio);
    }

    @Test
    @DisplayName("Genera correctamente un código OTP de 6 dígitos con hash SHA-256")
    void generar_CodigoOTPExitoso() {
        UUID usuarioId = UUID.randomUUID();
        when(repositorioDesafio.guardar(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ResultadoGeneracionDesafio resultado = service.generar(usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO);

        assertThat(resultado.codigo6Digitos()).matches("^\\d{6}$");
        assertThat(resultado.desafio()).isNotNull();
        assertThat(resultado.desafio().usuarioId()).isEqualTo(usuarioId);
        assertThat(resultado.desafio().tipoFactor()).isEqualTo(TipoDeSegundoFactor.CORREO_ELECTRONICO);
        assertThat(resultado.desafio().hashCodigo()).hasSize(64); // SHA-256 hex string

        verify(repositorioDesafio).guardar(any(DesafioPorCodigo.class));
    }
}
