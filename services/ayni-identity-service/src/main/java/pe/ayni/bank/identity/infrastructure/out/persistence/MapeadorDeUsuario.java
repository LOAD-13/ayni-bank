package pe.ayni.bank.identity.infrastructure.out.persistence;

import java.time.Instant;

import pe.ayni.bank.identity.domain.model.Celular;
import pe.ayni.bank.identity.domain.model.Consentimiento;
import pe.ayni.bank.identity.domain.model.ContrasenaCifrada;
import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.model.EstadoUsuario;
import pe.ayni.bank.identity.domain.model.Usuario;

/**
 * Traduce entre el agregado de dominio y su fila.
 *
 * <p>Es la frontera: a un lado, objetos de valor que se validan solos; al otro, cadenas y
 * fechas. Al reconstruir se pasa por los constructores del dominio, de modo que una fila
 * corrupta —un estado que no existe, un correo mal formado— falla al leerse en lugar de
 * propagarse por la aplicacion como un objeto invalido.
 */
final class MapeadorDeUsuario {

    private MapeadorDeUsuario() {
        throw new AssertionError("Clase de utilidad; no se instancia.");
    }

    static UsuarioEntity aEntidad(Usuario usuario, Instant momento) {
        return new UsuarioEntity(
                usuario.id(),
                usuario.correo().valor(),
                usuario.celular().valor(),
                usuario.contrasena().valor(),
                usuario.estado().name(),
                usuario.consentimiento().otorgadoEn(),
                usuario.consentimiento().versionDeLosTerminos(),
                usuario.registradoEn(),
                momento);
    }

    static Usuario aDominio(UsuarioEntity entidad) {
        return Usuario.reconstituir(
                entidad.getId(),
                new CorreoElectronico(entidad.getCorreo()),
                new Celular(entidad.getCelular()),
                new ContrasenaCifrada(entidad.getContrasenaHash()),
                EstadoUsuario.valueOf(entidad.getEstado()),
                new Consentimiento(entidad.getConsentimientoEn(), entidad.getTerminosVersion()),
                entidad.getRegistradoEn());
    }
}
