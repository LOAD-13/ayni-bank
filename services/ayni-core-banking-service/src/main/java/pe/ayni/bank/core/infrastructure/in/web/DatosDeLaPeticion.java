package pe.ayni.bank.core.infrastructure.in.web;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Lo que la pista de auditoria guarda de la peticion que origino un evento (HU-15): IP,
 * user-agent y recurso. Lo deja {@link InterceptorDeAuditoria} en la peticion; en un proceso
 * programado no hay peticion y no hay datos.
 */
public record DatosDeLaPeticion(String ip, String agente, String recurso) {

    static final String ATRIBUTO = DatosDeLaPeticion.class.getName();
    private static final int MAXIMO_IP = 45;
    private static final int MAXIMO_AGENTE = 256;
    private static final int MAXIMO_RECURSO = 120;

    static DatosDeLaPeticion de(HttpServletRequest peticion) {
        // La ruta con sus variables sin rellenar: identifica el recurso sin guardar identificadores.
        Object patron = peticion.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String ruta = patron != null ? patron.toString() : peticion.getRequestURI();
        return new DatosDeLaPeticion(recortar(ipDe(peticion), MAXIMO_IP),
                recortar(peticion.getHeader("User-Agent"), MAXIMO_AGENTE),
                recortar(peticion.getMethod() + " " + ruta, MAXIMO_RECURSO));
    }

    /** Los de la peticion en curso, si la hay. */
    public static Optional<DatosDeLaPeticion> actuales() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos)) {
            return Optional.empty();
        }
        HttpServletRequest peticion = atributos.getRequest();
        return Optional.of(peticion.getAttribute(ATRIBUTO) instanceof DatosDeLaPeticion datos ? datos : de(peticion));
    }

    /** La IP del cliente: el primer valor de X-Forwarded-For, como en identity. */
    static String ipDe(HttpServletRequest peticion) {
        String reenviada = peticion.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            return reenviada.split(",")[0].trim();
        }
        return peticion.getRemoteAddr();
    }

    private static String recortar(String valor, int maximo) {
        return valor == null || valor.length() <= maximo ? valor : valor.substring(0, maximo);
    }
}
