package pe.ayni.bank.core.domain.port.in;

import java.util.Optional;
import java.util.UUID;

import pe.ayni.bank.core.domain.model.Rendimiento;

/** Cuanto rinde la cuenta del titular: TREA vigente, lo ganado en el mes y el historico. */
public interface ConsultarRendimientoUseCase {

    Optional<Rendimiento> deTitular(UUID usuarioId);
}
