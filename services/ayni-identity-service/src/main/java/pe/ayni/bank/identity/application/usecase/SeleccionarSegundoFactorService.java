package pe.ayni.bank.identity.application.usecase;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.identity.domain.model.MetodoDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;
import pe.ayni.bank.identity.domain.port.in.SeleccionarSegundoFactorUseCase;
import pe.ayni.bank.identity.domain.port.out.GeneradorDeTotpPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeMetodoSegundoFactorPort;

/**
 * Servicio de aplicación para seleccionar e inscribir un método de segundo factor.
 */
@Service
@Transactional
public class SeleccionarSegundoFactorService implements SeleccionarSegundoFactorUseCase {

    private final RepositorioDeMetodoSegundoFactorPort repositorioMetodos;
    private final GeneradorDeTotpPort generadorTotp;

    public SeleccionarSegundoFactorService(RepositorioDeMetodoSegundoFactorPort repositorioMetodos,
                                          GeneradorDeTotpPort generadorTotp) {
        this.repositorioMetodos = Objects.requireNonNull(repositorioMetodos, "repositorioMetodos no puede ser nulo");
        this.generadorTotp = Objects.requireNonNull(generadorTotp, "generadorTotp no puede ser nulo");
    }

    @Override
    public MetodoDeSegundoFactor seleccionarMetodo(UUID usuarioId, TipoDeSegundoFactor tipo) {
        Objects.requireNonNull(usuarioId, "usuarioId no puede ser nulo");
        Objects.requireNonNull(tipo, "tipo no puede ser nulo");

        return repositorioMetodos.buscarPorUsuarioYTipo(usuarioId, tipo)
                .orElseGet(() -> {
                    String secreto = null;
                    if (tipo == TipoDeSegundoFactor.APP_AUTENTICADORA) {
                        secreto = generadorTotp.generarSecreto().valor();
                    }
                    MetodoDeSegundoFactor nuevo = MetodoDeSegundoFactor.inscribir(
                            UUID.randomUUID(), usuarioId, tipo, secreto, Instant.now()
                    );
                    return repositorioMetodos.guardar(nuevo);
                });
    }
}
