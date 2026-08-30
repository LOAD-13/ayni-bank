package pe.ayni.bank.identity.infrastructure.out.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.port.out.NotificadorDeSeguridadPort;

/**
 * Implementacion provisional de los avisos de seguridad, por el mismo motivo y con las
 * mismas cautelas que {@link NotificadorDeRegistroPorEventos}: la entrega real la hara
 * {@code ayni-notification-service} a traves del outbox (ADR-0003), y eso llega con HU-13.
 *
 * <p>Estos dos avisos son distintos de los del registro en una cosa: se mandan a alguien
 * que <strong>si</strong> es cliente, y le cuentan algo que solo el tiene derecho a saber.
 * Por eso el correo va enmascarado tambien aqui: que la persona exista no autoriza a dejar
 * su direccion escrita en un log que lee todo el equipo.
 */
@Component
public class NotificadorDeSeguridadPorEventos implements NotificadorDeSeguridadPort {

    private static final Logger log =
            LoggerFactory.getLogger(NotificadorDeSeguridadPorEventos.class);

    @Override
    public void avisarIngresoPausado(CorreoElectronico correo) {
        log.info("Pendiente de publicar por outbox: plantilla=INGRESO_PAUSADO "
                + "destinatario={}", correo.enmascarado());
    }

    @Override
    public void avisarSesionCerradaPorSeguridad(CorreoElectronico correo) {
        log.info("Pendiente de publicar por outbox: plantilla=SESION_CERRADA_POR_SEGURIDAD "
                + "destinatario={}", correo.enmascarado());
    }
}
