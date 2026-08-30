import axe from "axe-core";

/* Ejecuta axe-core sobre un fragmento renderizado y devuelve las violaciones
 * encontradas, ya formateadas para que el fallo del test diga que regla se
 * incumplio, en que elemento y como se arregla.
 *
 * Limitacion consciente: las pruebas corren sobre jsdom, que no calcula estilos
 * aplicados, de modo que las reglas de contraste de color quedan inactivas
 * aqui. Ese frente se cubre en HU-17 con axe sobre un navegador real. Lo que si
 * detecta este nivel es lo que mas se rompe en la practica: campos de
 * formulario sin nombre accesible, roles incorrectos, jerarquia de encabezados
 * rota, botones sin texto y atributos ARIA invalidos. */

const CONFIGURACION: axe.RunOptions = {
  runOnly: {
    type: "tag",
    // WCAG 2.1 nivel AA es lo comprometido en el SLA. `best-practice` queda
    // fuera a proposito: son recomendaciones, no el criterio que aceptamos.
    values: ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"],
  },
  rules: {
    // Se desactiva explicitamente en lugar de dejar que falle sola: sobre jsdom
    // la regla lanza una excepcion interna al leer estilos que no existen, y el
    // ruido en el log acaba ensenando a ignorar la salida de las pruebas. La
    // comprobacion real de contraste vive en HU-17, sobre navegador.
    "color-contrast": { enabled: false },
  },
};

export async function violacionesDeAccesibilidad(contenedor: Element): Promise<string[]> {
  const resultado = await axe.run(contenedor, CONFIGURACION);

  return resultado.violations.map((violacion) => {
    const elementos = violacion.nodes.map((nodo) => `      ${nodo.html}`).join("\n");
    return [
      `  [${violacion.id}] ${violacion.help}`,
      `    Impacto: ${violacion.impact ?? "no clasificado"}`,
      `    Referencia: ${violacion.helpUrl}`,
      elementos,
    ].join("\n");
  });
}
