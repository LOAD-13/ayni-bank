package pe.ayni.bank.notification.aceptacion;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/** Punto de entrada de los escenarios de HU-13; surefire lo ejecuta con el resto de pruebas. */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "pe.ayni.bank.notification.aceptacion")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty")
public class PruebasDeAceptacion {
}
