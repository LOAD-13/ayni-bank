package pe.ayni.bank.notification.infrastructure.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import com.fasterxml.jackson.databind.ObjectMapper;

import pe.ayni.bank.notification.domain.model.AvisoDeMovimiento;
import pe.ayni.bank.notification.domain.model.Titular;
import pe.ayni.bank.notification.domain.model.TipoDeAviso;
import pe.ayni.bank.notification.domain.port.in.RegistrarAvisosUseCase;
import pe.ayni.bank.notification.domain.port.in.RegistrarTitularUseCase;
import pe.ayni.bank.notification.infrastructure.in.messaging.EscuchadorDeTitulares.TitularActivado;
import pe.ayni.bank.notification.infrastructure.in.messaging.TraductorDeMovimientos.DepositoSimuladoRegistrado;
import pe.ayni.bank.notification.infrastructure.in.messaging.TraductorDeMovimientos.InteresCapitalizado;
import pe.ayni.bank.notification.infrastructure.in.messaging.TraductorDeMovimientos.TransferenciaRealizada;

/** El contrato con core-banking e identity: lo que llega por RabbitMQ y como se convierte en avisos. */
class TraductorDeMovimientosTest {

    private static final UUID EVENTO = UUID.fromString("0f9a1b2c-3d4e-4f60-8a7b-9c0d1e2f3a4b");
    private static final UUID MOVIMIENTO = UUID.fromString("a1b2c3d4-0000-4000-8000-000000000001");
    private static final UUID ANA = UUID.randomUUID();
    private static final UUID BRUNO = UUID.randomUUID();

    /** El JSON tal cual lo escribe el outbox de core-banking (PR #69). */
    private static String transferenciaJson() {
        return """
                {"movimientoId":"%s","cuentaOrigenId":"%s","cuentaDestinoId":"%s","importe":"1234.50",
                 "moneda":"PEN","usuarioOrigenId":"%s","usuarioDestinoId":"%s","cuentaOrigen":"****4321",
                 "cuentaDestino":"****8765","saldoOrigen":"765.50","saldoDestino":"2234.50",
                 "registradoEn":"2026-10-11T15:04:05.123456Z"}
                """.formatted(MOVIMIENTO, UUID.randomUUID(), UUID.randomUUID(), ANA, BRUNO);
    }

    @Test
    @DisplayName("una transferencia avisa a quien envia y a quien recibe, cada uno con su saldo")
    void transferencia() throws Exception {
        var e = new ObjectMapper().readValue(transferenciaJson(), TransferenciaRealizada.class);

        List<AvisoDeMovimiento> avisos = TraductorDeMovimientos.deTransferencia(EVENTO, e);

        assertThat(avisos).hasSize(2);
        AvisoDeMovimiento enviado = avisos.get(0);
        assertThat(enviado.usuarioId()).isEqualTo(ANA);
        assertThat(enviado.tipo()).isEqualTo(TipoDeAviso.TRANSFERENCIA_ENVIADA);
        assertThat(enviado.eventoId()).isEqualTo(EVENTO);
        assertThat(enviado.datos())
                .containsEntry("importe", "1,234.50")
                .containsEntry("moneda", "PEN")
                .containsEntry("cuenta", "****4321")
                .containsEntry("contraparte", "****8765")
                .containsEntry("saldo", "765.50")
                // 15:04 UTC son las 10:04 en Lima
                .containsEntry("fecha", "11/10/2026 a las 10:04")
                .containsEntry("operacion", "A1B2C3D4");
        AvisoDeMovimiento recibido = avisos.get(1);
        assertThat(recibido.usuarioId()).isEqualTo(BRUNO);
        assertThat(recibido.tipo()).isEqualTo(TipoDeAviso.TRANSFERENCIA_RECIBIDA);
        assertThat(recibido.datos())
                .containsEntry("cuenta", "****8765")
                .containsEntry("contraparte", "****4321")
                .containsEntry("saldo", "2,234.50");
    }

    @Test
    @DisplayName("un evento antiguo sin titulares no produce avisos")
    void sinTitulares() {
        var e = new TransferenciaRealizada(MOVIMIENTO, UUID.randomUUID(), UUID.randomUUID(), "1.00", "PEN",
                null, null, null, null, null, null, null);
        assertThat(TraductorDeMovimientos.deTransferencia(EVENTO, e)).isEmpty();
        var d = new DepositoSimuladoRegistrado(MOVIMIENTO, UUID.randomUUID(), "1.00", "PEN", null, null, null, null);
        assertThat(TraductorDeMovimientos.deDeposito(EVENTO, d)).isEmpty();
    }

    @Test
    @DisplayName("un deposito avisa a su titular con el saldo resultante")
    void deposito() {
        var d = new DepositoSimuladoRegistrado(MOVIMIENTO, UUID.randomUUID(), "500.00", "PEN", ANA, "****4321",
                "1500.00", "2026-10-11T03:30:00Z");

        List<AvisoDeMovimiento> avisos = TraductorDeMovimientos.deDeposito(EVENTO, d);

        assertThat(avisos).singleElement().satisfies(a -> {
            assertThat(a.tipo()).isEqualTo(TipoDeAviso.DEPOSITO_RECIBIDO);
            assertThat(a.datos())
                    .containsEntry("saldo", "1,500.00")
                    .containsEntry("fecha", "10/10/2026 a las 22:30")
                    .doesNotContainKey("contraparte");
        });
    }

