import { expect, test } from "@playwright/test";

import { enfocado, foco, tabularHasta } from "./apoyo";

/**
 * ISO/IEC 25010 · Operabilidad.
 *
 * Que la interfaz se pueda manejar con facilidad: con el teclado, en el orden en que se
 * lee, viendo siempre dónde está el foco y, en el celular, acertando al pulsar.
 */
test.describe("Usabilidad · Operabilidad", () => {
  test("el ingreso se completa solo con el teclado", async ({ page }) => {
    await page.route("**/api/v1/sesion", (ruta) =>
      ruta.fulfill({
        json: {
          desafioId: "3f1c2b90-0000-4000-8000-000000000001",
          requiereInscripcion: false,
          uriDeAprovisionamiento: null,
        },
      }),
    );
    await page.goto("/ingresar");

    await tabularHasta(page, page.getByLabel("Correo electrónico"));
    await page.keyboard.type("ana.quispe@ejemplo.pe");
    await page.keyboard.press("Tab");
    await expect(enfocado(page)).toHaveAccessibleName("Contraseña");
    await page.keyboard.type("Cont!rasena2026#");
    await page.keyboard.press("Enter");

    await expect(page.getByRole("heading", { name: "Confirma que eres tú" })).toBeVisible();
  });

  test("el tabulador recorre el registro en el orden en que se lee", async ({ page }) => {
    await page.goto("/registro");
    await page.getByLabel("Nombres").focus();
    const fin = page.getByRole("button", { name: /continuar con mi dni/i });

    const recorrido: { nombre: string; x: number; y: number; alto: number }[] = [];
    for (let i = 0; i < 30; i++) {
      const actual = await foco(page);
      if (!actual) break;
      // El campo de fecha recibe varias pulsaciones (día, mes, año) sin dejar de ser el
      // mismo elemento: cuenta como una sola parada.
      const { x, y } = actual.enPagina;
      const ultimo = recorrido.at(-1);
      if (!ultimo || ultimo.x !== x || ultimo.y !== y) {
        recorrido.push({ nombre: actual.nombre, x, y, alto: actual.caja.height });
      }
      if (await fin.evaluate((n) => n === document.activeElement)) break;
      await page.keyboard.press("Tab");
    }

    expect(recorrido.at(-1)?.nombre, "el Tab no llega al botón de enviar").toMatch(
      /continuar con mi dni/i,
    );
    for (let i = 1; i < recorrido.length; i++) {
      const antes = recorrido[i - 1];
      const ahora = recorrido[i];
      // Nunca hacia una fila de arriba: un foco que salta atrás obliga a buscar con la
      // vista dónde fue a parar. Dentro de una misma fila (cajas que se solapan en
      // vertical) se acepta cualquier orden, como el botón principal antes que el enlace.
      const mismaFila = ahora.y < antes.y + antes.alto && antes.y < ahora.y + ahora.alto;
      expect(
        mismaFila || ahora.y > antes.y,
        `el foco salta de «${antes.nombre}» (${Math.round(antes.x)},${Math.round(antes.y)}) a «${ahora.nombre}» (${Math.round(ahora.x)},${Math.round(ahora.y)}) fuera del orden de lectura`,
      ).toBe(true);
    }
  });

  for (const ruta of ["/ingresar", "/registro"]) {
    test(`${ruta}: cada parada del tabulador muestra dónde está el foco`, async ({ page }) => {
      await page.goto(ruta);
      await page.waitForLoadState("networkidle");

      // Se compara lo que se ve alrededor de cada elemento con foco y sin él: si no cambia
      // nada, quien usa teclado no sabe dónde está (WCAG 2.4.7).
      const sinCambio: string[] = [];
      let anterior: { nombre: string; zona: Zona; conFoco: Buffer } | null = null;
      for (let i = 0; i < 40; i++) {
        await page.keyboard.press("Tab");
        if (anterior) {
          const sinFoco = await page.screenshot({ clip: anterior.zona });
          if (sinFoco.equals(anterior.conFoco)) sinCambio.push(anterior.nombre);
        }
        const actual = await foco(page);
        if (!actual) break;
        const zona = alrededor(actual.caja);
        anterior = { nombre: actual.nombre, zona, conFoco: await page.screenshot({ clip: zona }) };
      }

      expect(sinCambio, "elementos sin indicador de foco visible").toEqual([]);
    });
  }

  test("en el celular, cada control mide al menos 24 × 24 px", async ({ page }, info) => {
    test.skip(info.project.name !== "movil", "el tamaño del objetivo se mide en el celular");

    const pequenos: string[] = [];
    for (const ruta of ["/", "/ingresar", "/registro"]) {
      await page.goto(ruta);
      const medidas = await page.evaluate(() => {
        const controles = document.querySelectorAll<HTMLElement>(
          "a[href], button, input:not([type=hidden]), select, textarea, [role=button], [role=switch]",
        );
        return Array.from(controles).flatMap((nodo) => {
          const estilo = getComputedStyle(nodo);
          if (estilo.visibility === "hidden" || estilo.display === "none") return [];
          // WCAG 2.5.8 exceptúa los enlaces dentro de un texto: su tamaño lo da la línea.
          if (estilo.display === "inline" && nodo.closest("p, li")) return [];
          let caja = nodo.getBoundingClientRect();
          // Una casilla se pulsa también por su etiqueta, que cuenta como el objetivo.
          const etiqueta = (nodo as HTMLInputElement).labels?.[0];
          if (etiqueta && ["checkbox", "radio"].includes((nodo as HTMLInputElement).type)) {
            caja = etiqueta.getBoundingClientRect();
          }
          if (caja.width === 0 && caja.height === 0) return [];
          const nombre =
            nodo.getAttribute("aria-label") ??
            (nodo.textContent?.trim() || (nodo as HTMLInputElement).labels?.[0]?.innerText) ??
            nodo.tagName;
          return [
            {
              nombre: nombre.slice(0, 40),
              x: caja.x,
              y: caja.y,
              ancho: caja.width,
              alto: caja.height,
            },
          ];
        });
      });
      for (const m of medidas) {
        if (m.ancho >= 24 && m.alto >= 24) continue;
        // Excepción de espaciado de WCAG 2.5.8: un objetivo pequeño cumple si un círculo de
        // 24 px centrado en él no toca ningún otro control. Lo que falla es tenerlo pegado.
        const cx = m.x + m.ancho / 2;
        const cy = m.y + m.alto / 2;
        const pegado = medidas.some((otro) => {
          if (otro === m) return false;
          const dx = Math.max(otro.x - cx, 0, cx - (otro.x + otro.ancho));
          const dy = Math.max(otro.y - cy, 0, cy - (otro.y + otro.alto));
          return Math.hypot(dx, dy) < 12;
        });
        if (pegado) {
          pequenos.push(`${ruta} «${m.nombre}» ${Math.round(m.ancho)}×${Math.round(m.alto)}`);
        }
      }
    }

    expect(pequenos, "controles por debajo de 24 × 24 px (WCAG 2.5.8)").toEqual([]);
  });
});

type Zona = { x: number; y: number; width: number; height: number };

/** La caja del elemento con un margen, donde se dibujan los contornos de foco. */
function alrededor(caja: Zona): Zona {
  const margen = 4;
  return {
    x: Math.max(0, caja.x - margen),
    y: Math.max(0, caja.y - margen),
    width: caja.width + margen * 2,
    height: caja.height + margen * 2,
  };
}
