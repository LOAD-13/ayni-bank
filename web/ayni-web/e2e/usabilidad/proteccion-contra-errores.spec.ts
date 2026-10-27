import { expect, test, type Page } from "@playwright/test";

import { conSesion } from "./apoyo";

/**
 * ISO/IEC 25010 · Protección contra errores del usuario.
 *
 * En una banca un error cuesta dinero. La interfaz tiene que impedir los que se pueden
 * impedir, mostrar la operación antes de ejecutarla y hacer que corregir sea más fácil que
 * equivocarse.
 */

/** Cuenta las veces que se pide iniciar una confirmación con el segundo factor. */
async function contarConfirmaciones(page: Page, retardoMs = 0) {
  const pedidas = { total: 0 };
  await page.route("**/api/v1/confirmaciones", async (ruta) => {
    pedidas.total += 1;
    if (retardoMs) await new Promise((fin) => setTimeout(fin, retardoMs));
    await ruta.fulfill({
      json: {
        confirmacionId: "c-1",
        metodo: "APP_AUTENTICADORA",
        expiraEn: new Date(Date.now() + 5 * 60_000).toISOString(),
      },
    });
  });
  return pedidas;
}

async function rellenarTransferencia(page: Page, importe = "150,50") {
  await page.getByLabel("Cuenta Ayni de destino").fill("001-1100000-0-031");
  await page.getByLabel("Importe en soles").fill(importe);
  await page.getByLabel("Concepto (opcional)").fill("Cena");
  await page.getByRole("button", { name: "Continuar" }).click();
}

test.describe("Usabilidad · Protección contra errores", () => {
  test.beforeEach(async ({ page }) => {
    await conSesion(page);
  });

  test("antes de mover el dinero muestra la operación para revisarla", async ({ page }) => {
    const pedidas = await contarConfirmaciones(page);
    await page.goto("/banca/transferir");
    await rellenarTransferencia(page);

    const resumen = page.getByRole("region", { name: "Confirma la transferencia" });
    await expect(resumen.getByText("00111000000031")).toBeVisible();
    await expect(resumen.getByText("S/ 150.50")).toBeVisible();
    await expect(resumen.getByText("Cena")).toBeVisible();
    // Revisar no compromete nada: todavía no se ha pedido ninguna confirmación.
    expect(pedidas.total).toBe(0);
  });

  test("«Corregir» devuelve el formulario con lo que ya se escribió", async ({ page }) => {
    await page.goto("/banca/transferir");
    await rellenarTransferencia(page);

    await page.getByRole("button", { name: "Corregir" }).click();

    await expect(page.getByLabel("Cuenta Ayni de destino")).toHaveValue("001-1100000-0-031");
    await expect(page.getByLabel("Importe en soles")).toHaveValue("150,50");
    await expect(page.getByLabel("Concepto (opcional)")).toHaveValue("Cena");
  });

  test("pulsar «Confirmar» dos veces seguidas pide una sola confirmación", async ({ page }) => {
    const pedidas = await contarConfirmaciones(page, 800);
    await page.goto("/banca/transferir");
    await rellenarTransferencia(page);

    await page.getByRole("button", { name: "Confirmar con mi segundo factor" }).dblclick();

    await expect(page.getByRole("heading", { name: "Confirma que eres tú" })).toBeVisible();
    expect(pedidas.total).toBe(1);
  });

  test("un importe no válido no avanza, marca el campo y dice cómo arreglarlo", async ({
    page,
  }) => {
    await page.goto("/banca/transferir");
    await rellenarTransferencia(page, "12.345");

    const importe = page.getByLabel("Importe en soles");
    await expect(importe).toHaveAttribute("aria-invalid", "true");
    await expect(importe).toHaveAccessibleDescription(/hasta dos decimales/i);
    await expect(page.getByRole("heading", { name: "Confirma la transferencia" })).toHaveCount(0);
  });
});
