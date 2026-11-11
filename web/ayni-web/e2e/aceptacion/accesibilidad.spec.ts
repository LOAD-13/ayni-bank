import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";

import { conSesion, tabularHasta } from "../usabilidad/apoyo";

/**
 * HU-17 · Aceptación de accesibilidad (AYNI-295).
 *
 * Implementa los tres escenarios de `accesibilidad.feature`. Cada `test` lleva el nombre del
 * escenario y cada `test.step` el texto exacto de su paso; la prueba final de este fichero
 * compara los dos y falla si alguien cambia uno sin el otro.
 */

const FEATURE = readFileSync(
  fileURLToPath(new URL("accesibilidad.feature", import.meta.url)),
  "utf-8",
);
const FUENTE = readFileSync(fileURLToPath(import.meta.url), "utf-8");
const ETIQUETAS_WCAG_21_AA = ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"];

/** La API de la transferencia: confirmación con segundo factor y comprobante. */
async function conTransferenciaSimulada(page: Page) {
  await page.route("**/api/v1/confirmaciones", (ruta) =>
    ruta.fulfill({
      json: {
        confirmacionId: "c-1",
        metodo: "APP_AUTENTICADORA",
        expiraEn: new Date(Date.now() + 5 * 60_000).toISOString(),
      },
    }),
  );
  await page.route("**/api/v1/confirmaciones/c-1/verificacion", (ruta) =>
    ruta.fulfill({
      json: { token: "confirmacion", expiraEn: new Date(Date.now() + 5 * 60_000).toISOString() },
    }),
  );
  await page.route("**/api/v1/transferencias", (ruta) =>
    ruta.fulfill({
      json: {
        movimientoId: "m-1",
        tipo: "TRANSFERENCIA",
        importe: "150.50",
        moneda: "PEN",
        cuentaOrigen: "****0031",
        cuentaDestino: "****0042",
        concepto: "Cena",
        registradoEn: new Date().toISOString(),
        saldoDisponible: "699.50",
      },
    }),
  );
}

/**
 * Si el elemento con foco se distingue a la vista: su propio contorno, o el marco del campo
 * que lo contiene (los campos de texto mueven el indicador al marco, ver globals.css).
 */
async function focoVisible(page: Page): Promise<{ nombre: string; visible: boolean }> {
  return page.evaluate(() => {
    const nodo = document.activeElement as HTMLElement | null;
    if (!nodo || nodo === document.body) return { nombre: "(ninguno)", visible: false };
    const propio = getComputedStyle(nodo);
    const contorno = propio.outlineStyle !== "none" && parseFloat(propio.outlineWidth) > 0;
    const marco = nodo.closest(".campo");
    const halo = marco ? getComputedStyle(marco).boxShadow !== "none" : false;
    const nombre =
      nodo.getAttribute("aria-label") ??
      (nodo as HTMLInputElement).labels?.[0]?.innerText ??
      nodo.textContent?.trim().slice(0, 40) ??
      nodo.tagName;
    return { nombre: nombre.trim(), visible: contorno || halo };
  });
}

