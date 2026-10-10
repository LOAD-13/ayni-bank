package pe.ayni.bank.identity.infrastructure.out.correo;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.port.out.NotificadorDeRecuperacionPort;

/**
 * Correos de HU-21 en produccion, enviados <strong>fuera del hilo de la peticion</strong>.
 *
 * <p>Va aparte de {@link NotificadorPorCorreo} por esa diferencia. Alli el envio sincrono no
 * delata nada; aqui si: enviar el enlace dentro de la peticion la alargaria lo que tarda
 * SES —unos cientos de milisegundos— solo cuando la cuenta existe, y el cronometro
 * revelaria lo que el cuerpo identico oculta. Ver ADR-0030.
 *
 * <p>El enlace lleva el token en el fragmento ({@code #t=}), que el navegador nunca envia al
 * servidor: no queda en los registros de Caddy ni del gateway, ni sale en el {@code Referer}.
 */
@Component
@Profile("prod")
public class NotificadorDeRecuperacionPorCorreo implements NotificadorDeRecuperacionPort, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(NotificadorDeRecuperacionPorCorreo.class);

    private final EnviadorDeCorreo enviador;
    private final Executor ejecutor;
    private final String urlPublica;

    /**
     * El pool es propio y no un bean: declarar un {@code Executor} en el contexto
     * desactivaria el que Spring Boot configura por defecto para el resto de la aplicacion.
     *
     * <p>Acotado a proposito: con una cola sin limite, una rafaga de peticiones acumularia
     * correos en memoria. Si la cola se llena, el correo se envia en el hilo de quien llama,
     * mas lento pero sin perder el mensaje; el freno de tres enlaces por hora y cuenta hace
     * que en la practica no ocurra.
     */
    @Autowired
    public NotificadorDeRecuperacionPorCorreo(EnviadorDeCorreo enviador,
                                              @Value("${ayni.web.url-publica}") String urlPublica) {
        this(enviador, new ThreadPoolExecutor(1, 4, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(200),
                tarea -> {
                    Thread hilo = new Thread(tarea, "correo-recuperacion");
                    hilo.setDaemon(true);
                    return hilo;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()), urlPublica);
    }

    NotificadorDeRecuperacionPorCorreo(EnviadorDeCorreo enviador, Executor ejecutor, String urlPublica) {
        this.enviador = enviador;
        this.ejecutor = ejecutor;
        this.urlPublica = urlPublica.endsWith("/") ? urlPublica.substring(0, urlPublica.length() - 1) : urlPublica;
    }

    @Override
    public void enviarEnlaceDeRecuperacion(CorreoElectronico correo, String tokenEnClaro) {
        enviar(correo, PlantillaDeCorreo.RECUPERACION, urlPublica + "/recuperar/nueva#t=" + tokenEnClaro);
    }

    @Override
    public void avisarContrasenaCambiada(CorreoElectronico correo) {
        enviar(correo, PlantillaDeCorreo.CONTRASENA_CAMBIADA, urlPublica + "/ingresar");
    }

    private void enviar(CorreoElectronico correo, PlantillaDeCorreo plantilla, String dato) {
        ejecutor.execute(() -> {
            try {
                enviador.enviar(correo.valor(), plantilla.asunto(), plantilla.html(dato), plantilla.texto(dato));
                log.info("Correo enviado. plantilla={} destinatario={}", plantilla, correo.enmascarado());
            } catch (RuntimeException e) {
                log.warn("No se pudo enviar el correo. plantilla={} destinatario={} causa={}",
                        plantilla, correo.enmascarado(), e.getClass().getSimpleName());
            }
        });
    }

    /** Al apagar, se da un margen a los correos en curso antes de cortar. */
    @Override
    public void destroy() throws InterruptedException {
        if (ejecutor instanceof ExecutorService servicio) {
            servicio.shutdown();
            if (!servicio.awaitTermination(10, TimeUnit.SECONDS)) {
                servicio.shutdownNow();
            }
        }
    }
}
