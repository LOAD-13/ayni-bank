package pe.ayni.bank.core.domain.port.out;

import java.util.Optional;
import java.util.UUID;

import pe.ayni.bank.core.domain.model.Conversion;

/** Guarda la union de las dos patas de cada conversion con su tipo aplicado (HU-12). */
public interface RegistroDeConversionesPort {

    void registrar(Conversion conversion);

    /** La conversion cuya pata de salida es {@code movimientoSalidaId}, para responder a un reintento. */
    Optional<Conversion> buscarPorSalida(UUID movimientoSalidaId);
}
