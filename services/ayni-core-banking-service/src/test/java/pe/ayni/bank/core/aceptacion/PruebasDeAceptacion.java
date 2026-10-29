package pe.ayni.bank.core.aceptacion;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * Punto de entrada de los escenarios de core-banking; surefire lo ejecuta con el resto.
 *
 * <p>Los escenarios {@code @pendiente} describen un criterio de aceptacion que el codigo
 * todavia no cumple. Se escriben igual, con el motivo en un comentario, para que la brecha
 * quede a la vista en la propia feature; no se ejecutan para no dejar la CI en rojo por
 * algo que no es una regresion. Quien implemente el criterio retira la etiqueta.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "pe.ayni.bank.core.aceptacion")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "not @pendiente")
public class PruebasDeAceptacion {
}
