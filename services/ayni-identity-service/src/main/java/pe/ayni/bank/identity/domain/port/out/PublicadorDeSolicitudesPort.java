package pe.ayni.bank.identity.domain.port.out;

import java.util.UUID;

/**
 * Anuncia al resto del sistema que una solicitud de onboarding cambio de estado.
 *
 * <p>El dominio no sabe que hay un RabbitMQ detras. Sabe que cuando una solicitud se
 * aprueba, alguien tiene que enterarse; quien y por que medio es cosa del adaptador.
 */
public interface PublicadorDeSolicitudesPort {

    void anunciarAprobacion(UUID solicitudId, UUID usuarioId);
}
