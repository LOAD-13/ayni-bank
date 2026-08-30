package pe.ayni.bank.identity.domain.port.out;

import java.util.Optional;
import java.util.UUID;

import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.model.Usuario;

/** Persistencia de usuarios. El dominio declara lo que necesita; el adaptador decide como. */
public interface RepositorioDeUsuariosPort {

    boolean existeCorreo(CorreoElectronico correo);

    Optional<Usuario> buscarPorCorreo(CorreoElectronico correo);

    /**
     * Lo usa el ingreso una vez pasada la contrasena, cuando ya se sabe de quien se habla y
     * lo que hay a mano es el identificador y no el correo.
     */
    Optional<Usuario> buscarPorId(UUID id);

    Usuario guardar(Usuario usuario);
}
