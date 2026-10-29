package pe.ayni.bank.core.domain.port.in;

import java.util.Optional;
import java.util.UUID;

import pe.ayni.bank.core.domain.model.CvvDinamico;
import pe.ayni.bank.core.domain.model.PanRevelado;
import pe.ayni.bank.core.domain.model.Tarjeta;

/** HU-09: lo que el titular puede ver de su tarjeta, y como. */
public interface ConsultarTarjetaUseCase {

    /** La tarjeta del titular; quien la muestre solo debe ensenar los ultimos 4 digitos. */
    Optional<Tarjeta> deTitular(UUID usuarioId);

    /** El CVV de este momento, que cambia al acabar su ventana. */
    CvvDinamico cvv(UUID usuarioId);

    /**
     * El PAN completo, solo con una confirmacion de segundo factor atada a esta operacion, y
     * dejando rastro en la pista de auditoria con la IP.
     */
    PanRevelado revelar(UUID usuarioId, String confirmacion, UUID clave, String ip);
}
