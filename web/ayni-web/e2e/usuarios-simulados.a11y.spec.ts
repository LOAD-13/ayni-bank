import { mkdirSync, writeFileSync } from "node:fs";

import { expect, test, type Locator, type Page } from "@playwright/test";

import { conSesion } from "./usabilidad/apoyo";

/**
 * HU-17 · AYNI-294, parte simulada: "usuarios simulados" hacen las seis tareas del protocolo
 * (docs/calidad/prueba-con-lector-de-pantalla.md) SOLO con teclado, encontrando cada control por su
 * rol y su nombre accesible, que es lo que un lector de pantalla anuncia. No hay ratón ni clics.
 *
 * Es una simulacion automatizada, no una prueba con personas: mide si las tareas se pueden completar
 * asi, cuanto tardan y cuantas pulsaciones de Tab cuestan, y deja los resultados en JSON.
 */
type Resultado = { tarea: string; exito: boolean; ms: number; tabs: number; detalle?: string };
const resultados: Resultado[] = [];

/** Tab hasta `destino`, contando las pulsaciones; falla si no llega. */
async function tabHasta(page: Page, destino: Locator, cuenta: { tabs: number }, maximo = 60) {
  for (let i = 0; i < maximo; i++) {
    if (await destino.evaluate((n) => n === document.activeElement).catch(() => false)) return;
    await page.keyboard.press("Tab");
    cuenta.tabs++;
  }
  throw new Error("No se llega con el teclado al control buscado");
}

async function tarea(nombre: string, page: Page, pasos: (c: { tabs: number }) => Promise<void>) {
  const cuenta = { tabs: 0 };
  const inicio = Date.now();
  try {
    await pasos(cuenta);
    resultados.push({ tarea: nombre, exito: true, ms: Date.now() - inicio, tabs: cuenta.tabs });
  } catch (e) {
    resultados.push({
      tarea: nombre,
      exito: false,
      ms: Date.now() - inicio,
      tabs: cuenta.tabs,
      detalle: String(e),
    });
    throw e;
  }
}

test.describe.configure({ mode: "serial" });

test.afterAll(() => {
  mkdirSync("test-results", { recursive: true });
  writeFileSync("test-results/usuarios-simulados.json", JSON.stringify(resultados, null, 2));
});

test("T1 · inicia sesion con correo, contrasena y codigo", async ({ page }) => {
  await page.route("**/api/v1/sesion", (r) =>
    r.fulfill({
      json: { desafioId: "d-1", requiereInscripcion: false, uriDeAprovisionamiento: null },
    }),
  );
  await page.goto("/ingresar");
  await tarea("T1 Iniciar sesion", page, async (c) => {
    await tabHasta(page, page.getByLabel("Correo electrónico"), c);
    await page.keyboard.type("ana.quispe@ejemplo.pe");
    await tabHasta(page, page.getByLabel("Contraseña", { exact: true }), c);
    await page.keyboard.type("Cont!rasena2026#");
    await page.keyboard.press("Enter");
    await tabHasta(page, page.getByLabel("Dígito 1 de 6"), c);
    await expect(page.getByRole("heading", { name: "Confirma que eres tú" })).toBeVisible();
  });
});

test("T2 · averigua su saldo", async ({ page }) => {
  await conSesion(page);
  await page.goto("/banca");
  await tarea("T2 Consultar el saldo", page, async () => {
    await expect(page.getByText(/850\.00/).first()).toBeVisible();
  });
});

