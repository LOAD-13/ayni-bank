import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

import { conSesion } from "./apoyo";

/**
 * Asistencia y compromiso del usuario (sesión 24, taller de la semana 12): bienvenida en el
 * primer ingreso, pistas en los íconos principales y ayuda dentro de la aplicación.
 */
test.describe("Usabilidad · Bienvenida en el primer ingreso", () => {
  // Navegador sin estrenar: sin la marca que la configuración pone para el resto de pruebas.
  test.use({ storageState: { cookies: [], origins: [] } });

  test("guía la primera vez, es accesible y no vuelve a salir", async ({ page }) => {
    await conSesion(page);
    await page.goto("/banca");

    const dialogo = page.getByRole("dialog");
    await expect(dialogo).toBeVisible();
    await expect(dialogo.getByText("Paso 1 de 4")).toBeVisible();

    const { violations } = await new AxeBuilder({ page })
      .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
      .analyze();
    expect(violations.map((v) => `[${v.id}] ${v.help}`)).toEqual([]);

    for (let i = 0; i < 3; i++) await dialogo.getByRole("button", { name: "Siguiente" }).click();
    await dialogo.getByRole("button", { name: "Empezar" }).click();
    await expect(dialogo).toBeHidden();

    await page.reload();
    await expect(page.getByRole("heading", { name: /hola/i })).toBeVisible();
    await expect(page.getByRole("dialog")).toBeHidden();
  });
});

test.describe("Usabilidad · Pistas y ayuda", () => {
  test("la pista de un acceso rápido se ve también al llegar con el tabulador", async ({
    page,
  }) => {
    await conSesion(page);
    await page.goto("/banca");

    const transferir = page.getByRole("link", { name: /^transferir a otra cuenta/i });
    await transferir.focus();
    await expect(page.getByRole("tooltip").filter({ hasText: /envía dinero/i })).toBeVisible();
    await expect(transferir).toHaveAccessibleDescription(/envía dinero/i);

    await page.keyboard.press("Escape");
    await expect(page.getByRole("tooltip").filter({ hasText: /envía dinero/i })).toBeHidden();
  });

  test("desde una operación se llega a las preguntas frecuentes", async ({ page }) => {
    await conSesion(page);
    await page.goto("/banca/transferir");
    await page.getByRole("link", { name: /preguntas frecuentes/i }).click();
    await expect(page.getByRole("heading", { name: "Ayuda", level: 1 })).toBeVisible();
    await page.getByText("¿Se cobró dos veces?").click();
    await expect(page.getByText(/recibes el comprobante de la primera/i)).toBeVisible();
  });
});
