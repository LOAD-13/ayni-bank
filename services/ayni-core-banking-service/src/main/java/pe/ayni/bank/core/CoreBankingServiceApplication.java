package pe.ayni.bank.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Arranque de {@code ayni-core-banking-service}.
 *
 * <p>{@code @EnableScheduling} lo necesita el publicador de la bandeja de salida. Sin esta
 * anotacion el componente se crea, no falla nada al arrancar y los eventos sencillamente
 * no salen nunca: la cuenta se abre, el evento se guarda y ahi se queda. Es el tipo de
 * fallo que no se ve hasta que alguien pregunta por que no llego una notificacion.
 */
@EnableScheduling
@SpringBootApplication
public class CoreBankingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreBankingServiceApplication.class, args);
    }
}
