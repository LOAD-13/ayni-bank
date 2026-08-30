package pe.ayni.bank.identity.domain.port.out;

import java.util.UUID;

/**
 * Expedientes de onboarding.
 *
 * <p>El senuelo es parte del contrato del puerto y no un truco del adaptador: la
 * antienumeracion es una regla de negocio, y el dominio tiene que poder expresarla.
 * Ver ADR-0008.
 */
public interface RepositorioDeSolicitudesPort {

    /** Abre el expediente real de un usuario que si se ha creado. */
    UUID abrirPara(UUID usuarioId);

    /**
     * Abre un expediente sin usuario, para responder a un correo ya registrado con algo
     * indistinguible de un registro correcto.
     */
    UUID abrirSenuelo();
}
