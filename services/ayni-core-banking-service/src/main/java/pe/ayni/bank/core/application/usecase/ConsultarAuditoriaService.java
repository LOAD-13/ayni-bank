package pe.ayni.bank.core.application.usecase;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.core.domain.model.FiltroDeAuditoria;
import pe.ayni.bank.core.domain.model.PaginaDeAuditoria;
import pe.ayni.bank.core.domain.port.in.ConsultarAuditoriaUseCase;
import pe.ayni.bank.core.domain.port.out.LecturaDeAuditoriaPort;

/** Traduce los dias de Lima del auditor a un rango de instantes y lee la pista (AYNI-284). */
@Service
public class ConsultarAuditoriaService implements ConsultarAuditoriaUseCase {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final LecturaDeAuditoriaPort lectura;

    public ConsultarAuditoriaService(LecturaDeAuditoriaPort lectura) {
        this.lectura = lectura;
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaDeAuditoria consultar(UUID usuarioId, String tipo, LocalDate desde, LocalDate hasta,
                                       int pagina, int tamano) {
        FiltroDeAuditoria filtro = new FiltroDeAuditoria(usuarioId, tipo,
                desde == null ? null : desde.atStartOfDay(LIMA).toInstant(),
                // «Hasta» incluye el dia entero: el rango del filtro es abierto por la derecha.
                hasta == null ? null : hasta.plusDays(1).atStartOfDay(LIMA).toInstant());
        return lectura.buscar(filtro, pagina, tamano);
    }
}
