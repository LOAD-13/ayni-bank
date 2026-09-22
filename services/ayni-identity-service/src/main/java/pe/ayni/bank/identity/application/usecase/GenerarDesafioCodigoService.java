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
import pe.ayni.bank.identity.domain.model.Usuario;
import pe.ayni.bank.identity.domain.port.in.GenerarDesafioCodigoUseCase;
import pe.ayni.bank.identity.domain.port.out.NotificadorDeSegundoFactorPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeDesafioPorCodigoPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeUsuariosPort;

/**
 * Caso de uso de aplicación que genera un código OTP de 6 dígitos con hash SHA-256 (HU-22 / AYNI-128).
 */
@Service
public class GenerarDesafioCodigoService implements GenerarDesafioCodigoUseCase {

    private final RepositorioDeDesafioPorCodigoPort repositorioDesafio;
    private final RepositorioDeUsuariosPort repositorioUsuarios;
    private final NotificadorDeSegundoFactorPort notificador;
    private final SecureRandom aleatorio = new SecureRandom();

    public GenerarDesafioCodigoService(RepositorioDeDesafioPorCodigoPort repositorioDesafio,
                                       RepositorioDeUsuariosPort repositorioUsuarios,
                                       NotificadorDeSegundoFactorPort notificador) {
        this.repositorioDesafio = repositorioDesafio;
        this.repositorioUsuarios = repositorioUsuarios;
        this.notificador = notificador;
    }

    @Override
    @Transactional
    public ResultadoGeneracionDesafio generar(UUID usuarioId, TipoDeSegundoFactor tipoFactor) {
        String codigo6Digitos = String.format("%06d", aleatorio.nextInt(1_000_000));
        String hashSha256 = calcularHashSha256(usuarioId, codigo6Digitos);

        Instant ahora = Instant.now();

        // Invalidar desafíos pendientes anteriores para este usuario y tipo de factor
        repositorioDesafio.buscarUltimoPendiente(usuarioId, tipoFactor)
                .filter(d -> !d.estaVerificado() && !d.estaExpirado(ahora))
                .ifPresent(anterior -> repositorioDesafio.guardar(anterior.invalidar()));

        DesafioPorCodigo nuevoDesafio = DesafioPorCodigo.generar(
                UUID.randomUUID(), usuarioId, tipoFactor, hashSha256, ahora);

        DesafioPorCodigo guardado = repositorioDesafio.guardar(nuevoDesafio);
        despacharSiCorresponde(usuarioId, tipoFactor, codigo6Digitos);

        return new ResultadoGeneracionDesafio(guardado, codigo6Digitos);
    }

    /**
     * APP_AUTENTICADORA no se despacha: el codigo lo genera la app fuera de linea a partir
     * del secreto TOTP, no algo que este servicio produzca y deba entregar.
     */
    private void despacharSiCorresponde(UUID usuarioId, TipoDeSegundoFactor tipoFactor, String codigo) {
        if (tipoFactor == TipoDeSegundoFactor.APP_AUTENTICADORA) {
            return;
        }

        Usuario usuario = repositorioUsuarios.buscarPorId(usuarioId).orElseThrow();
        switch (tipoFactor) {
            case CORREO_ELECTRONICO -> notificador.enviarCodigoPorCorreo(usuario.correo(), codigo);
            case SMS -> notificador.enviarCodigoPorSms(usuario.celular(), codigo);
            default -> throw new IllegalStateException("Tipo de factor sin canal de despacho: " + tipoFactor);
        }
    }

    public static String calcularHashSha256(UUID usuarioId, String texto) {
        String textoConSalt = (usuarioId != null ? usuarioId.toString() : "") + ":" + texto;
        return calcularHashSha256(textoConSalt);
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
