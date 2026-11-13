package pe.ayni.bank.core.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import pe.ayni.bank.core.infrastructure.in.web.InterceptorDeAuditoria;

@Configuration
public class ConfiguracionDeLaWeb implements WebMvcConfigurer {

    private final InterceptorDeAuditoria auditoria;

    public ConfiguracionDeLaWeb(InterceptorDeAuditoria auditoria) {
        this.auditoria = auditoria;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registro) {
        registro.addInterceptor(auditoria).addPathPatterns("/api/**");
    }
}
