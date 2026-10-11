package pe.ayni.bank.notification.infrastructure.in.programado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import pe.ayni.bank.notification.domain.model.ResultadoDelDespacho;
import pe.ayni.bank.notification.domain.port.in.DespacharNotificacionesUseCase;
import pe.ayni.bank.notification.infrastructure.config.ConfiguracionDeCorreo;
import pe.ayni.bank.notification.infrastructure.config.ConfiguracionDeMensajeria;
import pe.ayni.bank.notification.infrastructure.config.ConfiguracionDeNotificaciones;
import pe.ayni.bank.notification.infrastructure.out.correo.EnviadorDeCorreoEnBitacora;
import pe.ayni.bank.notification.infrastructure.out.correo.EnviadorDeCorreoSes;

class DespachadorProgramadoTest {

    @Test
    @DisplayName("cuenta cada resultado en ayni_notificaciones_total{resultado}")
    void metricas() {
        var metricas = new SimpleMeterRegistry();
        DespacharNotificacionesUseCase caso = mock(DespacharNotificacionesUseCase.class);
        when(caso.despacharPendientes()).thenReturn(new ResultadoDelDespacho(3, 2, 1));
        var despachador = new DespachadorProgramado(caso, metricas);

        despachador.despachar();
        despachador.despachar();

        assertThat(metricas.counter("ayni.notificaciones", "resultado", "enviada").count()).isEqualTo(6);
        assertThat(metricas.counter("ayni.notificaciones", "resultado", "reprogramada").count()).isEqualTo(4);
        assertThat(metricas.counter("ayni.notificaciones", "resultado", "fallida").count()).isEqualTo(2);
    }

    @Test
    @DisplayName("la configuracion enlaza las tres colas y arma la politica y los enviadores")
    void configuracion() {
        var mensajeria = new ConfiguracionDeMensajeria();
        assertThat(java.util.List.of(mensajeria.enlaceDeTitulares(), mensajeria.enlaceDeTransferencias(),
                mensajeria.enlaceDeDepositos()))
                .extracting(Binding::getRoutingKey)
                .containsExactly("identity.titular.activado", "core.transferencia.realizada",
                        "core.deposito.simulado.registrado");
        assertThat(mensajeria.conversorDeMensajes(new com.fasterxml.jackson.databind.ObjectMapper())).isNotNull();

        var notificaciones = new ConfiguracionDeNotificaciones();
        assertThat(notificaciones.politicaDeReintentos(Duration.ofSeconds(30), 5).maximo()).isEqualTo(5);
        assertThat(notificaciones.reloj()).isNotNull();

        var correo = new ConfiguracionDeCorreo();
        assertThat(correo.enviadorDeCorreoEnBitacora()).isInstanceOf(EnviadorDeCorreoEnBitacora.class);
        try (var ses = correo.sesV2Client("us-east-1")) {
            assertThat(correo.enviadorDeCorreoSes(ses, "Ayni <x@ayni.pe>")).isInstanceOf(EnviadorDeCorreoSes.class);
        }
    }
}
