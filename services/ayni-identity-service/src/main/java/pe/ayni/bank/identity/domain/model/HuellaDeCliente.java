package pe.ayni.bank.identity.domain.model;

/**
 * De donde llego el intento de acceso. HU-04 exige registrar IP y agente de usuario en la
 * pista de auditoria, tanto si el ingreso salio bien como si no.
 *
 * <p>La IP es dato personal segun la Ley N.o 29733. Se guarda en la auditoria, que tiene
 * una finalidad legitima y un plazo de conservacion; no se escribe en los logs de
 * aplicacion, que los lee cualquiera con acceso a Loki.
 */
public record HuellaDeCliente(String ip, String agenteDeUsuario) {

    /** Recorta el agente de usuario: hay navegadores que envian cadenas larguisimas. */
    private static final int MAXIMO_AGENTE = 255;

    public HuellaDeCliente {
        ip = ip == null || ip.isBlank() ? "desconocida" : ip.trim();
        agenteDeUsuario = agenteDeUsuario == null || agenteDeUsuario.isBlank()
                ? "desconocido"
                : agenteDeUsuario.trim();
        if (agenteDeUsuario.length() > MAXIMO_AGENTE) {
            agenteDeUsuario = agenteDeUsuario.substring(0, MAXIMO_AGENTE);
        }
    }
}
