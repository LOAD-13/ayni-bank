package pe.ayni.bank.identity.infrastructure.out.crypto;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

import pe.ayni.bank.identity.domain.model.ContrasenaCifrada;
import pe.ayni.bank.identity.domain.port.out.CifradorDeContrasenasPort;

/**
 * Adaptador de {@link CifradorDeContrasenasPort} sobre Argon2id.
 *
 * <p><strong>Por que Argon2id y no bcrypt.</strong> bcrypt solo cuesta CPU, y una GPU
 * moderna prueba miles de millones de contrasenas por segundo. Argon2id ademas exige
 * memoria por intento, que es precisamente lo que una GPU no puede paralelizar barato.
 * Es la primera recomendacion de OWASP y de la propia RFC 9106.
 *
 * <p><strong>Los parametros.</strong> Son los que OWASP fija para Argon2id: 19 MiB de
 * memoria, dos iteraciones y un grado de paralelismo. Se escriben explicitos y no se
 * delega en los valores por defecto de Spring, que son mas bajos y cambian entre
 * versiones sin que el cambio se note en ninguna parte.
 *
 * <p>Subirlos mas tarde no rompe nada: la derivacion guarda sus propios parametros en la
 * cadena resultante, de modo que las contrasenas antiguas se siguen verificando con los
 * suyos. Lo que no se puede es bajarlos y dar por buena la seguridad anterior.
 */
@Component
public class CifradorArgon2id implements CifradorDeContrasenasPort {

    /** Longitud de la sal, en bytes. */
    private static final int LONGITUD_SAL = 16;

    /** Longitud de la derivacion, en bytes. */
    private static final int LONGITUD_HASH = 32;

    /** Hilos en paralelo. */
    private static final int PARALELISMO = 1;

    /** Memoria por intento, en KiB. 19456 KiB son los 19 MiB que recomienda OWASP. */
    private static final int MEMORIA_KIB = 19456;

    /** Pasadas sobre la memoria. */
    private static final int ITERACIONES = 2;

    private final Argon2PasswordEncoder codificador;

    public CifradorArgon2id() {
        this.codificador = new Argon2PasswordEncoder(
                LONGITUD_SAL, LONGITUD_HASH, PARALELISMO, MEMORIA_KIB, ITERACIONES);
    }

    @Override
    public ContrasenaCifrada cifrar(String contrasenaEnClaro) {
        return new ContrasenaCifrada(codificador.encode(contrasenaEnClaro));
    }

    @Override
    public boolean coincide(String contrasenaEnClaro, ContrasenaCifrada cifrada) {
        // matches() de Spring compara en tiempo constante: no revela por cuanto se
        // parecia la contrasena probada midiendo lo que tardo en fallar.
        return codificador.matches(contrasenaEnClaro, cifrada.valor());
    }
}
