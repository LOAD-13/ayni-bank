package pe.ayni.bank.core.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.ayni.bank.core.domain.model.FuenteDeTipoDeCambioNoDisponibleException;
import pe.ayni.bank.core.domain.model.TipoDeCambioConocido;
import pe.ayni.bank.core.domain.model.TipoDeCambioOficial;
import pe.ayni.bank.core.domain.model.TipoDeCambioVigente;
import pe.ayni.bank.core.domain.port.out.FuenteDeTipoDeCambioPort;
import pe.ayni.bank.core.domain.port.out.RepositorioDeTipoDeCambioPort;

/** Cache diaria y respaldo al ultimo valor conocido (HU-12, AYNI-267 y AYNI-268). */
class ObtenerTipoDeCambioServiceTest {

    /** 14:00 en Lima, lunes 23 de noviembre de 2026. */
    private static final Instant AHORA = Instant.parse("2026-11-23T19:00:00Z");
    private static final TipoDeCambioOficial VIERNES = new TipoDeCambioOficial(LocalDate.parse("2026-11-20"),
            new BigDecimal("3.745000"), new BigDecimal("3.752000"), "BCRP");

    private final FuenteDeTipoDeCambioPort fuente = mock(FuenteDeTipoDeCambioPort.class);
    private final RepositorioDeTipoDeCambioPort guardados = mock(RepositorioDeTipoDeCambioPort.class);
    private ObtenerTipoDeCambioService servicio;

    @BeforeEach
    void preparar() {
        servicio = new ObtenerTipoDeCambioService(fuente, guardados, Clock.fixed(AHORA, ZoneOffset.UTC));
        when(guardados.obtenidoDesde(any())).thenReturn(Optional.empty());
        when(guardados.ultimoConocido()).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("Lo obtenido hoy se sirve de la cache sin volver a llamar a la fuente")
    void cacheDelDia() {
        Instant temprano = AHORA.minusSeconds(3600);
        when(guardados.obtenidoDesde(any())).thenReturn(Optional.of(new TipoDeCambioConocido(VIERNES, temprano)));

        assertThat(servicio.vigente()).isEqualTo(new TipoDeCambioVigente(VIERNES, temprano, false));
        verify(fuente, never()).ultimoPublicado(any());
    }

    @Test
    @DisplayName("Sin cache de hoy se consulta la fuente y se guarda lo que trae")
    void consultaYGuarda() {
        when(fuente.ultimoPublicado(LocalDate.parse("2026-11-23"))).thenReturn(VIERNES);

        assertThat(servicio.vigente()).isEqualTo(new TipoDeCambioVigente(VIERNES, AHORA, false));
        verify(guardados).guardar(VIERNES, AHORA);
    }

    @Test
    @DisplayName("El dia es el de Lima: a las 22:00 de Lima ya es el dia siguiente en UTC, y la cache sigue siendo de hoy")
    void diaDeLimaYNoDeUtc() {
        Instant nocheEnLima = Instant.parse("2026-11-24T03:00:00Z");
        servicio = new ObtenerTipoDeCambioService(fuente, guardados, Clock.fixed(nocheEnLima, ZoneOffset.UTC));
        when(fuente.ultimoPublicado(any())).thenReturn(VIERNES);

        servicio.vigente();

        // Hoy en Lima empezo el 23 a las 05:00 UTC, no el 24 a las 00:00 UTC.
        verify(guardados).obtenidoDesde(Instant.parse("2026-11-23T05:00:00Z"));
        verify(fuente).ultimoPublicado(LocalDate.parse("2026-11-23"));
    }

    @Test
    @DisplayName("Escenario 2: si la fuente no responde se usa el ultimo valor conocido, marcado como cache")
    void respaldoAlUltimoConocido() {
        Instant ayer = AHORA.minusSeconds(86_400);
        when(fuente.ultimoPublicado(any())).thenThrow(new FuenteDeTipoDeCambioNoDisponibleException("sin respuesta"));
        when(guardados.ultimoConocido()).thenReturn(Optional.of(new TipoDeCambioConocido(VIERNES, ayer)));

        assertThat(servicio.vigente()).isEqualTo(new TipoDeCambioVigente(VIERNES, ayer, true));
        verify(guardados, never()).guardar(any(), any());
    }

    @Test
    @DisplayName("Sin fuente y sin ningun valor guardado no hay tipo de cambio que ofrecer")
    void sinRespaldo() {
        FuenteDeTipoDeCambioNoDisponibleException caida = new FuenteDeTipoDeCambioNoDisponibleException("sin respuesta");
        when(fuente.ultimoPublicado(any())).thenThrow(caida);

        assertThatThrownBy(() -> servicio.vigente())
                .isInstanceOf(FuenteDeTipoDeCambioNoDisponibleException.class)
                .hasCause(caida);
    }
}
