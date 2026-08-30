package pe.ayni.bank.identity.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Segundo paso del ingreso: el vale del desafio y el codigo de la aplicacion. */
public record ComandoDeSegundoFactor(UUID desafioId, CodigoTotp codigo,
                                     HuellaDeCliente cliente) {

    public ComandoDeSegundoFactor {
        Objects.requireNonNull(desafioId, "Falta el desafio de segundo factor.");
        Objects.requireNonNull(codigo, "El codigo de verificacion es obligatorio.");
        Objects.requireNonNull(cliente);
    }

    @Override
    public String toString() {
        return "ComandoDeSegundoFactor[desafioId=" + desafioId + ", codigo=oculto]";
    }
}
