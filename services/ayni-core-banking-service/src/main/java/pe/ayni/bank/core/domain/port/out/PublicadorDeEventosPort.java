package pe.ayni.bank.core.domain.port.out;

import java.util.UUID;

/**
 * Escritura de eventos en la bandeja de salida.
 *
 * <p>El nombre dice «publicador» pero <strong>no publica nada</strong>: escribe el evento
 * en la misma base y en la misma transaccion que el hecho que lo origina. Enviarlo a
 * RabbitMQ es tarea de un proceso aparte que lee esa tabla despues.
 *
 * <p>La distincion es la razon de ser del patron. Publicar de verdad desde dentro de la
 * transaccion tiene un fallo sin arreglo: si el envio sale bien y la transaccion hace
 * rollback, se anuncio una cuenta que no existe; si la transaccion confirma y el envio
 * falla, la cuenta existe y nadie se entera. No hay orden de las dos operaciones que evite
 * ambas cosas, porque son dos sistemas sin una transaccion comun. Ver ADR-0003.
 */
public interface PublicadorDeEventosPort {

    void registrar(String agregadoTipo, UUID agregadoId, String tipoDeEvento, Object carga);
}
