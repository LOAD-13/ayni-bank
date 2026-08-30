import { expect, test } from "@playwright/test";

/**
 * Comprueba que las pantallas son responsive de verdad.
 *
 * «Responsive» aquí significa dos cosas concretas, y las dos se verifican:
 *
 *   1. **No hay desplazamiento horizontal ni franja vacía** a ninguna anchura.
 *   2. **El contenido refluye.** Al ensanchar la ventana no se ve lo mismo más grande:
 *      se ve *más contenido*, porque las cajas se reacomodan. Es la diferencia entre una
 *      página responsive y una imagen escalada, y es exactamente el defecto que tuvo una
 *      versión anterior de la landing: usaba `zoom`, que anula el zoom del navegador y
 *      hacía que la página se viera idéntica a cualquier tamaño.
 *
 * Nada de esto lo puede detectar Vitest: jsdom no calcula disposición.
 */

const RUTAS = ["/", "/registro", "/ingresar", "/pendiente"] as const;

/** De un móvil pequeño a un ultrapanorámico, pasando por los portátiles habituales. */
const ANCHURAS = [360, 390, 768, 1024, 1280, 1440, 1600, 1920, 2560, 3440] as const;

/** Margen de dos píxeles: el redondeo subpíxel del navegador no es un defecto. */
const TOLERANCIA = 2;

test.describe("Responsividad", () => {
  for (const ruta of RUTAS) {
    for (const ancho of ANCHURAS) {
      test(`${ruta} no desborda ni deja hueco a ${ancho}px`, async ({ page }) => {
        await page.setViewportSize({ width: ancho, height: 900 });
        await page.goto(ruta);
        await page.waitForLoadState("networkidle");

        const medidas = await page.evaluate(() => ({
          visible: document.documentElement.clientWidth,
          contenido: Math.max(document.body.scrollWidth, document.documentElement.scrollWidth),
        }));

        expect(
          medidas.contenido,
          `${ruta} desborda ${medidas.contenido - medidas.visible}px en horizontal`,
        ).toBeLessThanOrEqual(medidas.visible + TOLERANCIA);
      });
    }
  }

  for (const ruta of RUTAS) {
    test(`${ruta} refluye al cambiar el ancho, no se limita a escalar`, async ({ page }) => {
      // La señal que distingue reflujo de escalado es el **tamaño de letra en píxeles
      // reales**. Si la página se escala con `zoom`, el texto mide distinto a cada
      // anchura. Si refluye, mide siempre lo mismo y lo que cambia es la disposición.
      //
      // Se mide sobre `body`, que lleva un tamaño fijo del sistema de diseño, y no sobre
      // el primer titular: los titulares usan `clamp()` a propósito, que es tipografía
      // fluida y no el defecto que esta prueba busca. Medirlos ahí daba un falso positivo.
      await page.goto(ruta);

      await page.setViewportSize({ width: 1920, height: 900 });
      await page.waitForLoadState("networkidle");
      const anchoDelPrimerBloque = await page.evaluate(() =>
        Math.max(0, ...[...document.body.children].map((n) => n.getBoundingClientRect().width)),
      );
      const letraAncho = await page.evaluate(() =>
        parseFloat(getComputedStyle(document.body).fontSize),
      );

      await page.setViewportSize({ width: 640, height: 900 });
      await page.waitForLoadState("networkidle");
      const anchoEstrecho = await page.evaluate(() =>
        Math.max(0, ...[...document.body.children].map((n) => n.getBoundingClientRect().width)),
      );
      const letraEstrecho = await page.evaluate(() =>
        parseFloat(getComputedStyle(document.body).fontSize),
      );

      // El envoltorio ocupa el ancho de la ventana en ambos casos: nada de lienzo fijo.
      // `/pendiente` queda fuera porque es una página de texto centrada, y centrar es
      // una decisión de diseño, no una franja vacía.
      if (ruta !== "/pendiente") {
        expect(anchoDelPrimerBloque).toBeGreaterThan(1920 - TOLERANCIA);
      }
      expect(anchoEstrecho).toBeLessThanOrEqual(640 + TOLERANCIA);

      expect(
        letraEstrecho,
        `${ruta} cambia el tamaño de letra con el ancho: está escalada, no refluyendo`,
      ).toBeCloseTo(letraAncho, 1);
    });
  }

  test("el zoom del navegador muestra más contenido, no lo mismo más pequeño", async ({ page }) => {
    // Alejar con Ctrl y la rueda agranda el viewport en píxeles CSS. En una página
    // responsive eso hace que quepa más; en una escalada con `zoom`, no cambia nada.
    await page.goto("/");

    await page.setViewportSize({ width: 1280, height: 800 });
    await page.waitForLoadState("networkidle");
    const visiblesAlNormal = await page.evaluate(
      () =>
        [...document.querySelectorAll("a")].filter((n) => {
          const c = n.getBoundingClientRect();
          return c.top >= 0 && c.bottom <= window.innerHeight && c.width > 0;
        }).length,
    );

    // Un viewport el doble de ancho y alto equivale a alejar al 50 %.
    await page.setViewportSize({ width: 2560, height: 1600 });
    await page.waitForLoadState("networkidle");
    const visiblesAlAlejar = await page.evaluate(
      () =>
        [...document.querySelectorAll("a")].filter((n) => {
          const c = n.getBoundingClientRect();
          return c.top >= 0 && c.bottom <= window.innerHeight && c.width > 0;
        }).length,
    );

    expect(
      visiblesAlAlejar,
      "al alejar no aparece más contenido: la página está escalada, no es responsive",
    ).toBeGreaterThan(visiblesAlNormal);
  });
});
