import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";

import { conSesion } from "./usabilidad/apoyo";

/**
 * Movimientos, su detalle y Cuentas, con la API simulada: el resumen del mes, la búsqueda,
 * el paso al detalle con su comprobante y la ficha de la cuenta.
 */

const hace = (minutos: number) => new Date(Date.now() - minutos * 60_000).toISOString();

async function conMovimientos(page: Page) {
  await conSesion(page);
  await page.route("**/api/v1/cuentas/mia/movimientos**", (ruta) =>
    ruta.fulfill({
      json: [
        {
          movimientoId: "m3",
          tipo: "CARGO",
          importe: "150.50",
          moneda: "PEN",
          concepto: "Cena · a **********0031",
          registradoEn: hace(1),
          saldoPosterior: "1049.50",
        },
        {
          movimientoId: "m2",
          tipo: "ABONO",
          importe: "1200.00",
          moneda: "PEN",
          concepto: "Deposito simulado",
          registradoEn: hace(2),
          saldoPosterior: "1200.00",
        },
      ],
    }),
  );
  await page.route("**/api/v1/movimientos/m3", (ruta) =>
    ruta.fulfill({
      json: {
        movimientoId: "m3",
        tipo: "TRANSFERENCIA",
        importe: "150.50",
        moneda: "PEN",
        cuentaOrigen: "**********0030",
        cuentaDestino: "**********0031",
        concepto: "Cena · a **********0031",
        registradoEn: hace(1),
        saldoDisponible: "1049.50",
        sentido: "CARGO",
      },
    }),
  );
}

async function sinViolaciones(page: Page) {
  const { violations } = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
    .analyze();
  expect(violations.map((v) => `[${v.id}] ${v.help}`)).toEqual([]);
}

test.describe("Movimientos y Cuentas", () => {
  test("resume el mes, busca un movimiento y abre su detalle", async ({ page }) => {
    await conMovimientos(page);
    await page.goto("/banca/movimientos");

    await expect(page.getByText(/Entraron S\/\s1\s200\.00 y salieron S\/\s150\.50/)).toBeVisible();
    await expect(page.getByText(/Ahorraste S\/\s1\s049\.50/)).toBeVisible();
    await expect(page.getByText("2 movimientos")).toBeVisible();
    await sinViolaciones(page);

    await page.getByRole("searchbox", { name: "Buscar" }).fill("cena");
    await expect(page.getByText("1 movimiento", { exact: true })).toBeVisible();
    await page.getByRole("button", { name: "Salidas" }).click();
    await page.getByRole("link", { name: /Cena/ }).click();

    await expect(page).toHaveURL(/\/banca\/movimientos\/m3$/);
    await expect(page.getByRole("heading", { name: "Cena · a •••• 0031" })).toBeVisible();
    await expect(page.getByTestId("importe-del-movimiento")).toContainText("S/ 150.50");
    await expect(page.getByRole("button", { name: /descargar comprobante/i })).toBeVisible();
    await expect(
      page.getByRole("link", { name: "Movimientos", exact: true }).first(),
    ).toHaveAttribute("aria-current", "page");
    await sinViolaciones(page);
  });

  test("un movimiento que no existe o es ajeno lo dice sin más detalle", async ({ page }) => {
    await conSesion(page);
    await page.route("**/api/v1/movimientos/otro", (ruta) => ruta.fulfill({ status: 404 }));
    await page.goto("/banca/movimientos/otro");
    await expect(
      page.getByRole("heading", { name: "No encontramos este movimiento" }),
    ).toBeVisible();
  });

  test("Cuentas muestra número, CCI, TREA y comisión cero", async ({ page }) => {
    await conSesion(page);
    await page.goto("/banca/cuentas");

    await expect(page.getByRole("heading", { name: "Cuenta Ayni Soles" })).toBeVisible();
    await expect(page.getByTestId("saldo-de-la-cuenta")).toHaveText("S/ 850.00");
    await expect(page.getByText("999-001-1100-0000-0031-42")).toBeVisible();
    await expect(page.getByText("S/ 0.00")).toBeVisible();
    await sinViolaciones(page);
  });
});
