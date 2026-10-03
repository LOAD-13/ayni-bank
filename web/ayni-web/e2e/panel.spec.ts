import { expect, test, type Page } from "@playwright/test";

/**
 * Panel de la banca · HU-08, con la API simulada: saludo, saldo con TREA, ocultar saldos,
 * movimientos y el estado de quien entra sin cuenta abierta.
 */

const USUARIO = "6f1c0d2e-0000-4000-8000-000000000001";
const TOKEN = [
  "eyJhbGciOiJIUzI1NiJ9",
  Buffer.from(JSON.stringify({ sub: USUARIO })).toString("base64url"),
  "firma",
].join(".");

async function conSesion(page: Page, cuenta: "abierta" | "pendiente") {
  await page.route("**/api/v1/sesion/renovacion", (ruta) =>
    ruta.fulfill({
      json: { tokenDeAcceso: TOKEN, expiraEn: new Date(Date.now() + 15 * 60_000).toISOString() },
    }),
  );
  await page.route(`**/api/v1/usuarios/${USUARIO}/resumen`, (ruta) =>
    ruta.fulfill({
      json: {
        nombreDePila: "Ana",
        correo: "a***@example.pe",
        estado: cuenta === "abierta" ? "ACTIVO" : "PENDIENTE_VERIFICACION",
        solicitudPendiente: cuenta === "abierta" ? null : "eff2c2e3-8c0e-4e71-aadc-1f6426b518a7",
      },
    }),
  );
  if (cuenta === "pendiente") {
    await page.route("**/api/v1/cuentas/mia**", (ruta) =>
      ruta.fulfill({
        status: 404,
        contentType: "application/problem+json",
        json: { title: "Todavia no tienes cuenta", status: 404, codigo: "SIN_CUENTA" },
      }),
    );
    return;
  }
  await page.route("**/api/v1/cuentas/mia/movimientos**", (ruta) =>
    ruta.fulfill({
      json: [
        {
          movimientoId: "m2",
          tipo: "CARGO",
          importe: "150.00",
          moneda: "PEN",
          concepto: "Cena",
          registradoEn: "2026-10-02T23:10:00Z",
        },
        {
          movimientoId: "m1",
          tipo: "ABONO",
          importe: "1000.00",
          moneda: "PEN",
          concepto: "Deposito simulado",
          registradoEn: "2026-10-02T22:00:00Z",
        },
      ],
    }),
  );
  await page.route("**/api/v1/cuentas/mia", (ruta) =>
    ruta.fulfill({
      json: {
        cuentaId: "c1",
        numero: "00111000000031",
        cci: "99900111000000003142",
        cciFormateado: "999-001-1100-0000-0031-42",
        numeroFormateado: "0011-1000000031",
        moneda: "PEN",
        estado: "ACTIVA",
        trea: "4.50",
        comisionDeMantenimiento: "0.00",
        saldo: "850.00",
      },
    }),
  );
}

test.describe("Panel de la banca", () => {
  test("saluda, muestra el saldo con su TREA y los movimientos", async ({ page }) => {
    await conSesion(page, "abierta");
    await page.goto("/banca");

    await expect(page.getByRole("heading", { name: "Hola, Ana" })).toBeVisible();
    await expect(page.getByTestId("saldo-total")).toHaveText("S/ 850.00");
    await expect(page.getByText("TREA 4.50 %").first()).toBeVisible();
    await expect(page.getByText(/unos S\/ 3\.18 al mes/)).toBeVisible();
    await expect(page.getByRole("cell", { name: "Depósito" })).toBeVisible();

    await page.getByRole("button", { name: "Ocultar saldos" }).click();
    await expect(page.getByTestId("saldo-total")).toHaveText("S/ ••••");
  });

  test("sin cuenta abierta ofrece continuar la verificación", async ({ page }) => {
    await conSesion(page, "pendiente");
    await page.goto("/banca");

    await expect(
      page.getByRole("heading", { name: "Tu cuenta aún no está abierta" }),
    ).toBeVisible();
    await expect(page.getByRole("link", { name: /continuar la verificación/i })).toHaveAttribute(
      "href",
      "/registro/dni-anverso?solicitudId=eff2c2e3-8c0e-4e71-aadc-1f6426b518a7",
    );
  });
});
