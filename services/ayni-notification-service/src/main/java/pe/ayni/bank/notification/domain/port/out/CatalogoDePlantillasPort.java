package pe.ayni.bank.notification.domain.port.out;

import java.util.Optional;

import pe.ayni.bank.notification.domain.model.Plantilla;
import pe.ayni.bank.notification.domain.model.TipoDeAviso;

/** Las plantillas de correo activas, en castellano. */
public interface CatalogoDePlantillasPort {

    Optional<Plantilla> deCorreo(TipoDeAviso tipo);
}
