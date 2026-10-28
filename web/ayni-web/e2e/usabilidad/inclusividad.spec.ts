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
});
