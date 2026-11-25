package pe.ayni.bank.gateway.seguridad;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;

/**
 * Que permiso exige cada ruta (AYNI-281). Cada regla es {@code [METODO:]patron=permiso}, por
 * ejemplo {@code POST:/api/v1/transferencias=transferencia:iniciar}; sin metodo vale para
 * cualquiera. Gana la primera que case, asi que van de lo concreto a lo general. Una ruta sin
 * regla solo exige sesion.
 *
 * <p>Que el gateway decida por ruta no sustituye las comprobaciones de los servicios donde la
 * operacion es sensible: es la primera barrera, la que corta lo que no debe llegar.
 */
public class PermisosPorRuta {

    private record Regla(String metodo, String patron, String permiso) {
    }

    private final List<Regla> reglas;
    private final AntPathMatcher comparador = new AntPathMatcher();

    public PermisosPorRuta(List<String> reglas) {
        List<Regla> leidas = new ArrayList<>();
        for (String texto : reglas) {
            String regla = texto.trim();
            if (regla.isEmpty()) {
                continue;
            }
            int igual = regla.lastIndexOf('=');
            if (igual <= 0 || igual == regla.length() - 1) {
                throw new IllegalArgumentException("Regla de permiso no valida: " + regla);
            }
            String ruta = regla.substring(0, igual);
            String metodo = null;
            if (!ruta.startsWith("/")) {
                int separador = ruta.indexOf(':');
                if (separador <= 0) {
                    throw new IllegalArgumentException("Regla de permiso no valida: " + regla);
                }
                metodo = ruta.substring(0, separador).toUpperCase();
                ruta = ruta.substring(separador + 1);
            }
            leidas.add(new Regla(metodo, ruta, regla.substring(igual + 1)));
        }
        this.reglas = List.copyOf(leidas);
    }

    /** El permiso que exige esa peticion, o vacio si basta con tener sesion. */
    public Optional<String> exigidoPara(HttpMethod metodo, String ruta) {
        return reglas.stream()
                .filter(r -> r.metodo() == null || (metodo != null && r.metodo().equals(metodo.name())))
                .filter(r -> comparador.match(r.patron(), ruta))
                .map(Regla::permiso)
                .findFirst();
    }
}
