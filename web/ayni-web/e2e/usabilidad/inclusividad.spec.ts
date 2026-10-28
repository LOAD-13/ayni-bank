import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

import { conSesion } from "./apoyo";

/**
 * ISO/IEC 25010 · Inclusividad.
 *
 * Que la interfaz respete las preferencias que la persona ya configuró en su sistema. Lo
 * del contraste y los lectores de pantalla lo cubren las pruebas de axe; aquí, el
 * movimiento.
 */
test.describe("Usabilidad · Inclusividad", () => {
  test("con «reducir movimiento» activado, nada sigue animándose", async ({ page }) => {
    await page.emulateMedia({ reducedMotion: "reduce" });
    await conSesion(page);
    // La cuenta tarda en llegar: mientras tanto se pinta el esqueleto de carga, que es
    // justo lo que se anima.
    await page.route("**/api/v1/cuentas/mia", async (ruta) => {
      await new Promise((fin) => setTimeout(fin, 3_000));
      await ruta.fallback();
    });
    await page.goto("/banca");
    await expect(page.getByText("Cargando tu resumen…")).toBeAttached();

    // Quien pide menos movimiento puede marearse con lo que parpadea o gira sin parar
    // (WCAG 2.3.3). Una animación de duración casi nula cumple su papel sin moverse.
    const enMovimiento = await page.evaluate(() =>
      document
        .getAnimations()
        .filter((a) => {
          const tiempo = a.effect?.getComputedTiming();
          return a.playState === "running" && Number(tiempo?.activeDuration ?? 0) > 1;
        })
        .map((a) => (a.effect as KeyframeEffect | null)?.target?.className?.toString() ?? "?"),
    );

    expect(enMovimiento, "animaciones en marcha pese a «reducir movimiento»").toEqual([]);
  });

  // Las páginas públicas las revisa accesibilidad.a11y.spec.ts y la tarjeta, tarjeta.spec.ts.
  // Faltaba la banca con la sesión abierta, que es donde el cliente pasa su tiempo.
  for (const ruta of [
    "/banca",
    "/banca/transferir",
    "/banca/interbancaria",
    "/banca/depositar",
    "/banca/configuracion",
  ]) {
    test(`${ruta} no presenta violaciones de WCAG 2.1 AA con la sesión abierta`, async ({
      page,
    }) => {
      await conSesion(page);
      await page.goto(ruta);
      await page.waitForLoadState("networkidle");

      const { violations } = await new AxeBuilder({ page })
        .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
        .analyze();

      expect(violations.map((v) => `[${v.id}] ${v.help}`)).toEqual([]);
    });
  }
});
