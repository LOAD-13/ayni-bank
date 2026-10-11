package pe.ayni.bank.core.infrastructure.in.programado;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import pe.ayni.bank.core.domain.port.in.CerrarDiaUseCase;

/**
 * Dispara el cierre diario de HU-06.
 *
 * <p>Cada dia pendiente se cierra en su propia transaccion y en orden. Si uno falla, se para
 * ahi: cerrar el dia siguiente sin el anterior romperia la cadena de saldos, y el proximo
 * disparo lo vuelve a intentar.
 */
@Component
public class CierreDiarioProgramado {

    private static final Logger log = LoggerFactory.getLogger(CierreDiarioProgramado.class);

    private final CerrarDiaUseCase cierre;

    public CierreDiarioProgramado(CerrarDiaUseCase cierre) {
        this.cierre = cierre;
    }

    @Scheduled(cron = "${ayni.devengo.cron}", zone = "America/Lima")
    public void cerrarLoPendiente() {
        for (LocalDate dia : cierre.diasPendientes()) {
            try {
                cierre.cerrar(dia);
            } catch (RuntimeException e) {
                log.error("El cierre del {} fallo y se reintentara en el siguiente disparo", dia, e);
                return;
            }
        }
    }
}
