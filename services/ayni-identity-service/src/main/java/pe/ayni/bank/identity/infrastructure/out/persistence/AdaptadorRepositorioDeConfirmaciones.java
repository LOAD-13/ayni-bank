package pe.ayni.bank.identity.infrastructure.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import pe.ayni.bank.identity.domain.model.ConfirmacionDeOperacion;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeConfirmacionesPort;

/** Implementa {@link RepositorioDeConfirmacionesPort} sobre JPA. */
@Repository
public class AdaptadorRepositorioDeConfirmaciones implements RepositorioDeConfirmacionesPort {

    private final ConfirmacionJpaRepository repositorio;

    AdaptadorRepositorioDeConfirmaciones(ConfirmacionJpaRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public void guardar(ConfirmacionDeOperacion c) {
        repositorio.save(new ConfirmacionDeOperacionEntity(c.id(), c.usuarioId(), c.huella(), c.metodo().name(),
                c.desafioCodigoId(), (short) c.intentosFallidos(), c.creadaEn(), c.expiraEn(), c.usadaEn()));
    }

    @Override
    public Optional<ConfirmacionDeOperacion> buscar(UUID id) {
        return repositorio.findById(id).map(f -> ConfirmacionDeOperacion.reconstituir(f.getId(), f.getUsuarioId(),
                f.getHuella(), TipoDeSegundoFactor.valueOf(f.getMetodo()), f.getDesafioCodigoId(),
                f.getIntentosFallidos(), f.getCreadaEn(), f.getExpiraEn(), f.getUsadaEn()));
    }
}

interface ConfirmacionJpaRepository extends JpaRepository<ConfirmacionDeOperacionEntity, UUID> {
}
