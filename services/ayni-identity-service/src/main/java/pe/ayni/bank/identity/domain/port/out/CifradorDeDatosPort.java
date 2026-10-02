package pe.ayni.bank.identity.domain.port.out;

/**
 * Cifrado reversible de datos personales en reposo, segun §5.2 del documento de diseno.
 *
 * <p>Es un puerto distinto de {@link CifradorDeContrasenasPort} porque resuelve un problema
 * distinto. Una contrasena se deriva y no se recupera jamas: si hiciera falta leerla, el
 * diseno estaria mal. Un numero de documento, en cambio, hay que poder volver a leerlo para
 * contrastarlo con lo que extraiga el OCR. Usar aqui Argon2id seria imposible; usar alli
 * AES seria una brecha.
 */
public interface CifradorDeDatosPort {

    /** @return criptograma en texto, apto para guardar en una columna VARCHAR */
    String cifrar(String enClaro);

    String descifrar(String criptograma);
}
