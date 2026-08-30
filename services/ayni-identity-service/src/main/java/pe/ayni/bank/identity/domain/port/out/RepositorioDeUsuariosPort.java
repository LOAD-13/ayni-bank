package pe.ayni.bank.identity.domain.port.out;

import java.util.Optional;

import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.model.Usuario;

/** Persistencia de usuarios. El dominio declara lo que necesita; el adaptador decide como. */
public interface RepositorioDeUsuariosPort {

    boolean existeCorreo(CorreoElectronico correo);

    Optional<Usuario> buscarPorCorreo(CorreoElectronico correo);

    Usuario guardar(Usuario usuario);
}
