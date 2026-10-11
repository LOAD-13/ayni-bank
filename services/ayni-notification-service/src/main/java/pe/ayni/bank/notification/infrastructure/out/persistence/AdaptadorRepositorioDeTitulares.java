package pe.ayni.bank.notification.infrastructure.out.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import pe.ayni.bank.notification.domain.model.Titular;
import pe.ayni.bank.notification.domain.port.out.RepositorioDeTitularesPort;

@Component
public class AdaptadorRepositorioDeTitulares implements RepositorioDeTitularesPort {

    private final TitularJpaRepository repositorio;

    AdaptadorRepositorioDeTitulares(TitularJpaRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public void guardar(Titular titular, Instant actualizadoEn) {
        repositorio.guardar(titular.usuarioId(), titular.correo(), titular.nombrePila(), actualizadoEn);
    }

    @Override
    public Optional<Titular> buscar(UUID usuarioId) {
        return repositorio.findById(usuarioId)
                .map(t -> new Titular(t.getUsuarioId(), t.getCorreo(), t.getNombrePila()));
    }
}
