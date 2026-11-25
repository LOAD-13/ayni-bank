package pe.ayni.bank.core.aceptacion;

import java.math.BigDecimal;
import java.time.LocalDate;

import pe.ayni.bank.core.domain.model.FuenteDeTipoDeCambioNoDisponibleException;
import pe.ayni.bank.core.domain.model.TipoDeCambioOficial;
import pe.ayni.bank.core.domain.port.out.FuenteDeTipoDeCambioPort;

/**
 * El BCRP de los escenarios de HU-12: publica lo que el paso le dice, o no responde. Es lo unico
 * simulado de la conversion; la cache diaria, el spread y el libro mayor son los reales.
 */
public class FuenteDeTipoDeCambioDePrueba implements FuenteDeTipoDeCambioPort {

    private TipoDeCambioOficial publicado;
    private boolean responde = true;

    public void publicar(LocalDate fecha, BigDecimal compra, BigDecimal venta) {
        this.publicado = new TipoDeCambioOficial(fecha, compra, venta, "BCRP");
        this.responde = true;
    }

    public void dejarDeResponder() {
        this.responde = false;
    }

    public void reiniciar() {
        this.publicado = null;
        this.responde = true;
    }

    @Override
    public TipoDeCambioOficial ultimoPublicado(LocalDate hasta) {
        if (!responde || publicado == null) {
            throw new FuenteDeTipoDeCambioNoDisponibleException("El BCRP de prueba no responde");
        }
        return publicado;
    }
}
