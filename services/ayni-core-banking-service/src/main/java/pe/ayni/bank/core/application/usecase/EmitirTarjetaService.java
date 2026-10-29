package pe.ayni.bank.core.application.usecase;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.core.domain.model.Pan;
import pe.ayni.bank.core.domain.model.Tarjeta;
import pe.ayni.bank.core.domain.port.in.EmitirTarjetaUseCase;
import pe.ayni.bank.core.domain.port.out.RepositorioDeTarjetasPort;
import pe.ayni.bank.core.domain.service.GeneradorDePan;

/**
 * Emite la tarjeta de debito virtual · HU-09.
 *
 * <p>Idempotente por titular: si el evento de aprobacion llega dos veces, la segunda devuelve
 * la tarjeta que ya existe. El PAN es unico: si el generado ya existe, se genera otro. Con
 * nueve digitos al azar el choque es rarisimo, pero no imposible, y un PAN repetido seria
 * dos clientes pagando con la misma tarjeta. La base lo impone ademas con un indice unico.
 */
@Service
public class EmitirTarjetaService implements EmitirTarjetaUseCase {

    static final int INTENTOS = 5;

    private final RepositorioDeTarjetasPort tarjetas;
    private final GeneradorDePan generador;
    private final Clock reloj;

    public EmitirTarjetaService(RepositorioDeTarjetasPort tarjetas, GeneradorDePan generador, Clock reloj) {
        this.tarjetas = tarjetas;
        this.generador = generador;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public Tarjeta emitirPara(UUID usuarioId, UUID cuentaId) {
        return tarjetas.deTitular(usuarioId).orElseGet(() -> {
            Tarjeta nueva = Tarjeta.emitir(cuentaId, usuarioId, panUnico(), reloj.instant());
            tarjetas.guardar(nueva);
            return nueva;
        });
    }

    private Pan panUnico() {
        for (int i = 0; i < INTENTOS; i++) {
            Pan candidato = generador.generar();
            if (!tarjetas.existePan(candidato)) {
                return candidato;
            }
        }
        throw new IllegalStateException("No se pudo generar un PAN unico en " + INTENTOS + " intentos.");
    }
}
