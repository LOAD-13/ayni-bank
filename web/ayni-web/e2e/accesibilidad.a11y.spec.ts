import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

/**
 * Accesibilidad sobre navegador real, no sobre jsdom.
 *
 * Es una diferencia que importa: jsdom no calcula estilos aplicados, así que **no puede
 * comprobar el contraste de color**, que es justo donde una landing sobre fotografías y
 * degradados se rompe. Aquí sí se comprueba.
 *
 * Se limita a WCAG 2.1 nivel AA, que es lo comprometido en el SLA del proyecto.
 */
const RUTAS = ["/", "/registro", "/ingresar", "/pendiente"] as const;

for (const ruta of RUTAS) {
  test(`${ruta} no presenta violaciones de WCAG 2.1 AA`, async ({ page }) => {
    await page.goto(ruta);
    await page.waitForLoadState("networkidle");

    const { violations } = await new AxeBuilder({ page })
      .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
      .analyze();

    const detalle = violations
      .map(
        (v) =>
          `  [${v.id}] ${v.help}\n    ${v.helpUrl}\n` +
          v.nodes.map((n) => `      ${n.html.slice(0, 160)}`).join("\n"),
      )
      .join("\n");

    expect(detalle, `${ruta} incumple WCAG 2.1 AA`).toBe("");
  });
}
