package pe.ayni.bank.core.aceptacion;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/** Punto de entrada de los escenarios de core-banking; surefire lo ejecuta con el resto. */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "pe.ayni.bank.core.aceptacion")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty")
public class PruebasDeAceptacion {
}
