package pe.ayni.bank.core.aceptacion;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import pe.ayni.bank.core.infrastructure.interbancaria.CamaraPort;
import pe.ayni.bank.core.infrastructure.interbancaria.EstadoInterbancario;

/**
 * La camara de compensacion de los escenarios de HU-11: responde lo que el paso le pide, o no
 * responde, y recuerda que instrucciones recibio. Es lo unico simulado de la transferencia
 * interbancaria; la base de datos, los asientos y el outbox son los reales.
 */
public class CamaraDePrueba implements CamaraPort {

    private EstadoInterbancario respuesta = EstadoInterbancario.COMPLETADA;
    private boolean responde = true;
    private final List<UUID> recibidas = new ArrayList<>();

    public void responder(EstadoInterbancario estado) {
        this.respuesta = estado;
        this.responde = true;
    }

    public void noResponder() {
        this.responde = false;
    }

    public List<UUID> recibidas() {
        return List.copyOf(recibidas);
    }

    public void reiniciar() {
        responder(EstadoInterbancario.COMPLETADA);
        recibidas.clear();
    }

    @Override
    public EstadoInterbancario enviar(UUID id, String cci, String importe) {
        recibidas.add(id);
        return contestar();
    }

    @Override
    public EstadoInterbancario consultar(UUID id) {
        return contestar();
    }

    private EstadoInterbancario contestar() {
        if (!responde) {
            throw new IllegalStateException("La camara no responde");
        }
        return respuesta;
    }
}
