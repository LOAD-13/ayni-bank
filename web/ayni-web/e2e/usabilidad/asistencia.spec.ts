import { expect, test, type Page } from "@playwright/test";

import { conSesion } from "./apoyo";

/** El aviso de la página. Next.js añade su propio anunciador de rutas, vacío, con el mismo rol. */
function alerta(page: Page) {
  return page.getByRole("alert").filter({ hasText: /\S/ });
}

/**
 * ISO/IEC 25010 · Asistencia al usuario y autodescriptividad.
 *
 * Cuando algo sale mal, la interfaz dice qué pasó y qué hacer, y lo dice también a quien
 * usa un lector de pantalla, en el campo que hay que corregir.
 */
test.describe("Usabilidad · Asistencia al usuario", () => {
  test("cada error del registro queda enlazado al campo que hay que corregir", async ({ page }) => {
    await page.goto("/registro");
    await page.getByRole("button", { name: /continuar con mi dni/i }).click();

    const invalidos = page.locator("[aria-invalid=true]");
    await expect(invalidos.first()).toBeVisible();
    const total = await invalidos.count();
    expect(total).toBeGreaterThan(2);

    for (let i = 0; i < total; i++) {
      // El mensaje tiene que leerse al enfocar el campo, no en otro punto de la página.
      await expect(invalidos.nth(i)).toHaveAccessibleDescription(/\S/);
    }
  });

  test("si la red falla al ingresar, el mensaje dice qué hacer", async ({ page }) => {
    await page.route("**/api/v1/sesion", (ruta) => ruta.abort("internetdisconnected"));
    await page.goto("/ingresar");

    await page.getByLabel("Correo electrónico").fill("ana.quispe@ejemplo.pe");
    await page.getByLabel("Contraseña", { exact: true }).fill("Cont!rasena2026#");
    await page.getByRole("button", { name: "Ingresar" }).click();

    await expect(alerta(page)).toContainText(/revisa tu conexión/i);
  });

  test("si no cargan los movimientos, el panel dice qué hacer", async ({ page }) => {
    await conSesion(page);
    await page.route("**/api/v1/cuentas/mia/movimientos**", (ruta) =>
      ruta.fulfill({ status: 503, contentType: "text/plain", body: "" }),
    );
    await page.goto("/banca");

    await expect(page.getByText(/no pudimos cargar tus movimientos/i)).toContainText(
      /inténtalo de nuevo/i,
    );
  });

  test("un error de credenciales se anuncia y deja los datos para reintentar", async ({ page }) => {
    await page.route("**/api/v1/sesion", (ruta) =>
      ruta.fulfill({
        status: 401,
        contentType: "application/problem+json",
        json: { title: "No pudimos verificar tus datos", status: 401 },
      }),
    );
    await page.goto("/ingresar");

    await page.getByLabel("Correo electrónico").fill("ana.quispe@ejemplo.pe");
    await page.getByLabel("Contraseña", { exact: true }).fill("Cont!rasena2026#");
    await page.getByRole("button", { name: "Ingresar" }).click();

    await expect(alerta(page)).toBeVisible();
    // Volver a escribir el correo tras cada intento es esfuerzo inútil.
    await expect(page.getByLabel("Correo electrónico")).toHaveValue("ana.quispe@ejemplo.pe");
  });
});
