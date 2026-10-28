package pe.ayni.bank.core.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activa las tareas programadas: el publicador de la bandeja de salida y el cierre diario.
 *
 * <p>Sin esta clase los componentes se crean, no falla nada al arrancar y los eventos
 * sencillamente no salen nunca: la cuenta se abre, el evento se guarda y ahi se queda. Es
 * el tipo de fallo que no se ve hasta que alguien pregunta por que no llego una
 * notificacion.
 *
 * <p>Vive aqui y no en la clase de arranque porque las pruebas de rebanada
 * ({@code @DataJpaTest}) heredan lo que lleve esa clase. Con la programacion activa en las
 * pruebas de aceptacion, el publicador corria solo cada dos segundos y a veces publicaba un
 * evento antes de que el escenario comprobara que seguia pendiente.
 */
@Configuration
@EnableScheduling
public class ConfiguracionDeProgramacion {
}
