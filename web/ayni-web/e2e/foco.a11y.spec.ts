import { expect, test, type Page } from "@playwright/test";

import { conSesion } from "./usabilidad/apoyo";

/**
 * Foco visible y coherente · HU-17, AYNI-290 (WCAG 2.1 AA, criterio 2.4.7).
 *
 * Recorre cada pantalla solo con Tab y, en cada parada, comprueba que el elemento enfocado
 * se ve: un contorno de al menos 2 px o el halo del marco del campo. Un control que recibe el
 * foco sin marcarlo deja a quien usa teclado sin saber dónde está.
 */
const PARADAS = 15;

/** Qué indicador lleva el elemento con el foco, o null si no se ve ninguno. */
async function indicadorDelFoco(page: Page) {
  return page.evaluate(() => {
    const el = document.activeElement as HTMLElement | null;
    if (!el || el === document.body) return { nombre: "body", visible: true };
    const nombre = `${el.tagName.toLowerCase()} «${(el.getAttribute("aria-label") ?? el.textContent ?? "").trim().slice(0, 40)}»`;
    const propio = getComputedStyle(el);
    const contorno =
      propio.outlineStyle !== "none" &&
      parseFloat(propio.outlineWidth) >= 2 &&
      propio.outlineColor !== "rgba(0, 0, 0, 0)";
    // El halo solo cuenta si lo enciende el foco: una sombra fija no indica nada.
    const marco = el.closest(".campo");
    const halo =
      !!marco && (marco.matches(":has(:focus-visible)") || marco.matches(":focus-visible"));
    return { nombre, visible: contorno || halo };
  });
}

async function recorrerConTab(page: Page) {
  const sinIndicador: string[] = [];
  for (let i = 0; i < PARADAS; i++) {
    await page.keyboard.press("Tab");
    const foco = await indicadorDelFoco(page);
    if (!foco.visible) sinIndicador.push(foco.nombre);
  }
  return sinIndicador;
}

test.describe("Foco visible en todo el recorrido con teclado", () => {
  test("ingreso: credenciales", async ({ page }) => {
    await page.goto("/ingresar");
    expect(await recorrerConTab(page)).toEqual([]);
  });

  test("ingreso: las seis casillas del código", async ({ page }) => {
    await page.route("**/api/v1/sesion", (ruta) =>
      ruta.fulfill({
        json: {
          desafioId: "3f1c2b90-0000-4000-8000-000000000001",
          requiereInscripcion: false,
          uriDeAprovisionamiento: null,
        },
      }),
    );
    await page.goto("/ingresar");
    await page.getByLabel("Correo electrónico").fill("ana.quispe@ejemplo.pe");
    await page.getByLabel("Contraseña", { exact: true }).fill("Cont!rasena2026#");
    await page.getByRole("button", { name: "Ingresar" }).click();
    await page.getByLabel("Dígito 1 de 6").focus();

    expect((await indicadorDelFoco(page)).visible).toBe(true);
    expect(await recorrerConTab(page)).toEqual([]);
  });

  test("control: si alguien quita el contorno, la prueba lo detecta", async ({ page }) => {
    await page.goto("/ingresar");
    await page.addStyleTag({ content: "*:focus-visible { outline: none !important; }" });
    expect((await recorrerConTab(page)).length).toBeGreaterThan(0);
  });

  for (const ruta of ["/banca", "/banca/transferir", "/banca/tarjeta"]) {
    test(`banca: ${ruta}`, async ({ page }) => {
      await conSesion(page);
      await page.goto(ruta);
      await page.getByRole("main").waitFor();
      expect(await recorrerConTab(page)).toEqual([]);
    });
  }
});