test("T3 · transfiere S/ 150 con su segundo factor", async ({ page }) => {
  await conSesion(page);
  await page.route("**/api/v1/confirmaciones**", (r) =>
    r.fulfill({
      json: r.request().url().endsWith("/confirmaciones")
        ? { confirmacionId: "c-1", metodo: "APP_AUTENTICADORA", expiraEn: "2030-01-01T00:00:00Z" }
        : { token: "confirmada", expiraEn: "2030-01-01T00:00:00Z" },
    }),
  );
  await page.route("**/api/v1/transferencias", (r) =>
    r.fulfill({
      json: {
        movimientoId: "m-1",
        tipo: "TRANSFERENCIA",
        importe: "150.00",
        moneda: "PEN",
        cuentaOrigen: "**********0031",
        cuentaDestino: "**********0042",
        concepto: "Prueba",
        registradoEn: "2026-11-27T15:00:00Z",
        saldoDisponible: "700.00",
      },
    }),
  );
  await page.goto("/banca/transferir");
  await tarea("T3 Transferir", page, async (c) => {
    await tabHasta(page, page.getByLabel("Cuenta Ayni de destino"), c);
    await page.keyboard.type("00111000000042");
    await tabHasta(page, page.getByLabel("Importe en soles"), c);
    await page.keyboard.type("150");
    await tabHasta(page, page.getByRole("button", { name: "Continuar" }), c);
    await page.keyboard.press("Enter");
    await tabHasta(page, page.getByRole("button", { name: "Confirmar con mi segundo factor" }), c);
    await page.keyboard.press("Enter");
    await tabHasta(page, page.getByLabel("Código de verificación"), c);
    await page.keyboard.type("123456");
    await tabHasta(page, page.getByRole("button", { name: "Confirmar y transferir" }), c);
    await page.keyboard.press("Enter");
    await expect(page.getByRole("heading", { name: "Transferencia realizada" })).toBeVisible();
  });
});

test("T5 · congela su tarjeta", async ({ page }) => {
  await conSesion(page);
  const controles = {
    estado: "ACTIVA",
    ultimos4: "4821",
    moneda: "PEN",
    limiteDiario: "300.00",
    consumidoHoy: "120.00",
    comprasPorInternet: true,
  };
  await page.route("**/api/v1/tarjetas/mia", (r) =>
    r.fulfill({
      json: {
        ultimos4: "4821",
        numeroEnmascarado: "•••• •••• •••• 4821",
        vencimiento: "10/30",
        estado: controles.estado,
      },
    }),
  );
  await page.route("**/api/v1/tarjetas/mia/controles", (r) => r.fulfill({ json: controles }));
  await page.route("**/api/v1/tarjetas/mia/congelamiento", (r) => {
    controles.estado = r.request().method() === "DELETE" ? "ACTIVA" : "CONGELADA";
    return r.fulfill({ json: controles });
  });
  await page.goto("/banca/tarjeta");
  await tarea("T5 Congelar la tarjeta", page, async (c) => {
    await tabHasta(
      page,
      page.getByRole("button", { name: "Congelar tarjeta" }).filter({ visible: true }),
      c,
    );
    await page.keyboard.press("Enter");
    await expect(page.getByText(/Congelada/i).first()).toBeVisible();
  });
});

test("T4 · intenta transferir mas de lo que tiene y entiende por que no puede", async ({
  page,
}) => {
  await conSesion(page);
  await page.goto("/banca/transferir");
  await tarea("T4 Saldo insuficiente", page, async (c) => {
    await tabHasta(page, page.getByLabel("Cuenta Ayni de destino"), c);
    await page.keyboard.type("00111000000031");
    await tabHasta(page, page.getByLabel("Importe en soles"), c);
    await page.keyboard.type("9000");
    await tabHasta(page, page.getByRole("button", { name: "Continuar" }), c);
    await page.keyboard.press("Enter");
    await expect(page.getByRole("alert").filter({ hasText: /Saldo insuficiente/ })).toBeVisible();
  });
});

test("T6 · cierra la sesion", async ({ page }) => {
  await conSesion(page);
  // Solo el cierre (DELETE /api/v1/sesion): la renovacion de la sesion la simula conSesion.
  await page.route("**/api/v1/sesion", (r) =>
    r.request().method() === "DELETE" ? r.fulfill({ status: 204 }) : r.fallback(),
  );
  await page.goto("/banca");
  await tarea("T6 Cerrar sesion", page, async (c) => {
    await tabHasta(
      page,
      page.getByRole("button", { name: "Cerrar sesión" }).filter({ visible: true }),
      c,
    );
    await page.keyboard.press("Enter");
    await expect(page).not.toHaveURL(/\/banca/);
  });
});
