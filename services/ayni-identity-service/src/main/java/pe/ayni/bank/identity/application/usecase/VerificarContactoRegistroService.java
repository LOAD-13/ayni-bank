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
import pe.ayni.bank.identity.domain.model.MetodoDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.SolicitudVerificacionContacto;
import pe.ayni.bank.identity.domain.port.in.VerificarContactoRegistroUseCase;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeDesafioPorCodigoPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeMetodoSegundoFactorPort;

/**
 * Servicio de aplicación para verificar el código OTP del contacto inicial durante el registro (HU-22 / AYNI-130).
 */
@Service
public class VerificarContactoRegistroService implements VerificarContactoRegistroUseCase {

    private final RepositorioDeDesafioPorCodigoPort repositorioDesafio;
    private final RepositorioDeMetodoSegundoFactorPort repositorioMetodos;

    public VerificarContactoRegistroService(RepositorioDeDesafioPorCodigoPort repositorioDesafio,
                                             RepositorioDeMetodoSegundoFactorPort repositorioMetodos) {
        this.repositorioDesafio = repositorioDesafio;
        this.repositorioMetodos = repositorioMetodos;
    }

    @Override
    @Transactional
    public boolean verificarContacto(SolicitudVerificacionContacto solicitud) {
        DesafioPorCodigo desafio = repositorioDesafio.buscarUltimoPendiente(solicitud.usuarioId(), solicitud.tipoContacto())
                .filter(d -> !d.estaVerificado())
                .orElseThrow(CodigoDesafioInvalidoException::new);

        Instant ahora = Instant.now();

        if (desafio.estaExpirado(ahora)) {
            throw new DesafioExpiradoException();
        }

        if (desafio.alcanzoMaximoIntentos()) {
            throw new MaximoIntentosDesafioExcedidoException();
        }

        String hashIngresado = GenerarDesafioCodigoService.calcularHashSha256(solicitud.usuarioId(), solicitud.codigo());

        boolean coincide = MessageDigest.isEqual(
                hashIngresado.getBytes(StandardCharsets.UTF_8),
                desafio.hashCodigo().getBytes(StandardCharsets.UTF_8));

        if (!coincide) {
            DesafioPorCodigo fallido = desafio.registrarIntentoFallido();
            repositorioDesafio.guardar(fallido);
            if (fallido.alcanzoMaximoIntentos()) {
                throw new MaximoIntentosDesafioExcedidoException();
            }
            throw new CodigoDesafioInvalidoException();
        }

        repositorioDesafio.guardar(desafio.marcarVerificado(ahora));

        MetodoDeSegundoFactor metodo = repositorioMetodos.buscarPorUsuarioYTipo(solicitud.usuarioId(), solicitud.tipoContacto())
                .orElseGet(() -> MetodoDeSegundoFactor.inscribir(
                        UUID.randomUUID(), solicitud.usuarioId(), solicitud.tipoContacto(), null, ahora));

        repositorioMetodos.guardar(metodo.confirmar(ahora));
        return true;
    }
}
