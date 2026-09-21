package pe.ayni.bank.identity.application.usecase;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.identity.domain.model.DesafioPorCodigo;
import pe.ayni.bank.identity.domain.model.ResultadoGeneracionDesafio;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;
import pe.ayni.bank.identity.domain.port.in.GenerarDesafioCodigoUseCase;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeDesafioPorCodigoPort;

/**
 * Caso de uso de aplicación que genera un código OTP de 6 dígitos con hash SHA-256 (HU-22 / AYNI-128).
 */
@Service
public class GenerarDesafioCodigoService implements GenerarDesafioCodigoUseCase {

    private final RepositorioDeDesafioPorCodigoPort repositorioDesafio;
    private final SecureRandom aleatorio = new SecureRandom();

    public GenerarDesafioCodigoService(RepositorioDeDesafioPorCodigoPort repositorioDesafio) {
        this.repositorioDesafio = repositorioDesafio;
    }

    @Override
    @Transactional
    public ResultadoGeneracionDesafio generar(UUID usuarioId, TipoDeSegundoFactor tipoFactor) {
        String codigo6Digitos = String.format("%06d", aleatorio.nextInt(1_000_000));
        String hashSha256 = calcularHashSha256(codigo6Digitos);

        DesafioPorCodigo nuevoDesafio = DesafioPorCodigo.generar(
                UUID.randomUUID(), usuarioId, tipoFactor, hashSha256, Instant.now());

        DesafioPorCodigo guardado = repositorioDesafio.guardar(nuevoDesafio);
        return new ResultadoGeneracionDesafio(guardado, codigo6Digitos);
    }

    public static String calcularHashSha256(String texto) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("No se pudo calcular hash SHA-256", e);
        }
    }
}
