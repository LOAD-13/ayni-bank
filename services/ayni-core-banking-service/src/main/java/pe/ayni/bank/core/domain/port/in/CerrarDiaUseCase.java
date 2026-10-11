package pe.ayni.bank.core.domain.port.in;

import java.time.LocalDate;
import java.util.List;

/**
 * El cierre diario de HU-06: devenga el interes de cada cuenta y, el ultimo dia del mes,
 * lo capitaliza.
 */
public interface CerrarDiaUseCase {

    /** Los dias que ya terminaron en Lima y aun no se cerraron, del mas antiguo al mas reciente. */
    List<LocalDate> diasPendientes();

    /** Cierra un dia. Si ya estaba cerrado, no hace nada. */
    void cerrar(LocalDate fecha);
}
