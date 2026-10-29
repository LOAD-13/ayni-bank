package pe.ayni.bank.notification.infrastructure.out.persistence;

import java.util.Optional;

import org.springframework.stereotype.Component;

import pe.ayni.bank.notification.domain.model.Plantilla;
import pe.ayni.bank.notification.domain.model.TipoDeAviso;
import pe.ayni.bank.notification.domain.port.out.CatalogoDePlantillasPort;

@Component
public class AdaptadorCatalogoDePlantillas implements CatalogoDePlantillasPort {

    private final PlantillaJpaRepository plantillas;

    AdaptadorCatalogoDePlantillas(PlantillaJpaRepository plantillas) {
        this.plantillas = plantillas;
    }

    @Override
    public Optional<Plantilla> deCorreo(TipoDeAviso tipo) {
        return plantillas.deCorreo(tipo.name()).map(p -> new Plantilla(p.getAsunto(), p.getCuerpo()));
    }
}
