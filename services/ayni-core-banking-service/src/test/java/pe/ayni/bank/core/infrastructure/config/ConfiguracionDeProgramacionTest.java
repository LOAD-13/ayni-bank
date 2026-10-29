package pe.ayni.bank.core.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

class ConfiguracionDeProgramacionTest {

    @Test
    @DisplayName("Activa las tareas programadas: sin ellas el outbox no publicaria nunca")
    void activaLaProgramacion() {
        new ApplicationContextRunner()
                .withUserConfiguration(ConfiguracionDeProgramacion.class)
                .run(contexto -> assertThat(contexto).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
    }

    @Test
    @DisplayName("Esta bajo el paquete de la clase de arranque, asi que el escaneo de componentes la encuentra")
    void laEncuentraElEscaneo() {
        assertThat(ConfiguracionDeProgramacion.class.getPackageName())
                .startsWith(pe.ayni.bank.core.CoreBankingServiceApplication.class.getPackageName());
    }
}