test.describe("Característica: Accesibilidad de la banca con teclado y lector de pantalla", () => {
  test.beforeEach(async ({ page }) => {
    await test.step("Dado que Ana tiene la sesión abierta y una cuenta Ayni en soles con S/ 850.00", async () => {
      await conSesion(page);
    });
  });

  test("Escenario: Transferencia solo con teclado", async ({ page }) => {
    await conTransferenciaSimulada(page);
    // Cualquier clic falla la prueba: el escenario exige completar la operación sin ratón.
    const clics: string[] = [];
    await page.exposeFunction("registrarClicDeRaton", (destino: string) => clics.push(destino));
    await page.addInitScript(() => {
      window.addEventListener(
        "pointerdown",
        (evento) => {
          const destino = evento.target as HTMLElement | null;
          (window as unknown as { registrarClicDeRaton: (d: string) => void }).registrarClicDeRaton(
            destino?.tagName ?? "?",
          );
        },
        true,
      );
    });

    const paradas: { nombre: string; visible: boolean }[] = [];
    /** Tabula hasta el control, comprueba que el foco se ve y lo deja listo para usarlo. */
    async function llegarCon(control: ReturnType<Page["getByRole"]>) {
      await tabularHasta(page, control);
      paradas.push(await focoVisible(page));
    }

    await test.step("Dado que Ana navega usando únicamente el teclado", async () => {
      await page.goto("/banca/transferir");
      await expect(page.getByLabel("Cuenta Ayni de destino")).toBeVisible();
    });

    await test.step("Cuando recorre el flujo completo de transferencia", async () => {
      await llegarCon(page.getByRole("textbox", { name: "Cuenta Ayni de destino" }));
      await page.keyboard.type("00111000000042");
      await llegarCon(page.getByRole("textbox", { name: "Importe en soles" }));
      await page.keyboard.type("150.50");
      await llegarCon(page.getByRole("textbox", { name: "Concepto (opcional)" }));
      await page.keyboard.type("Cena");
      await llegarCon(page.getByRole("button", { name: "Continuar" }));
      await page.keyboard.press("Enter");

      await expect(page.getByRole("heading", { name: "Confirma la transferencia" })).toBeVisible();
      await llegarCon(page.getByRole("button", { name: "Confirmar con mi segundo factor" }));
      await page.keyboard.press("Enter");

      await expect(page.getByRole("heading", { name: "Confirma que eres tú" })).toBeVisible();
      await llegarCon(page.getByRole("textbox", { name: "Código de verificación" }));
      await page.keyboard.type("123456");
      await llegarCon(page.getByRole("button", { name: "Confirmar y transferir" }));
      await page.keyboard.press("Enter");
    });

    await test.step("Entonces puede alcanzar y activar todos los controles mediante tabulación", async () => {
      expect(paradas.map((p) => p.nombre)).toEqual([
        "Cuenta Ayni de destino",
        "Importe en soles",
        "Concepto (opcional)",
        "Continuar",
        "Confirmar con mi segundo factor",
        "Código de verificación",
        "Confirmar y transferir",
      ]);
    });

    await test.step("Y el foco es visible en todo momento", async () => {
      const sinIndicador = paradas.filter((p) => !p.visible).map((p) => p.nombre);
      expect(sinIndicador, "controles sin indicador de foco visible").toEqual([]);
    });

    await test.step("Y completa la transferencia sin usar el ratón", async () => {
      await expect(page.getByText("S/ 699.50").first()).toBeVisible();
      expect(clics, "se usó el ratón durante la transferencia").toEqual([]);
    });
  });

  test("Escenario: Verificación automática en el pipeline", async ({ page }) => {
    // El job «Web · calidad y accesibilidad» falla con cualquier prueba en rojo y es un check
    // obligatorio del PR. Aquí se demuestra que el análisis que corre ahí no es decorativo:
    // detecta una violación real y deja de hacerlo cuando se corrige.
    const analizar = async () =>
      (await new AxeBuilder({ page }).withTags(ETIQUETAS_WCAG_21_AA).analyze()).violations.map(
        (v) => v.id,
      );

    await test.step("Dado que una pantalla de la banca tiene una violación de WCAG 2.1 AA", async () => {
      await page.goto("/banca/transferir");
      await expect(page.getByLabel("Cuenta Ayni de destino")).toBeVisible();
      // Un campo sin etiqueta y una imagen con significado sin texto alternativo: los dos
      // errores que HU-17 nombra en sus criterios.
      await page.evaluate(() => {
        const zona = document.createElement("div");
        zona.id = "violacion-de-prueba";
        zona.innerHTML = '<input type="text" name="sin-etiqueta"><img src="/icon.png">';
        document.querySelector("main")?.append(zona);
      });
    });

    let encontradas: string[] = [];
    await test.step("Cuando se ejecuta el análisis de axe-core del pipeline", async () => {
      encontradas = await analizar();
    });

    await test.step("Entonces el análisis informa la violación", async () => {
      expect(encontradas).toEqual(expect.arrayContaining(["label", "image-alt"]));
    });

    await test.step("Y la misma pantalla sin la violación pasa el análisis", async () => {
      await page.evaluate(() => document.getElementById("violacion-de-prueba")?.remove());
      expect(await analizar()).toEqual([]);
    });
  });

  test("Escenario: Mensaje de error comprensible", async ({ page }) => {
    await test.step("Dado que Ana está en el formulario de transferencia", async () => {
      await page.goto("/banca/transferir");
      await expect(page.getByLabel("Cuenta Ayni de destino")).toBeVisible();
      await page.waitForLoadState("networkidle");
    });

    await test.step("Cuando escribe un importe de S/ 900.00, superior a su saldo, e intenta continuar", async () => {
      await page.getByLabel("Cuenta Ayni de destino").fill("00111000000042");
      await page.getByLabel("Importe en soles").fill("900.00");
      await page.getByRole("button", { name: "Continuar" }).click();
    });

    const importe = page.getByLabel("Importe en soles");
    await test.step("Entonces el sistema explica que el saldo es insuficiente e indica el saldo disponible", async () => {
      await expect(importe).toHaveAttribute("aria-invalid", "true");
      await expect(importe).toHaveAccessibleDescription(/saldo insuficiente/i);
      await expect(importe).toHaveAccessibleDescription(/S\/ 850\.00/);
      await expect(page.getByRole("heading", { name: "Confirma la transferencia" })).toHaveCount(0);
    });

    await test.step("Y el mensaje es anunciado por el lector de pantalla", async () => {
      // role="alert" es una región viva asertiva: el lector lo lee en cuanto aparece.
      await expect(
        page.getByRole("alert").filter({ hasText: /saldo insuficiente/i }),
      ).toBeVisible();
    });
  });
});

test("el feature y las pruebas dicen lo mismo", () => {
  const escenarios = [...FEATURE.matchAll(/^\s*Escenario: (.+)$/gm)].map((m) => m[1].trim());
  expect(escenarios).toEqual([
    "Transferencia solo con teclado",
    "Verificación automática en el pipeline",
    "Mensaje de error comprensible",
  ]);

  // Cada paso del feature tiene que aparecer, literal, como test.step en este fichero.
  // Se toleran saltos de línea tras el paréntesis: Prettier parte los pasos largos.
  const pasos = [...FEATURE.matchAll(/^\s*(Dado|Cuando|Entonces|Y) (.+)$/gm)].map((m) =>
    `${m[1]} ${m[2]}`.trim(),
  );
  const fuente = FUENTE.replace(/test\.step\(\s+/g, "test.step(");
  const sinImplementar = pasos.filter((paso) => !fuente.includes(`test.step("${paso}"`));
  expect(sinImplementar, "pasos del feature sin su test.step").toEqual([]);
});
