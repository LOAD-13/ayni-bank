package pe.ayni.bank.core.infrastructure.in.messaging;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.CuentaDuplicadaException;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.port.in.AbrirCuentaDeAhorroUseCase;
import pe.ayni.bank.core.domain.port.in.EmitirTarjetaUseCase;
import pe.ayni.bank.core.infrastructure.config.ConfiguracionDeMensajeria;

/**
 * Oye que una solicitud de onboarding quedó aprobada y abre la cuenta.
 *
 * <p>Es el punto donde el esqueleto ambulante cruza de un servicio a otro. Que sea un
 * escuchador y no una llamada HTTP desde identidad es lo que permite que
 * {@code core-banking} pueda estar caído sin que el KYC falle: el evento espera en la cola.
 */
@Component
public class EscuchadorDeSolicitudAprobada {

    private static final Logger log =
            LoggerFactory.getLogger(EscuchadorDeSolicitudAprobada.class);

    /** Toda cuenta de ahorro se abre en soles. La de dólares llega en HU-09. */
    private static final Moneda MONEDA_DE_APERTURA = Moneda.PEN;

    private final AbrirCuentaDeAhorroUseCase abrirCuenta;
    private final EmitirTarjetaUseCase emitirTarjeta;

    public EscuchadorDeSolicitudAprobada(AbrirCuentaDeAhorroUseCase abrirCuenta, EmitirTarjetaUseCase emitirTarjeta) {
        this.abrirCuenta = abrirCuenta;
        this.emitirTarjeta = emitirTarjeta;
    }

    @RabbitListener(queues = ConfiguracionDeMensajeria.COLA_SOLICITUDES_APROBADAS)
    public void alAprobarseLaSolicitud(SolicitudAprobada evento) {
        try {
            Cuenta cuenta = abrirCuenta.abrirPara(evento.usuarioId(), evento.solicitudId(),
                    MONEDA_DE_APERTURA);
            // HU-09: la tarjeta virtual sale con la cuenta. Va en su propia transaccion: si
            // fallara, el mensaje vuelve a la cola, la apertura devuelve la cuenta que ya
            // existe y la emision, idempotente, lo intenta de nuevo.
            emitirTarjeta.emitirPara(evento.usuarioId(), cuenta.id());
        } catch (CuentaDuplicadaException duplicada) {
            // El titular ya tiene su cuenta. No es un fallo del mensaje: es el mismo hecho
            // llegando otra vez, o un KYC reintentado. Se traga a propósito, porque
            // relanzar la excepción devolvería el mensaje a la cola y entraría en un bucle
            // infinito de reintentos que no puede terminar bien nunca.
            log.info("La solicitud {} no abre cuenta: el titular ya tiene una activa.",
                    evento.solicitudId());
        }
    }

    /**
     * Lo que publica identidad.
     *
     * <p>Se declara aquí y no en un módulo compartido a propósito. Un módulo de eventos
     * comunes acopla los servicios en tiempo de compilación: cambiarlo obliga a desplegar
     * todos a la vez, que es exactamente lo que los microservicios evitan. El contrato
     * entre ambos es el JSON, no una clase Java.
     */
    public record SolicitudAprobada(UUID solicitudId, UUID usuarioId, String aprobadaEn) {
    }
}