    @Test
    @DisplayName("HU-06: el interes capitalizado avisa al titular con el mes en castellano y el saldo")
    void interes() throws Exception {
        var e = new ObjectMapper().readValue("""
                {"movimientoId":"%s","cuentaId":"%s","usuarioId":"%s","cuenta":"****4321","importe":"4.20",
                 "moneda":"PEN","saldo":"1004.20","periodo":"2026-10","registradoEn":"2026-11-01T05:00:00Z"}
                """.formatted(MOVIMIENTO, UUID.randomUUID(), ANA), InteresCapitalizado.class);

        assertThat(TraductorDeMovimientos.deInteres(EVENTO, e)).singleElement().satisfies(a -> {
            assertThat(a.tipo()).isEqualTo(TipoDeAviso.INTERES_CAPITALIZADO);
            assertThat(a.usuarioId()).isEqualTo(ANA);
            assertThat(a.datos())
                    .containsEntry("periodo", "octubre de 2026")
                    .containsEntry("importe", "4.20")
                    .containsEntry("saldo", "1,004.20")
                    .containsEntry("fecha", "01/11/2026 a las 00:00");
        });
        var sinTitular = new InteresCapitalizado(MOVIMIENTO, UUID.randomUUID(), null, "****4321", "4.20", "PEN",
                "1004.20", "2026-10", "2026-11-01T05:00:00Z");
        assertThat(TraductorDeMovimientos.deInteres(EVENTO, sinTitular)).isEmpty();

        RegistrarAvisosUseCase avisos = mock(RegistrarAvisosUseCase.class);
        new EscuchadorDeMovimientos(avisos).alCapitalizarseLosIntereses(e, EVENTO.toString());
        verify(avisos).registrar(TraductorDeMovimientos.deInteres(EVENTO, e));
    }

    @Test
    @DisplayName("el eventId es el messageId del outbox; si no llega o no es valido, el del movimiento")
    void eventoId() {
        assertThat(TraductorDeMovimientos.eventoId(EVENTO.toString(), MOVIMIENTO)).isEqualTo(EVENTO);
        assertThat(TraductorDeMovimientos.eventoId(null, MOVIMIENTO)).isEqualTo(MOVIMIENTO);
        assertThat(TraductorDeMovimientos.eventoId("no-es-uuid", MOVIMIENTO)).isEqualTo(MOVIMIENTO);
    }

    @Test
    @DisplayName("el conversor de RabbitMQ lee el JSON crudo del outbox con el tipo del escuchador")
    void conversor() throws Exception {
        var props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setMessageId(EVENTO.toString());
        Method metodo = EscuchadorDeMovimientos.class.getMethod("alRealizarseUnaTransferencia",
                TransferenciaRealizada.class, String.class);
        props.setInferredArgumentType(metodo.getGenericParameterTypes()[0]);
        var mensaje = new Message(transferenciaJson().getBytes(StandardCharsets.UTF_8), props);

        Object leido = new Jackson2JsonMessageConverter(new ObjectMapper()).fromMessage(mensaje);

        assertThat(leido).isInstanceOf(TransferenciaRealizada.class);
        assertThat(((TransferenciaRealizada) leido).saldoDestino()).isEqualTo("2234.50");
    }

    @Test
    @DisplayName("los escuchadores entregan los avisos y el titular a sus casos de uso")
    @SuppressWarnings("unchecked")
    void escuchadores() throws Exception {
        RegistrarAvisosUseCase avisos = mock(RegistrarAvisosUseCase.class);
        var escuchador = new EscuchadorDeMovimientos(avisos);
        var e = new ObjectMapper().readValue(transferenciaJson(), TransferenciaRealizada.class);

        escuchador.alRealizarseUnaTransferencia(e, EVENTO.toString());
        escuchador.alRegistrarseUnDeposito(new DepositoSimuladoRegistrado(MOVIMIENTO, UUID.randomUUID(), "1.00",
                "PEN", ANA, "****4321", "2.00", "2026-10-11T03:30:00Z"), null);

        ArgumentCaptor<List<AvisoDeMovimiento>> captor = ArgumentCaptor.forClass(List.class);
        verify(avisos, times(2)).registrar(captor.capture());
        assertThat(captor.getAllValues().get(0)).hasSize(2);
        assertThat(captor.getAllValues().get(1)).singleElement()
                .extracting(AvisoDeMovimiento::eventoId).isEqualTo(MOVIMIENTO);

        RegistrarTitularUseCase titulares = mock(RegistrarTitularUseCase.class);
        var evento = new TitularActivado(ANA, "ana@example.pe", "Ana", "2026-10-11T15:00:00Z");
        new EscuchadorDeTitulares(titulares).alActivarseElTitular(evento);
        verify(titulares).registrar(new Titular(ANA, "ana@example.pe", "Ana"));
        assertThat(evento.toString()).doesNotContain("ana@example.pe");
    }
}
