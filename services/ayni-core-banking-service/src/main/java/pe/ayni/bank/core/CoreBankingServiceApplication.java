package pe.ayni.bank.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Arranque de {@code ayni-core-banking-service}.
 *
 * <p>Las tareas programadas se activan en {@code ConfiguracionDeProgramacion}, no aqui: lo
 * que lleva esta clase lo heredan tambien las pruebas de rebanada.
 */
@SpringBootApplication
public class CoreBankingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreBankingServiceApplication.class, args);
    }
}
