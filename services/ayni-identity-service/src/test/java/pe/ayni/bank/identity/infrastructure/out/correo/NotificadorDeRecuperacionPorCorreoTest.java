package pe.ayni.bank.identity.infrastructure.out.correo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.infrastructure.out.messaging.NotificadorDeRecuperacionPorEventos;

/** HU-21: que plantilla sale, con que enlace, y que el envio no ocurre en el hilo de quien llama. */
class NotificadorDeRecuperacionPorCorreoTest {

    private record Enviado(String para, String asunto, String html, String texto) {
    }

    private final List<Enviado> enviados = new ArrayList<>();
    private final List<Runnable> encolados = new ArrayList<>();
    private final CorreoElectronico ana = new CorreoElectronico("ana.quispe@example.pe");

    /** Un ejecutor que solo encola: permite afirmar que nada se envia hasta que corre. */
    private final Executor diferido = encolados::add;

    private final NotificadorDeRecuperacionPorCorreo notificador = new NotificadorDeRecuperacionPorCorreo(
            (para, asunto, html, texto) -> enviados.add(new Enviado(para, asunto, html, texto)),
            diferido, "https://ayni.example.pe/");

    @Test
    @DisplayName("el enlace lleva el token en el fragmento y no se envia en el hilo de la peticion")
    void enlaceEnElFragmento() {
        notificador.enviarEnlaceDeRecuperacion(ana, "tok_123-abc");

        assertThat(enviados).isEmpty();
        encolados.forEach(Runnable::run);

        assertThat(enviados).singleElement().satisfies(e -> {
            assertThat(e.para()).isEqualTo("ana.quispe@example.pe");
            assertThat(e.asunto()).isEqualTo(PlantillaDeCorreo.RECUPERACION.asunto());
            assertThat(e.html()).contains("https://ayni.example.pe/recuperar/nueva#t=tok_123-abc");
            assertThat(e.texto()).contains("30 minutos");
        });
    }

    @Test
    @DisplayName("el aviso de cambio de contrasena lleva a la pantalla de ingreso")
    void avisoDeCambio() {
        notificador.avisarContrasenaCambiada(ana);
        encolados.forEach(Runnable::run);

        assertThat(enviados).singleElement().satisfies(e -> {
            assertThat(e.asunto()).isEqualTo(PlantillaDeCorreo.CONTRASENA_CAMBIADA.asunto());
            assertThat(e.html()).contains("https://ayni.example.pe/ingresar");
        });
    }

    @Test
    @DisplayName("un fallo de SES no se propaga: la contrasena ya cambio")
    void falloDeEnvio() {
        NotificadorDeRecuperacionPorCorreo conFallo = new NotificadorDeRecuperacionPorCorreo(
                (para, asunto, html, texto) -> {
                    throw new IllegalStateException("SES caido");
                }, Runnable::run, "https://ayni.example.pe");

        assertThatCode(() -> conFallo.avisarContrasenaCambiada(ana)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("con su pool propio envia en otro hilo y se apaga ordenadamente")
    void poolPropio() throws Exception {
        CountDownLatch enviado = new CountDownLatch(1);
        List<String> hilos = new ArrayList<>();
        NotificadorDeRecuperacionPorCorreo real = new NotificadorDeRecuperacionPorCorreo(
                (para, asunto, html, texto) -> {
                    hilos.add(Thread.currentThread().getName());
                    enviado.countDown();
                }, "https://ayni.example.pe");

        real.avisarContrasenaCambiada(ana);

        assertThat(enviado.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(hilos).containsExactly("correo-recuperacion");
        real.destroy();
    }

    @Test
    @DisplayName("fuera de produccion solo se anota, sin enlace")
    void fueraDeProduccion() {
        NotificadorDeRecuperacionPorEventos provisional = new NotificadorDeRecuperacionPorEventos();
        assertThatCode(() -> {
            provisional.enviarEnlaceDeRecuperacion(ana, "tok");
            provisional.avisarContrasenaCambiada(ana);
        }).doesNotThrowAnyException();
    }
}
