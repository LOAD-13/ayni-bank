import type { Locator, Page } from "@playwright/test";

/**
 * Lo que comparten las pruebas de usabilidad (T-21 · AYNI-221).
 *
 * La API se simula con `page.route`, como en el resto de e2e: lo que se mide aquí es la
 * interfaz —qué ve, qué entiende y qué puede hacer la persona—, no el backend.
 */

export const USUARIO = "6f1c0d2e-0000-4000-8000-000000000001";

const TOKEN = [
  "eyJhbGciOiJIUzI1NiJ9",
  Buffer.from(JSON.stringify({ sub: USUARIO })).toString("base64url"),
  "firma",
].join(".");

/** Una clienta con la sesión abierta y su cuenta en soles. */
export async function conSesion(page: Page) {
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
        estado: "ACTIVO",
        solicitudPendiente: null,
        estadoSolicitud: null,
      },
    }),
  );
  await page.route("**/api/v1/cuentas/mia/movimientos**", (ruta) => ruta.fulfill({ json: [] }));
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

/** El elemento que tiene el foco ahora mismo. */
export function enfocado(page: Page): Locator {
  return page.locator("*:focus");
}

/**
 * Dónde está y cómo se llama lo que tiene el foco, sin esperar: `null` si el foco salió de
 * la página. Un `locator.boundingBox()` se quedaría esperando a que apareciera.
 */
export async function foco(page: Page) {
  return page.evaluate(() => {
    const nodo = document.activeElement as HTMLElement | null;
    if (!nodo || nodo === document.body) return null;
    const caja = nodo.getBoundingClientRect();
    const nombre =
      nodo.getAttribute("aria-label") ??
      (nodo as HTMLInputElement).labels?.[0]?.innerText ??
      nodo.textContent?.trim().slice(0, 40) ??
      nodo.tagName;
    return {
      nombre: nombre.trim(),
      // Respecto a la ventana: es lo que sirve para recortar una captura.
      caja: { x: caja.x, y: caja.y, width: caja.width, height: caja.height },
      // Respecto a la página: el Tab desplaza la vista, y para comparar posiciones entre
      // paradas hay que descontarlo.
      enPagina: { x: caja.x + window.scrollX, y: caja.y + window.scrollY },
    };
  });
}

/** Pulsa Tab hasta llegar a `destino`; falla si no llega en `maximo` pulsaciones. */
export async function tabularHasta(page: Page, destino: Locator, maximo = 40) {
  for (let i = 0; i < maximo; i++) {
    if (await destino.evaluate((nodo) => nodo === document.activeElement)) return;
    await page.keyboard.press("Tab");
  }
  throw new Error(`Con ${maximo} pulsaciones de Tab no se llega al elemento buscado`);
}
