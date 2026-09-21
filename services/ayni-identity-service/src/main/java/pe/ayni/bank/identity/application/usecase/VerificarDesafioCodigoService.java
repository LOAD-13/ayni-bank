package pe.ayni.bank.identity.application.usecase;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.identity.domain.model.CodigoDesafioInvalidoException;
import pe.ayni.bank.identity.domain.model.DesafioExpiradoException;
import pe.ayni.bank.identity.domain.model.DesafioPorCodigo;
import pe.ayni.bank.identity.domain.model.MaximoIntentosDesafioExcedidoException;
import pe.ayni.bank.identity.domain.port.in.VerificarDesafioCodigoUseCase;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeDesafioPorCodigoPort;

/**
 * Caso de uso de aplicación para verificar un código OTP con límite de 3 intentos (HU-22 / AYNI-128).
 */
@Service
public class VerificarDesafioCodigoService implements VerificarDesafioCodigoUseCase {

    private final RepositorioDeDesafioPorCodigoPort repositorioDesafio;

    public VerificarDesafioCodigoService(RepositorioDeDesafioPorCodigoPort repositorioDesafio) {
        this.repositorioDesafio = repositorioDesafio;
    }

    @Override
    @Transactional
    public boolean verificar(UUID desafioId, String codigoIngresado) {
        DesafioPorCodigo desafio = repositorioDesafio.buscarPorId(desafioId)
                .orElseThrow(CodigoDesafioInvalidoException::new);

        Instant ahora = Instant.now();

        if (desafio.estaExpirado(ahora)) {
            throw new DesafioExpiradoException();
        }

        if (desafio.alcanzoMaximoIntentos()) {
            throw new MaximoIntentosDesafioExcedidoException();
        }

        String hashIngresado = GenerarDesafioCodigoService.calcularHashSha256(codigoIngresado);

        boolean esValido = MessageDigest.isEqual(
                hashIngresado.getBytes(StandardCharsets.UTF_8),
                desafio.hashCodigo().getBytes(StandardCharsets.UTF_8));

        if (!esValido) {
            DesafioPorCodigo actualizado = desafio.registrarIntentoFallido();
            repositorioDesafio.guardar(actualizado);
            if (actualizado.alcanzoMaximoIntentos()) {
                throw new MaximoIntentosDesafioExcedidoException();
            }
            throw new CodigoDesafioInvalidoException();
        }

        DesafioPorCodigo verificado = desafio.marcarVerificado(ahora);
        repositorioDesafio.guardar(verificado);
        return true;
    }
}
