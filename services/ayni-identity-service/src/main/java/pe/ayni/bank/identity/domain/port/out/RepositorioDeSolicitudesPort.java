package pe.ayni.bank.identity.domain.port.out;

import java.util.Optional;
import java.util.UUID;

import pe.ayni.bank.identity.domain.model.IdentidadDeclarada;

/**
 * Expedientes de onboarding.
 *
 * <p>El senuelo es parte del contrato del puerto y no un truco del adaptador: la
 * antienumeracion es una regla de negocio, y el dominio tiene que poder expresarla.
 * Ver ADR-0008.
 */
public interface RepositorioDeSolicitudesPort {

    /**
     * Abre el expediente real de un usuario que si se ha creado, con los datos de identidad
     * que declaro. Esos datos son el termino de comparacion del OCR en HU-02.
     */
    UUID abrirPara(UUID usuarioId, IdentidadDeclarada identidad);

    /**
     * Abre un expediente sin usuario, para responder a un correo ya registrado con algo
     * indistinguible de un registro correcto.
     *
     * <p>No recibe la identidad declarada a proposito. No se ha creado ningun usuario, asi
     * que no hay nada que verificar despues; conservar los datos personales de un intento
     * sobre una cuenta ajena seria almacenamiento sin finalidad.
     */
    UUID abrirSenuelo();

    /**
     * El usuario al que pertenece una solicitud.
     *
     * <p>Vacio tanto si la solicitud no existe como si es un senuelo, y las dos cosas se
     * tratan igual a proposito: un senuelo no tiene titular, asi que no hay nada que
     * aprobar ni cuenta que abrir. Ver ADR-0008.
     */
    Optional<UUID> titularDe(UUID solicitudId);

    /** Lleva la solicitud a APROBADA, que es lo que dispara la apertura de la cuenta. */
    void marcarAprobada(UUID solicitudId);

    /**
     * El nombre de pila que declaro el titular, para poder saludarle.
     *
     * <p>Solo el primero de los nombres, no la cadena completa. «Listo, Ana Lucia Beatriz»
     * suena a carta del banco; «Listo, Ana» suena a alguien hablandole a una persona.
     */
    Optional<String> nombreDePilaDe(UUID usuarioId);
}
