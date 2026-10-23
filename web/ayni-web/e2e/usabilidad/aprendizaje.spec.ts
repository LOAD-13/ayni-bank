import { expect, test } from "@playwright/test";

import { conSesion } from "./apoyo";

/**
 * ISO/IEC 25010 · Reconocibilidad de la adecuación y facilidad de aprendizaje.
 *
 * Que quien llega por primera vez entienda para qué sirve cada pantalla y qué se le va a
 * pedir, sin manual y sin aprender por ensayo y error.
 */
test.describe("Usabilidad · Facilidad de aprendizaje", () => {
  test("la portada dice qué es Ayni y cómo empezar sin desplazarse", async ({ page }) => {
    await page.goto("/");

    // Lo primero que se ve tiene que responder «qué es esto» y «qué hago ahora».
    await expect(page.getByRole("heading", { level: 1 })).toBeInViewport();
    await expect(page.getByText(/abre tu cuenta en 5 minutos con tu dni/i)).toBeInViewport();
    await expect(
      page.getByRole("link", { name: /abrir mi cuenta gratis/i }).first(),
    ).toBeInViewport();
  });

  test("cada campo del registro tiene una etiqueta visible que no desaparece al escribir", async ({
    page,
  }) => {
    await page.goto("/registro");
    const campos = page.locator("form input:not([type=hidden])");
    const total = await campos.count();
    expect(total).toBeGreaterThan(5);

    for (let i = 0; i < total; i++) {
      const campo = campos.nth(i);
      // La etiqueta asociada de verdad (<label for>), no un placeholder que se borra.
      const etiqueta = await campo.evaluate((nodo) =>
        Array.from((nodo as HTMLInputElement).labels ?? [])
          .map((l) => l.innerText.trim())
          .join(" "),
      );
      expect(etiqueta, `el campo ${i + 1} no tiene etiqueta`).not.toBe("");

      const tipo = await campo.getAttribute("type");
      if (tipo === "checkbox") continue;
      if (tipo === "date") await campo.fill("1998-03-14");
      else await campo.fill("1");
      await expect(page.getByText(etiqueta, { exact: true }).first()).toBeVisible();
    }
  });

  test("la transferencia anuncia sus límites y el formato antes de pedir nada", async ({
    page,
  }) => {
    await conSesion(page);
    await page.goto("/banca/transferir");

    // Saber de antemano el mínimo, el máximo y cómo es una cuenta Ayni evita aprenderlo a
    // fuerza de rechazos.
    await expect(page.getByText(/desde S\/ 1\.00 hasta S\/ 5 000\.00/i)).toBeVisible();
    await expect(page.getByText(/14 dígitos de la cuenta/i)).toBeVisible();
  });

  test("«Iniciar sesión» en el registro lleva al ingreso, no a una página en construcción", async ({
    page,
  }) => {
    await page.goto("/registro");
    await page.getByRole("link", { name: /ya tengo cuenta/i }).click();

    // Un enlace tiene que llevar a lo que promete; si no, se aprende a no fiarse de ellos.
    await expect(page).toHaveURL(/\/ingresar$/);
    await expect(page.getByRole("heading", { name: "Entra a tu banca" })).toBeVisible();
  });

  test("el registro muestra la política de contraseña antes de escribirla", async ({ page }) => {
    await page.goto("/registro");

    // Descubrir los requisitos al fallarlos convierte la contraseña en adivinanzas.
    await expect(page.getByText(/mínimo 12 caracteres/i)).toBeVisible();
    await expect(page.getByLabel("Contraseña", { exact: true })).toHaveValue("");
  });
});
