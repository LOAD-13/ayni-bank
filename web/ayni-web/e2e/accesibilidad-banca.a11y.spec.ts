import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";

import { conSesion } from "./usabilidad/apoyo";

/**
 * WCAG 2.1 AA dentro de la banca · HU-17 (AYNI-286).
 *
 * `accesibilidad.a11y.spec.ts` cubre las rutas públicas, pero los flujos que HU-17 nombra
 * —transferir, congelar la tarjeta— viven detrás de la sesión y no se analizaban. Aquí se
 * recorren todas las rutas de `/banca` con la sesión simulada, igual que el resto de e2e, y
 * también los estados que solo aparecen al interactuar: un formulario con errores es justo
 * donde suelen faltar las asociaciones entre el campo y su mensaje.
 *
 * Corre en el job «Web · calidad y accesibilidad» del CI, que es un check obligatorio para
 * fusionar en `develop`: una violación nueva bloquea el Pull Request.
 */

const ETIQUETAS_WCAG_21_AA = ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"];

/** Lo que falta para que cada pantalla de la banca cargue con datos, además de la sesión. */
async function conBancaCompleta(page: Page) {
  await conSesion(page);
  await page.route("**/api/v1/cuentas/mia/rendimiento", (ruta) =>
    ruta.fulfill({
      json: {
        tea: "4.50",
        trea: "4.50",
        treaIgualATea: true,
        tasaVigenteDesde: "2026-08-01",
        moneda: "PEN",
        devengadoDelMes: "0.31250000",
        devengadoDelMesRedondeado: "0.31",
        abonadoTotal: "1.20",
        devengos: [
          { fecha: "2026-11-10", saldoBase: "850.00", tea: "4.50", importe: "0.10416667" },
        ],
      },
    }),
  );
  await page.route("**/api/v1/tarjetas/mia", (ruta) =>
    ruta.fulfill({
      json: {
        ultimos4: "4821",
        numeroEnmascarado: "•••• •••• •••• 4821",
        vencimiento: "10/30",
        estado: "ACTIVA",
      },
    }),
  );
  await page.route("**/api/v1/tarjetas/mia/controles", (ruta) =>
    ruta.fulfill({
      json: {
        estado: "ACTIVA",
        ultimos4: "4821",
        moneda: "PEN",
        limiteDiario: "300.00",
        consumidoHoy: "120.00",
        comprasPorInternet: true,
      },
    }),
  );
}

/** Analiza la página tal como está y devuelve las violaciones listas para leer en el CI. */
async function violacionesWcag(page: Page): Promise<string> {
  const { violations } = await new AxeBuilder({ page }).withTags(ETIQUETAS_WCAG_21_AA).analyze();
  return violations
    .map(
      (v) =>
        `  [${v.id}] ${v.help}\n    ${v.helpUrl}\n` +
        v.nodes.map((n) => `      ${n.html.slice(0, 160)}`).join("\n"),
    )
    .join("\n");
}

/**
 * Cada ruta con un texto que solo aparece cuando la pantalla cargó sus datos. Sin esta
 * espera, una pantalla que cae en «Revisa tu conexión» pasaría el análisis vacía: axe
 * no encuentra violaciones en lo que no se pintó.
 */
const RUTAS_DE_BANCA = [
  { ruta: "/banca", cargada: "S/ 850.00" },
  { ruta: "/banca/transferir", cargada: "Cuenta Ayni de destino" },
  { ruta: "/banca/interbancaria", cargada: "CCI de destino" },
  { ruta: "/banca/depositar", cargada: "el depósito es simulado" },
  { ruta: "/banca/tarjeta", cargada: "Estado de la tarjeta" },
  { ruta: "/banca/configuracion", cargada: "Segundo factor" },
  { ruta: "/banca/ayuda", cargada: "¿Cuánto puedo transferir?" },
  { ruta: "/banca/opinion", cargada: "Diez afirmaciones" },
] as const;

test.describe("WCAG 2.1 AA en la banca", () => {
  test.beforeEach(async ({ page }) => {
    await conBancaCompleta(page);
  });

  for (const { ruta, cargada } of RUTAS_DE_BANCA) {
    test(`${ruta} no presenta violaciones de WCAG 2.1 AA`, async ({ page }) => {
      await page.goto(ruta);
      await expect(page.getByText(cargada).first()).toBeVisible();
      await page.waitForLoadState("networkidle");

      expect(await violacionesWcag(page), `${ruta} incumple WCAG 2.1 AA`).toBe("");
    });
  }

  test("la transferencia con errores de validación tampoco presenta violaciones", async ({
    page,
  }) => {
    await page.goto("/banca/transferir");
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();

    // Continuar con el formulario vacío: aparecen los mensajes de error junto a los campos.
    await page.getByRole("button", { name: /continuar/i }).click();
    await expect(page.getByRole("alert").first()).toBeVisible();

    expect(await violacionesWcag(page), "el formulario con errores incumple WCAG 2.1 AA").toBe("");
  });
});
