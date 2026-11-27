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
        Instant ahora = reloj.instant();
        TitularDeLaSolicitud titular = TitularDeLaSolicitud.de(usuarioId, solicitudes, usuarios);

        // OWASP A04 / ADR-0008 (antienumeración): una solicitud señuelo no tiene titular en la base;
        // se responde con un método efímero indistinguible en vez de fallar con 500 por la FK.
        if (titular.senuelo()) {
            return MetodoDeSegundoFactor.inscribir(UUID.randomUUID(), usuarioId, tipo, secretoPara(tipo), ahora);
        }
        MetodoDeSegundoFactor metodo = buscarOInscribir(titular.usuarioId(), tipo, ahora);
        inscribirSegundoFactorTotp(titular.usuarioId(), metodo, ahora);
        enviarCodigoDeConfirmacion(titular.usuarioId(), metodo);
        return metodo;
    }

    /** Solo la App Autenticadora necesita un secreto TOTP; los demás métodos no guardan ninguno. */
    private String secretoPara(TipoDeSegundoFactor tipo) {
        return tipo == TipoDeSegundoFactor.APP_AUTENTICADORA ? generadorTotp.generarSecreto().valor() : null;
    }

    private MetodoDeSegundoFactor buscarOInscribir(UUID usuarioId, TipoDeSegundoFactor tipo, Instant ahora) {
        return repositorioMetodos.buscarPorUsuarioYTipo(usuarioId, tipo)
                .orElseGet(() -> repositorioMetodos.guardar(MetodoDeSegundoFactor.inscribir(
                        UUID.randomUUID(), usuarioId, tipo, secretoPara(tipo), ahora)));
    }

    /** La App Autenticadora deja además su {@link SegundoFactor}, una sola vez por usuario. */
    private void inscribirSegundoFactorTotp(UUID usuarioId, MetodoDeSegundoFactor metodo, Instant ahora) {
        if (metodo.tipo() != TipoDeSegundoFactor.APP_AUTENTICADORA || segundosFactores == null
                || metodo.secreto() == null || segundosFactores.buscarPorUsuario(usuarioId).isPresent()) {
            return;
        }
        segundosFactores.guardar(SegundoFactor.inscribir(usuarioId, new SecretoTotp(metodo.secreto()), ahora));
    }

    /** El correo sin confirmar recibe su código en el momento de elegirlo. */
    private void enviarCodigoDeConfirmacion(UUID usuarioId, MetodoDeSegundoFactor metodo) {
        if (metodo.tipo() != TipoDeSegundoFactor.CORREO_ELECTRONICO || metodo.estaConfirmado()
                || generarDesafioCodigo == null || usuarios == null
                || usuarios.buscarPorId(usuarioId).isEmpty()) {
            return;
        }
        generarDesafioCodigo.generar(usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO);
    }
}


