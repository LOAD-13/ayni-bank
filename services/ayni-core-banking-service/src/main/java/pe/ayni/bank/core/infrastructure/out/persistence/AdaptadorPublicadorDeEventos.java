package pe.ayni.bank.core.infrastructure.out.persistence;

import java.time.Clock;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Repository;

import pe.ayni.bank.core.domain.port.out.PublicadorDeEventosPort;

/**
 * Escribe el evento en la bandeja de salida. No lo envia: de eso se encarga
 * {@code PublicadorDeOutbox} despues del COMMIT. Ver ADR-0003.
 */
@Repository
public class AdaptadorPublicadorDeEventos implements PublicadorDeEventosPort {

    private final OutboxJpaRepository outbox;
    private final ObjectMapper mapeador;
    private final Clock reloj;

    AdaptadorPublicadorDeEventos(OutboxJpaRepository outbox, ObjectMapper mapeador,
                                 Clock reloj) {
        this.outbox = outbox;
        this.mapeador = mapeador;
        this.reloj = reloj;
    }

    @Override
    public void registrar(String agregadoTipo, UUID agregadoId, String tipoDeEvento,
                          Object carga) {
        try {
            outbox.save(new OutboxEntity(UUID.randomUUID(), agregadoTipo, agregadoId,
                    tipoDeEvento, mapeador.writeValueAsString(carga), reloj.instant()));
        } catch (JsonProcessingException e) {
            // Se propaga para que la transaccion entera falle. Guardar la cuenta sin su
            // evento dejaria el sistema en el estado incoherente que el outbox evita.
            throw new IllegalStateException(
                    "No se pudo serializar el evento " + tipoDeEvento, e);
        }
    }
}
