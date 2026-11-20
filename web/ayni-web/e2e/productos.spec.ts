import { expect, test } from "@playwright/test";

/** Las páginas de productos: el simulador responde y dice que sus tasas son referenciales. */
test.describe("Productos", () => {
  test("el simulador del depósito recalcula al cambiar el plazo", async ({ page }) => {
    await page.goto("/depositos-a-plazo");
    const simulador = page.getByRole("region", { name: "Simula tu depósito" });
    await expect(simulador).toContainText("tasas referenciales");

    // S/ 10 000 a 360 días al 5.60 %: S/ 560.00 de interés.
    await simulador.getByRole("button", { name: "360 días" }).click();
    await expect(simulador).toContainText(/Ganarías\s*S\/\s560\.00/);
    await simulador.getByRole("button", { name: "90 días" }).click();
    await expect(simulador).not.toContainText(/Ganarías\s*S\/\s560\.00/);
  });

  test("el préstamo muestra la cuota y la tasa, y lleva a abrir la cuenta", async ({ page }) => {
    await page.goto("/prestamos");
    const simulador = page.getByRole("region", { name: "Cotiza tu préstamo" });
    await simulador.getByRole("button", { name: "12", exact: true }).click();
    await expect(simulador).toContainText("12 cuotas de");
    await expect(simulador).toContainText("24.90 %");
    await page
      .getByRole("link", { name: /abrir mi cuenta gratis/i })
      .first()
      .click();
    await expect(page).toHaveURL(/\/registro$/);
  });

  test("desde la portada, cada producto tiene su página", async ({ page }) => {
    await page.goto("/productos");
    for (const nombre of [
      "Depósito a plazo",
      "Tarjeta de crédito",
      "Préstamo personal",
      "Seguros simples",
      "Cuenta negocio",
      "Inversiones",
    ]) {
      await expect(
        page.getByRole("main").getByRole("link", { name: new RegExp(nombre) }),
      ).toBeVisible();
    }
  });
});
