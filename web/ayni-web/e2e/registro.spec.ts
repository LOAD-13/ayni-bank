import { expect, test } from "@playwright/test";

/**
 * El formulario de registro · HU-01.
 *
 * Estas comprobaciones son de navegador real por necesidad: dos de ellas miran estilos
 * calculados —el contorno del foco y el color del marco— y jsdom no calcula estilos
 * aplicados. Con un entorno simulado pasarían con la página rota, que es exactamente el
 * tipo de prueba que no sirve para nada.
 */
test.describe("Registro", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/registro");
  });

  test("el celular lleva el prefijo +51 fuera del valor que se escribe", async ({ page }) => {
    const celular = page.getByLabel("Celular");
    await celular.fill("987654321");

    // El prefijo se ve, pero no forma parte del valor: el dominio espera nueve dígitos y
    // no tiene que limpiar nada, y nadie puede borrarlo por accidente.
    await expect(page.getByText("+51", { exact: true })).toBeVisible();
    await expect(celular).toHaveValue("987654321");
  });

  test("al enfocar un campo no aparece un recuadro dentro del marco", async ({ page }) => {
    const celular = page.getByLabel("Celular");
    const marco = page.locator(".campo").filter({ has: celular });

    const antes = await marco.evaluate((n) => getComputedStyle(n).borderColor);
    await celular.focus();

    // El <input> no dibuja contorno propio…
    await expect(celular).toHaveCSS("outline-style", "none");
    // …y el indicador de foco se traslada al marco, que cambia de color. Sigue habiendo
    // señal visible de dónde está el teclado, que es lo que pide el criterio 2.4.7.
    expect(await marco.evaluate((n) => getComputedStyle(n).borderColor)).not.toBe(antes);
  });

  test("marca los campos vacíos sin enviar nada al servidor", async ({ page }) => {
    await page.getByRole("button", { name: /continuar con mi dni/i }).click();

    await expect(page.getByText("Escribe tus nombres.")).toBeVisible();
    await expect(page.getByText("El DNI tiene ocho dígitos.")).toBeVisible();
    await expect(page.getByText("Indica tu fecha de nacimiento.")).toBeVisible();
  });

  test("no deja abrir una cuenta a un menor de edad", async ({ page }) => {
    const hace10Anos = new Date();
    hace10Anos.setFullYear(hace10Anos.getFullYear() - 10);

    await page.getByLabel("Fecha de nacimiento").fill(hace10Anos.toISOString().slice(0, 10));
    await page.getByRole("button", { name: /continuar con mi dni/i }).click();

    await expect(page.getByText(/al menos 18 años/i)).toBeVisible();
  });

  test("la política de contraseña se cumple requisito a requisito", async ({ page }) => {
    const contrasena = page.getByLabel("Contraseña", { exact: true });

    await contrasena.fill("corta");
    await expect(page.getByText("Falta").first()).toBeVisible();

    await contrasena.fill("Cont!rasena2026#");
    // Los cinco requisitos del diseño, todos cumplidos.
    await expect(page.getByText("Cumplido")).toHaveCount(5);
  });
});
