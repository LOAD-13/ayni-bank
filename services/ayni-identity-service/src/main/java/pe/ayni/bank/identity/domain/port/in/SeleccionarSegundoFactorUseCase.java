package pe.ayni.bank.identity.domain.port.in;

import java.util.UUID;

import pe.ayni.bank.identity.domain.model.MetodoDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;

/** Caso de uso para elegir y registrar un método de segundo factor (HU-22 / AYNI-127). */
public interface SeleccionarSegundoFactorUseCase {

    MetodoDeSegundoFactor seleccionarMetodo(UUID usuarioId, TipoDeSegundoFactor tipo);
}
