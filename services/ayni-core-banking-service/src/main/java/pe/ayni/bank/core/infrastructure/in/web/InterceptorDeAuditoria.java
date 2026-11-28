package pe.ayni.bank.core.infrastructure.in.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Deja en cada peticion de la API la IP, el user-agent y el recurso, para que todo evento que
 * escriba la pista de auditoria los lleve sin que cada controlador tenga que pasarlos (HU-15).
 */
@Component
public class InterceptorDeAuditoria implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest peticion, HttpServletResponse respuesta, Object manejador) {
        peticion.setAttribute(DatosDeLaPeticion.ATRIBUTO, DatosDeLaPeticion.de(peticion));
        return true;
    }
}
