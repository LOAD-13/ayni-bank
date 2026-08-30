package pe.ayni.bank.identity.domain.model;

/**
 * Un token de renovacion recien generado: su valor en claro y su huella.
 *
 * <p>Los dos van juntos porque el valor en claro <strong>solo existe en este instante</strong>:
 * se entrega al navegador en una cookie y se olvida. Lo que se persiste es la huella.
 * Separar las dos operaciones en dos llamadas invitaria a guardar el claro «un momentito»
 * en alguna variable, que es justo como acaban filtrandose las sesiones.
 */
public record TokenDeRenovacion(String enClaro, String huella) {

    @Override
    public String toString() {
        return "TokenDeRenovacion[oculto]";
    }
}
