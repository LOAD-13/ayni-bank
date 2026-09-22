package pe.ayni.bank.identity.infrastructure.out.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import pe.ayni.bank.identity.domain.model.Celular;
import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.port.out.NotificadorDeSegundoFactorPort;

/**
 * Implementacion provisional del notificador: registra la intencion en el log.
 *
 * <p>Misma reserva que {@link NotificadorDeRegistroPorEventos}: la entrega real la hace
 * {@code ayni-notification-service} por outbox transaccional (ADR-0003), y eso llega con
 * HU-13. Hasta entonces, generar un desafio OTP sin dejar ni este rastro dejaba el codigo
 * en claro calculado y descartado sin que nada indicara que el envio nunca ocurrio.
 *
 * <p>El codigo en claro no se registra: ni el usuario legitimo ni un atacante con acceso a
 * Loki deben poder leerlo desde aqui. Solo se dice que existio un desafio para ese
 * destinatario enmascarado.
 *
 * <p>Es el bean por defecto ({@code !e2e}): fuera del perfil de pruebas de extremo a
 * extremo, {@link NotificadorDeSegundoFactorPorCorreoDePrueba} no se activa.
 */
@Component
@Profile("!e2e")
public class NotificadorDeSegundoFactorPorEventos implements NotificadorDeSegundoFactorPort {

    private static final Logger log =
            LoggerFactory.getLogger(NotificadorDeSegundoFactorPorEventos.class);

    @Override
    public void enviarCodigoPorCorreo(CorreoElectronico correo, String codigo) {
        log.info("Pendiente de publicar por outbox: plantilla=CODIGO_VERIFICACION destinatario={}",
                correo.enmascarado());
    }

    @Override
    public void enviarCodigoPorSms(Celular celular, String codigo) {
        log.info("Pendiente de publicar por outbox: plantilla=CODIGO_VERIFICACION destinatario={}",
                celular.enmascarado());
    }
}
