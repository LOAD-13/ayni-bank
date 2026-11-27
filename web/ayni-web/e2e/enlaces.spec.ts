import { expect, test } from "@playwright/test";

/**
 * Verifica que **todo lo que parece pulsable, lo es y lleva a alguna parte**.
 *
 * El diseño de la landing tiene decenas de enlaces y todos llevan a una pantalla completa.
 * La regla del proyecto es que ninguno devuelva 404. Lo único que aún lleva a la página de
 * pendientes es el ingreso con biometría, que se dejó así a propósito.
 */
const RUTAS = ["/", "/registro", "/pendiente"] as const;

/** Acciones del diseño aprobado. Si alguna deja de ser enlace, el test lo detecta. */
const ACCIONES = [
  "Abrir cuenta",
  "Abrir mi cuenta gratis",
  "Ver cómo funciona",
  "Ver tarifario completo",
  "Ver todos los productos",
  "Hablar con un asesor",
  "Cuenta Ayni",
  "Libro de reclamaciones",
  "Tarifario",
  "Centro de ayuda",
] as const;

test.describe("Enlaces", () => {
  test("ningún enlace apunta al vacío", async ({ page }) => {
    for (const ruta of RUTAS) {
      await page.goto(ruta);
      const destinos = await page
        .locator("a[href]")
        .evaluateAll((nodos) => nodos.map((n) => n.getAttribute("href") ?? ""));

      expect(destinos.length, `${ruta} no tiene ni un solo enlace`).toBeGreaterThan(0);
      for (const destino of destinos) {
        expect(destino, `${ruta} tiene un enlace vacío`).not.toMatch(/^(#|\s*)$/);
      }
    }
  });

  test("cada acción del diseño es un enlace de verdad", async ({ page }) => {
    await page.goto("/");
    for (const accion of ACCIONES) {
      const enlace = page.locator("a", { hasText: accion }).first();
      await expect(enlace, `«${accion}» no es un enlace`).toHaveAttribute("href", /.+/);
    }
  });

  test("ningún destino devuelve 404", async ({ page, request }) => {
    const vistos = new Set<string>();
    // La portada y las páginas públicas a las que lleva, un nivel más abajo.
    for (const origen of [
      "/",
      "/ayuda",
      "/canales",
      "/nosotros",
      "/prensa",
      "/blog",
      "/productos",
      "/negocios",
    ]) {
      await page.goto(origen);
      const destinos = await page
        .locator("a[href^='/']")
        .evaluateAll((nodos) => nodos.map((n) => (n.getAttribute("href") ?? "").split("#")[0]));
      for (const destino of destinos) {
        if (!destino || vistos.has(destino)) continue;
        vistos.add(destino);
        const respuesta = await request.get(destino);
        expect(
          respuesta.status(),
          `${destino} (desde ${origen}) responde ${respuesta.status()}`,
        ).toBe(200);
      }
    }
  });

  // `:visible` y no `.first()` a secas: en móvil los enlaces de la barra están recogidos
  // en el menú desplegable y los del escritorio siguen en el DOM, ocultos. `.first()`
  // elegiría uno de esos y el clic esperaría para siempre a algo que nadie puede pulsar.
  test("la portada ya no lleva a pendientes y la biometría sí lo dice", async ({ page }) => {
    await page.goto("/");
    await expect(page.locator("a[href='/pendiente']")).toHaveCount(0);
    await page.goto("/ingresar");
    await page.locator("a[href='/pendiente']:visible").first().click();
    await expect(page.getByRole("heading", { level: 1 })).toHaveText(/todavía no está lista/i);
  });

  test("«Abrir cuenta» lleva al registro", async ({ page }) => {
    await page.goto("/");
    await page.locator("a[href='/registro']:visible").first().click();
    await expect(page).toHaveURL(/\/registro$/);
  });
});
