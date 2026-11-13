package pe.ayni.bank.core.infrastructure.out.tipodecambio;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import pe.ayni.bank.core.domain.model.FuenteDeTipoDeCambioNoDisponibleException;
import pe.ayni.bank.core.domain.model.TipoDeCambioOficial;
import pe.ayni.bank.core.domain.port.out.FuenteDeTipoDeCambioPort;

/**
 * Tipo de cambio de referencia desde la API publica de series estadisticas del BCRP (HU-12).
 *
 * <p>Pide las series PD04639PD (compra) y PD04640PD (venta), el tipo de cambio del sistema bancario
 * que publica la SBS, de los ultimos diez dias, y se queda con el ultimo dia que trae los dos
 * valores: los fines de semana y feriados no hay publicacion y el dia en curso llega como
 * {@code n.d.} hasta el cierre. La API responde JSON con {@code Content-Type: text/html}, por eso
 * se lee como texto y se interpreta aqui.
 */
@Component
public class AdaptadorBcrp implements FuenteDeTipoDeCambioPort {

    static final String FUENTE = "BCRP";
    private static final String SERIES = "PD04639PD-PD04640PD";
    private static final int DIAS_HACIA_ATRAS = 10;
    private static final Map<String, Integer> MESES = Map.ofEntries(
            Map.entry("Ene", 1), Map.entry("Feb", 2), Map.entry("Mar", 3), Map.entry("Abr", 4),
            Map.entry("May", 5), Map.entry("Jun", 6), Map.entry("Jul", 7), Map.entry("Ago", 8),
            Map.entry("Set", 9), Map.entry("Sep", 9), Map.entry("Oct", 10), Map.entry("Nov", 11),
            Map.entry("Dic", 12));

    private final RestClient http;
    private final JsonMapper json;

    @Autowired
    public AdaptadorBcrp(@Value("${ayni.tipo-de-cambio.bcrp.url}") String url,
                         @Value("${ayni.tipo-de-cambio.bcrp.timeout-de-conexion}") Duration conexion,
                         @Value("${ayni.tipo-de-cambio.bcrp.timeout-de-lectura}") Duration lectura,
                         RestClient.Builder builder, JsonMapper json) {
        this(builder.baseUrl(url).requestFactory(fabrica(conexion, lectura)).build(), json);
    }

    AdaptadorBcrp(RestClient http, JsonMapper json) {
        this.http = http;
        this.json = json;
    }

    @Override
    public TipoDeCambioOficial ultimoPublicado(LocalDate hasta) {
        String cuerpo;
        try {
            cuerpo = http.get()
                    .uri("/{series}/json/{desde}/{hasta}", SERIES, hasta.minusDays(DIAS_HACIA_ATRAS), hasta)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new FuenteDeTipoDeCambioNoDisponibleException("El BCRP no respondio", e);
        }
        Respuesta respuesta;
        try {
            respuesta = json.readValue(cuerpo == null ? "" : cuerpo, Respuesta.class);
        } catch (JacksonException e) {
            throw new FuenteDeTipoDeCambioNoDisponibleException("Respuesta del BCRP ilegible", e);
        }
        List<Periodo> periodos = respuesta == null || respuesta.periods() == null ? List.of() : respuesta.periods();
        for (int i = periodos.size() - 1; i >= 0; i--) {
            TipoDeCambioOficial tipo = convertir(periodos.get(i));
            if (tipo != null) {
                return tipo;
            }
        }
        throw new FuenteDeTipoDeCambioNoDisponibleException(
                "El BCRP no publico tipo de cambio entre " + hasta.minusDays(DIAS_HACIA_ATRAS) + " y " + hasta);
    }

    /** Un periodo sin los dos valores numericos ({@code n.d.}) no sirve: se salta. */
    private static TipoDeCambioOficial convertir(Periodo periodo) {
        if (periodo.values() == null || periodo.values().size() < 2) {
            return null;
        }
        try {
            return new TipoDeCambioOficial(fecha(periodo.name()), new BigDecimal(periodo.values().get(0)),
                    new BigDecimal(periodo.values().get(1)), FUENTE);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    /** El BCRP nombra los dias como {@code 12.Nov.26}, con el mes abreviado en castellano. */
    static LocalDate fecha(String nombre) {
        String[] partes = nombre.split("[.]");
        Integer mes = partes.length == 3 ? MESES.get(partes[1]) : null;
        if (mes == null) {
            throw new IllegalArgumentException("Fecha del BCRP desconocida: " + nombre);
        }
        return LocalDate.of(2000 + Integer.parseInt(partes[2]), mes, Integer.parseInt(partes[0]));
    }

    private static SimpleClientHttpRequestFactory fabrica(Duration conexion, Duration lectura) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(conexion);
        fabrica.setReadTimeout(lectura);
        return fabrica;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Respuesta(List<Periodo> periods) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Periodo(String name, List<String> values) {
    }
}
