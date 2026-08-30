package pe.ayni.bank.identity.aceptacion;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * Punto de entrada de las pruebas de aceptacion.
 *
 * <p>Surefire ejecuta esta clase como una prueba mas, de modo que los escenarios de las
 * historias corren en el mismo {@code mvn test} que todo lo demas. No hay un comando
 * aparte que alguien pueda olvidarse de lanzar.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "pe.ayni.bank.identity.aceptacion")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty")
public class PruebasDeAceptacion {
}
