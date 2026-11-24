package pe.ayni.bank.core.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.ayni.bank.core.domain.model.Cotizacion;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.SegmentoCambiario;
import pe.ayni.bank.core.domain.model.TipoDeCambioOficial;
import pe.ayni.bank.core.domain.model.TipoDeCambioVigente;

/** La politica cambiaria de HU-12 (diseño §3.7.1): referencia, spread simetrico y segmentos. */
class PoliticaDeSpreadTest {

    private static final SegmentoCambiario ESTANDAR =
            new SegmentoCambiario("ESTANDAR", new BigDecimal("0.005000"), new BigDecimal("0.0000"));
    private static final SegmentoCambiario PREFERENCIAL =
            new SegmentoCambiario("PREFERENCIAL", new BigDecimal("0.002500"), new BigDecimal("5000.0000"));
    private static final Instant OBTENIDO = Instant.parse("2026-11-23T19:00:00Z");
    private static final TipoDeCambioVigente HOY = vigente(false);

    private static TipoDeCambioVigente vigente(boolean enCache) {
        return new TipoDeCambioVigente(new TipoDeCambioOficial(LocalDate.parse("2026-11-20"),
                new BigDecimal("3.745000"), new BigDecimal("3.752000"), "BCRP"), OBTENIDO, enCache);
    }

    @Test
    @DisplayName("De soles a dolares el banco vende: venta de referencia mas la mitad del margen")
    void clienteCompraDolares() {
        Cotizacion c = PoliticaDeSpread.cotizar(HOY, ESTANDAR, Moneda.PEN);

        assertThat(c.referencia()).isEqualByComparingTo("3.752");
        assertThat(c.tipo().solesPorDolar()).isEqualTo(new BigDecimal("3.761380"));
        assertThat(c.segmento()).isEqualTo(ESTANDAR);
    }

    @Test
    @DisplayName("De dolares a soles el banco compra: compra de referencia menos la mitad del margen, redondeada HALF_EVEN")
    void clienteVendeDolares() {
        Cotizacion c = PoliticaDeSpread.cotizar(HOY, ESTANDAR, Moneda.USD);

        assertThat(c.referencia()).isEqualByComparingTo("3.745");
        // 3.745 x 0.9975 = 3.7356375, un empate en la septima cifra: HALF_EVEN sube a la par.
        assertThat(c.tipo().solesPorDolar()).isEqualTo(new BigDecimal("3.735638"));
    }

    @Test
    @DisplayName("La tarifa preferencial da mejor tipo al cliente en los dos sentidos")
    void preferencialEsMejor() {
        Cotizacion compraEst = PoliticaDeSpread.cotizar(HOY, ESTANDAR, Moneda.PEN);
        Cotizacion compraPref = PoliticaDeSpread.cotizar(HOY, PREFERENCIAL, Moneda.PEN);
        Cotizacion ventaEst = PoliticaDeSpread.cotizar(HOY, ESTANDAR, Moneda.USD);
        Cotizacion ventaPref = PoliticaDeSpread.cotizar(HOY, PREFERENCIAL, Moneda.USD);

        assertThat(compraPref.tipo().solesPorDolar()).isEqualTo(new BigDecimal("3.756690"));
        assertThat(ventaPref.tipo().solesPorDolar()).isEqualTo(new BigDecimal("3.740319"));
        assertThat(compraPref.tipo().solesPorDolar()).isLessThan(compraEst.tipo().solesPorDolar());
        assertThat(ventaPref.tipo().solesPorDolar()).isGreaterThan(ventaEst.tipo().solesPorDolar());
    }

    @Test
    @DisplayName("El mismo importe convertido por dos segmentos da resultados distintos y ambos se recalculan")
    void mismoImporteDosSegmentos() {
        Dinero soles = Dinero.de("370.00", Moneda.PEN);

        Dinero estandar = PoliticaDeSpread.cotizar(HOY, ESTANDAR, Moneda.PEN).tipo().convertir(soles);
        Dinero preferencial = PoliticaDeSpread.cotizar(HOY, PREFERENCIAL, Moneda.PEN).tipo().convertir(soles);

        assertThat(estandar).isEqualTo(Dinero.de("98.37", Moneda.USD));
        assertThat(preferencial).isEqualTo(Dinero.de("98.49", Moneda.USD));
    }

    @Test
    @DisplayName("El aviso de cache y el momento de obtencion pasan tal cual a lo que se aplica")
    void conservaLaProcedencia() {
        Cotizacion c = PoliticaDeSpread.cotizar(vigente(true), ESTANDAR, Moneda.PEN);

        assertThat(c.tipo().enCache()).isTrue();
        assertThat(c.tipo().obtenidoEn()).isEqualTo(OBTENIDO);
    }

    @Test
    @DisplayName("Se elige el segmento de mayor minimo que el saldo alcanza; con exactamente el minimo, ese")
    void eligeElSegmento() {
        List<SegmentoCambiario> todos = List.of(PREFERENCIAL, ESTANDAR);

        assertThat(PoliticaDeSpread.segmentoPara(todos, new BigDecimal("4999.99"))).isEqualTo(ESTANDAR);
        assertThat(PoliticaDeSpread.segmentoPara(todos, new BigDecimal("5000.00"))).isEqualTo(PREFERENCIAL);
        assertThat(PoliticaDeSpread.segmentoPara(todos, BigDecimal.ZERO)).isEqualTo(ESTANDAR);
    }

    @Test
    @DisplayName("Sin ningun segmento aplicable es un error de datos, no un margen inventado")
    void sinSegmento() {
        assertThatThrownBy(() -> PoliticaDeSpread.segmentoPara(List.of(PREFERENCIAL), BigDecimal.ONE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Un segmento no admite un margen negativo ni del cien por cien")
    void segmentoValido() {
        assertThatThrownBy(() -> new SegmentoCambiario("X", new BigDecimal("-0.01"), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SegmentoCambiario("X", BigDecimal.ONE, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
