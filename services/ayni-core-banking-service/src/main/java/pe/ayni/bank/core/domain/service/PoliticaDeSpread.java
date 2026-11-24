package pe.ayni.bank.core.domain.service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import pe.ayni.bank.core.domain.model.Cotizacion;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.SegmentoCambiario;
import pe.ayni.bank.core.domain.model.TipoDeCambioAplicado;
import pe.ayni.bank.core.domain.model.TipoDeCambioVigente;

/**
 * La politica de tipo de cambio de Ayni (HU-12, diseño §3.7.1): SUNAT/BCRP es la referencia, no el
 * precio final. El banco gana en el margen, que se reparte por igual entre compra y venta.
 *
 * <p>Cuando el cliente cambia soles por dolares, el banco vende dolares: se aplica la venta de
 * referencia mas la mitad del margen. Cuando cambia dolares por soles, el banco los compra: la
 * compra de referencia menos la mitad. Siempre sale peor para el cliente que la referencia.
 */
public final class PoliticaDeSpread {

    private static final BigDecimal DOS = new BigDecimal("2");

    private PoliticaDeSpread() {
    }

    /**
     * El segmento mas alto cuyo saldo minimo alcanza el saldo promedio del cliente. Las tarifas
     * mejores exigen mas saldo, asi que se busca la de mayor minimo que se cumpla.
     */
    public static SegmentoCambiario segmentoPara(List<SegmentoCambiario> segmentos, BigDecimal saldoPromedio) {
        return segmentos.stream()
                .filter(s -> saldoPromedio.compareTo(s.saldoPromedioMinimo()) >= 0)
                .max(Comparator.comparing(SegmentoCambiario::saldoPromedioMinimo))
                .orElseThrow(() -> new IllegalStateException(
                        "No hay segmento cambiario para el saldo " + saldoPromedio));
    }

    /**
     * @param origen la moneda que entrega el cliente
     * @return el tipo aplicado, normalizado a la escala con que se guarda, para que el importe recibido
     *         pueda recalcularse exactamente desde lo registrado
     */
    public static Cotizacion cotizar(TipoDeCambioVigente vigente, SegmentoCambiario segmento, Moneda origen) {
        BigDecimal mitad = segmento.spread().divide(DOS);
        boolean clienteCompraDolares = origen == Moneda.PEN;
        BigDecimal referencia = clienteCompraDolares ? vigente.oficial().venta() : vigente.oficial().compra();
        BigDecimal factor = clienteCompraDolares ? BigDecimal.ONE.add(mitad) : BigDecimal.ONE.subtract(mitad);
        TipoDeCambioAplicado tipo = new TipoDeCambioAplicado(referencia.multiply(factor),
                vigente.obtenidoEn(), vigente.enCache());
        return new Cotizacion(tipo, referencia, segmento);
    }
}
