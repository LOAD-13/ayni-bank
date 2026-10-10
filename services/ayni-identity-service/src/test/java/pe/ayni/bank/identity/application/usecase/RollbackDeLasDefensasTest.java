package pe.ayni.bank.identity.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;

import java.util.stream.Stream;

import pe.ayni.bank.identity.domain.model.CodigoDesafioInvalidoException;
import pe.ayni.bank.identity.domain.model.CredencialesInvalidasException;
import pe.ayni.bank.identity.domain.model.CuentaBloqueadaException;
import pe.ayni.bank.identity.domain.model.CuentaInhabilitadaException;
import pe.ayni.bank.identity.domain.model.MaximoIntentosDesafioExcedidoException;
import pe.ayni.bank.identity.domain.model.ReutilizacionDeRefreshTokenException;
import pe.ayni.bank.identity.domain.model.SegundoFactorInvalidoException;

/**
 * Las defensas contra la fuerza bruta escriben algo —el contador de fallos, el intento del
 * codigo, la auditoria, la familia invalidada— y <strong>despues</strong> lanzan la
 * excepcion que se traduce en 401 o 400. Con un {@code @Transactional} por defecto, esa
 * excepcion revierte lo escrito: el ingreso nunca se pausaba, el codigo de seis digitos
 * admitia intentos ilimitados y la reutilizacion de un token no cerraba la sesion.
 *
 * <p>Las pruebas de los casos de uso no lo veian porque usan dobles en memoria, que no
 * tienen transacciones. Esta prueba pregunta a Spring, con su propio lector de anotaciones,
 * que haria con cada excepcion.
 */
class RollbackDeLasDefensasTest {

    private static final AnnotationTransactionAttributeSource SPRING = new AnnotationTransactionAttributeSource();

    static Stream<Arguments> defensas() {
        return Stream.of(
                Arguments.of(IniciarSesionService.class, "presentarCredenciales", new CredencialesInvalidasException()),
                Arguments.of(IniciarSesionService.class, "presentarCredenciales",
                        new CuentaBloqueadaException(Duration.ofMinutes(1))),
                Arguments.of(IniciarSesionService.class, "presentarCredenciales", new CuentaInhabilitadaException()),
                Arguments.of(IniciarSesionService.class, "verificarSegundoFactor", new SegundoFactorInvalidoException()),
                Arguments.of(IniciarSesionService.class, "verificarSegundoFactor",
                        new CuentaBloqueadaException(Duration.ofMinutes(1))),
                Arguments.of(IniciarSesionService.class, "renovar",
                        new ReutilizacionDeRefreshTokenException(UUID.randomUUID())),
                Arguments.of(VerificarDesafioCodigoService.class, "verificar", new CodigoDesafioInvalidoException()),
                Arguments.of(VerificarDesafioCodigoService.class, "verificar",
                        new MaximoIntentosDesafioExcedidoException()),
                Arguments.of(VerificarContactoRegistroService.class, "verificarContacto",
                        new CodigoDesafioInvalidoException()),
                Arguments.of(VerificarContactoRegistroService.class, "verificarContacto",
                        new MaximoIntentosDesafioExcedidoException()));
    }

    @ParameterizedTest(name = "{0}.{1} conserva lo escrito al lanzar {2}")
    @MethodSource("defensas")
    @DisplayName("la excepcion de una defensa no revierte lo que la defensa escribio")
    void noRevierte(Class<?> servicio, String metodo, RuntimeException excepcion) {
        assertThat(atributoDe(servicio, metodo).rollbackOn(excepcion)).isFalse();
    }

    @ParameterizedTest(name = "{0}.{1} sigue revirtiendo ante un fallo inesperado")
    @MethodSource("defensas")
    @DisplayName("un fallo inesperado sigue revirtiendo la transaccion entera")
    void loInesperadoSiRevierte(Class<?> servicio, String metodo, RuntimeException ignorada) {
        assertThat(atributoDe(servicio, metodo).rollbackOn(new IllegalStateException("base caida"))).isTrue();
    }

    private static TransactionAttribute atributoDe(Class<?> servicio, String nombre) {
        Method metodo = Arrays.stream(servicio.getDeclaredMethods())
                .filter(m -> m.getName().equals(nombre))
                .findFirst().orElseThrow();
        TransactionAttribute atributo = SPRING.getTransactionAttribute(metodo, servicio);
        assertThat(atributo).as("%s.%s debe ser transaccional", servicio.getSimpleName(), nombre).isNotNull();
        return atributo;
    }
}
