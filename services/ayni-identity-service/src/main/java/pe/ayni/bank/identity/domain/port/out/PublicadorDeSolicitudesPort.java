package pe.ayni.bank.identity.domain.port.out;

import java.util.UUID;

import pe.ayni.bank.identity.domain.model.CorreoElectronico;

/**
 * Anuncia al resto del sistema que una solicitud de onboarding cambio de estado.
 *
 * <p>El dominio no sabe que hay un RabbitMQ detras. Sabe que cuando una solicitud se
 * aprueba, alguien tiene que enterarse; quien y por que medio es cosa del adaptador.
 */
public interface PublicadorDeSolicitudesPort {

    void anunciarAprobacion(UUID solicitudId, UUID usuarioId);

    /**
     * HU-13: el titular ya puede operar. notification-service guarda su correo y su nombre de
     * pila en una proyeccion local (ADR-0004) para avisarle de sus movimientos sin depender
     * de identity en caliente.
     */
    void anunciarTitularActivado(UUID usuarioId, CorreoElectronico correo, String nombrePila);
}
