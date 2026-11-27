package pe.ayni.bank.core.domain.port.out;

import pe.ayni.bank.core.domain.model.FiltroDeAuditoria;
import pe.ayni.bank.core.domain.model.PaginaDeAuditoria;

/** Lectura de {@code evento_auditoria}. Sin metodos de escritura, a proposito. */
public interface LecturaDeAuditoriaPort {

    /** Del mas reciente al mas antiguo. */
    PaginaDeAuditoria buscar(FiltroDeAuditoria filtro, int pagina, int tamano);
}
