package pe.ayni.bank.identity.infrastructure.out.persistence;

import java.time.Clock;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.model.Usuario;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeUsuariosPort;

/** Implementa {@link RepositorioDeUsuariosPort} sobre JPA. */
@Repository
public class AdaptadorRepositorioDeUsuarios implements RepositorioDeUsuariosPort {

    private final UsuarioJpaRepository repositorio;
    private final Clock reloj;

    AdaptadorRepositorioDeUsuarios(UsuarioJpaRepository repositorio, Clock reloj) {
        this.repositorio = repositorio;
        this.reloj = reloj;
    }

    @Override
    public boolean existeCorreo(CorreoElectronico correo) {
        return repositorio.existsByCorreo(correo.valor());
    }

    @Override
    public Optional<Usuario> buscarPorCorreo(CorreoElectronico correo) {
        return repositorio.findByCorreo(correo.valor()).map(MapeadorDeUsuario::aDominio);
    }

    @Override
    public Usuario guardar(Usuario usuario) {
        UsuarioEntity guardada =
                repositorio.save(MapeadorDeUsuario.aEntidad(usuario, reloj.instant()));
        return MapeadorDeUsuario.aDominio(guardada);
    }
}
