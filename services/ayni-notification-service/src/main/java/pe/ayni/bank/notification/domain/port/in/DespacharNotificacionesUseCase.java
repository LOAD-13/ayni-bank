package pe.ayni.bank.notification.domain.port.in;

import pe.ayni.bank.notification.domain.model.ResultadoDelDespacho;

/** Envia las notificaciones pendientes cuyo turno ya llego. */
public interface DespacharNotificacionesUseCase {

    ResultadoDelDespacho despacharPendientes();
}
