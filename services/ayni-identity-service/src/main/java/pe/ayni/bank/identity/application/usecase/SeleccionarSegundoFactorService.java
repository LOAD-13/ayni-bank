package pe.ayni.bank.identity.application.usecase;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.identity.domain.model.MetodoDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.SecretoTotp;
import pe.ayni.bank.identity.domain.model.SegundoFactor;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;
import pe.ayni.bank.identity.domain.port.in.GenerarDesafioCodigoUseCase;
import pe.ayni.bank.identity.domain.port.in.SeleccionarSegundoFactorUseCase;
import pe.ayni.bank.identity.domain.port.out.GeneradorDeTotpPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeMetodoSegundoFactorPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSegundoFactorPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSolicitudesPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeUsuariosPort;

/**
 * Servicio de aplicación para seleccionar e inscribir un método de segundo factor (HU-22 / AYNI-127).
 */
@Service
@Transactional
public class SeleccionarSegundoFactorService implements SeleccionarSegundoFactorUseCase {

    private final RepositorioDeMetodoSegundoFactorPort repositorioMetodos;
    private final GeneradorDeTotpPort generadorTotp;
    private final Clock reloj;
    private final RepositorioDeSolicitudesPort solicitudes;
    private final RepositorioDeSegundoFactorPort segundosFactores;
    private final RepositorioDeUsuariosPort usuarios;
    private final GenerarDesafioCodigoUseCase generarDesafioCodigo;

    public SeleccionarSegundoFactorService(RepositorioDeMetodoSegundoFactorPort repositorioMetodos,
                                          GeneradorDeTotpPort generadorTotp) {
        this(repositorioMetodos, generadorTotp, Clock.systemUTC(), null, null, null, null);
    }

    public SeleccionarSegundoFactorService(RepositorioDeMetodoSegundoFactorPort repositorioMetodos,
                                          GeneradorDeTotpPort generadorTotp,
                                          Clock reloj) {
        this(repositorioMetodos, generadorTotp, reloj, null, null, null, null);
    }

    @Autowired
    public SeleccionarSegundoFactorService(RepositorioDeMetodoSegundoFactorPort repositorioMetodos,
                                          GeneradorDeTotpPort generadorTotp,
                                          Clock reloj,
                                          @Autowired(required = false) RepositorioDeSolicitudesPort solicitudes,
                                          @Autowired(required = false) RepositorioDeSegundoFactorPort segundosFactores,
                                          @Autowired(required = false) RepositorioDeUsuariosPort usuarios,
                                          @Autowired(required = false) GenerarDesafioCodigoUseCase generarDesafioCodigo) {
        this.repositorioMetodos = Objects.requireNonNull(repositorioMetodos, "repositorioMetodos no puede ser nulo");
        this.generadorTotp = Objects.requireNonNull(generadorTotp, "generadorTotp no puede ser nulo");
        this.reloj = Objects.requireNonNull(reloj, "reloj no puede ser nulo");
        this.solicitudes = solicitudes;
        this.segundosFactores = segundosFactores;
        this.usuarios = usuarios;
        this.generarDesafioCodigo = generarDesafioCodigo;
    }

    @Override
    public MetodoDeSegundoFactor seleccionarMetodo(UUID usuarioId, TipoDeSegundoFactor tipo) {
        Objects.requireNonNull(usuarioId, "usuarioId no puede ser nulo");
        Objects.requireNonNull(tipo, "tipo no puede ser nulo");

        if (tipo == TipoDeSegundoFactor.SMS) {
            throw new IllegalArgumentException(
                    "El segundo factor por SMS estará disponible próximamente. "
                            + "Elige App Autenticadora o Correo Electrónico.");
        }

        java.util.Optional<UUID> titularDeSolicitud = solicitudes != null
                ? solicitudes.titularDe(usuarioId)
                : java.util.Optional.empty();
        UUID usuarioIdReal = titularDeSolicitud.orElse(usuarioId);
        Instant ahora = reloj.instant();

        // OWASP A04 / ADR-0008 (Antienumeración): una solicitud señuelo (correo ya registrado)
        // no tiene titular en BD. Devolver respuesta efímera indistinguible en vez de fallar con 500 por FK.
        if (solicitudes != null && titularDeSolicitud.isEmpty()
                && usuarios != null && usuarios.buscarPorId(usuarioIdReal).isEmpty()) {
            String secretoSenuelo = tipo == TipoDeSegundoFactor.APP_AUTENTICADORA
                    ? generadorTotp.generarSecreto().valor()
                    : null;
            return MetodoDeSegundoFactor.inscribir(
                    UUID.randomUUID(), usuarioId, tipo, secretoSenuelo, ahora);
        }

        MetodoDeSegundoFactor metodo = repositorioMetodos.buscarPorUsuarioYTipo(usuarioIdReal, tipo)
                .orElseGet(() -> {
                    String secreto = null;
                    if (tipo == TipoDeSegundoFactor.APP_AUTENTICADORA) {
                        secreto = generadorTotp.generarSecreto().valor();
                    }
                    MetodoDeSegundoFactor nuevo = MetodoDeSegundoFactor.inscribir(
                            UUID.randomUUID(), usuarioIdReal, tipo, secreto, ahora
                    );
                    return repositorioMetodos.guardar(nuevo);
                });

        if (tipo == TipoDeSegundoFactor.APP_AUTENTICADORA && segundosFactores != null && metodo.secreto() != null) {
            if (segundosFactores.buscarPorUsuario(usuarioIdReal).isEmpty()) {
                segundosFactores.guardar(SegundoFactor.inscribir(
                        usuarioIdReal, new SecretoTotp(metodo.secreto()), ahora));
            }
        }

        if (tipo == TipoDeSegundoFactor.CORREO_ELECTRONICO
                && !metodo.estaConfirmado()
                && generarDesafioCodigo != null
                && usuarios != null
                && usuarios.buscarPorId(usuarioIdReal).isPresent()) {
            generarDesafioCodigo.generar(usuarioIdReal, TipoDeSegundoFactor.CORREO_ELECTRONICO);
        }

        return metodo;
    }
}


