import { expect, test, type Locator, type Page } from "@playwright/test";

import { conSesion, focoVisible, tabularHasta } from "./usabilidad/apoyo";

/**
 * Los flujos críticos de la banca solo con teclado · HU-17 (AYNI-289).
 *
 * `aceptacion/accesibilidad.spec.ts` cubre la transferencia entre cuentas. Aquí se recorren los
 * demás —conversión, apertura de la cuenta en dólares, depósito, transferencia a otro banco,
 * controles de la tarjeta y el back-office— con las teclas Tab, Enter, Espacio y las flechas. Cada
 * prueba falla si algún control no se alcanza con Tab, si el foco no se ve en alguna parada, o si
 * la operación hizo falta un clic: se vigila cualquier `pointerdown` del ratón.
 */

/** Se entera de cada clic de ratón; el flujo debe terminar sin ninguno. */
async function vigilarClics(page: Page): Promise<string[]> {
  const clics: string[] = [];
  await page.exposeFunction("registrarClicDeRaton", (destino: string) => clics.push(destino));
  await page.addInitScript(() => {
    window.addEventListener(
      "pointerdown",
      (evento) => {
        (window as unknown as { registrarClicDeRaton: (d: string) => void }).registrarClicDeRaton(
          (evento.target as HTMLElement | null)?.tagName ?? "?",
        );
      },
      true,
    );
  });
  return clics;
}

/** Lleva el foco al control con Tab y anota si se ve; devuelve las paradas sin indicador. */
function recorrido(page: Page) {
  const sinIndicador: string[] = [];
  return {
    sinIndicador,
    async llegar(control: Locator) {
      await tabularHasta(page, control, 60);
      const foco = await focoVisible(page);
      if (!foco.visible) sinIndicador.push(foco.nombre);
    },
  };
}

const CUENTA_EN_DOLARES = {
  cuentaId: "c2",
  numero: "00121000000032",
  cci: "99900121000000003299",
  cciFormateado: "999-001-2100-0000-0032-99",
  numeroFormateado: "0012-1000000032",
  moneda: "USD",
  estado: "ACTIVA",
  trea: "1.20",
  comisionDeMantenimiento: "0.00",
  saldo: "0.00",
};

const COTIZACION = {
  origen: "PEN",
  destino: "USD",
  tipoAplicado: "3.761380",
  tipoReferencia: "3.752000",
  margen: "0.50",
  segmento: "ESTANDAR",
  fuente: "BCRP",
  obtenidoEn: "2026-11-20T18:00:00Z",
  enCache: true,
  importeOrigen: "376.14",
  importeDestino: "100.00",
};

/** La confirmación con segundo factor que piden las transferencias. */
async function conSegundoFactor(page: Page) {
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
}

test.describe("Flujos críticos solo con teclado", () => {
  test.beforeEach(async ({ page }) => {
    await conSesion(page);
  });

  test("conversión: elegir el sentido con flechas, simular y confirmar con el aviso de último tipo", async ({
    page,
  }) => {
    const clics = await vigilarClics(page);
    await page.route(/\/api\/v1\/cuentas\/mia\?moneda=USD/, (ruta) =>
      ruta.fulfill({ json: CUENTA_EN_DOLARES }),
    );
    await page.route("**/api/v1/tipo-cambio/cotizacion**", (ruta) =>
      ruta.fulfill({ json: COTIZACION }),
    );
    await page.route("**/api/v1/conversiones", (ruta) =>
      ruta.fulfill({
        status: 201,
        json: {
          conversionId: "cv-1",
          movimientoId: "m-1",
          importeOrigen: "376.14",
          monedaOrigen: "PEN",
          importeDestino: "100.00",
          monedaDestino: "USD",
          tipoAplicado: "3.761380",
          tipoReferencia: "3.752000",
          margen: "0.50",
          segmento: "ESTANDAR",
          fuente: "BCRP",
          tipoObtenidoEn: "2026-11-20T18:00:00Z",
          enCache: true,
          registradaEn: new Date().toISOString(),
        },
      }),
    );
    const ruta = recorrido(page);

    await page.goto("/banca/convertir");
    await expect(page.getByLabel("Importe en soles")).toBeVisible();

    // Los dos sentidos son un grupo de radio: una sola parada de Tab y flechas dentro.
    await ruta.llegar(page.getByRole("radio", { name: "Soles a dólares" }));
    await page.keyboard.press("ArrowDown");
    await expect(page.getByRole("radio", { name: "Dólares a soles" })).toBeChecked();
    await page.keyboard.press("ArrowUp");
    await expect(page.getByRole("radio", { name: "Soles a dólares" })).toBeChecked();

    await ruta.llegar(page.getByRole("textbox", { name: "Importe en soles" }));
    await page.keyboard.type("376.14");
    await ruta.llegar(page.getByRole("button", { name: "Simular conversión" }));
    await page.keyboard.press("Enter");

    await expect(page.getByRole("heading", { name: "Así quedaría tu conversión" })).toBeVisible();
    const confirmar = page.getByRole("button", { name: "Confirmar conversión" });
    // El aviso de último tipo conocido se repite al llegar al botón (aria-describedby).
    await expect(confirmar).toHaveAttribute("aria-describedby", "aviso-tipo-de-cambio");
    await ruta.llegar(confirmar);
    await page.keyboard.press("Enter");

    await expect(page.getByRole("heading", { name: "Conversión realizada" })).toBeVisible();
    await ruta.llegar(page.getByRole("button", { name: "Hacer otra conversión" }));
    await page.keyboard.press("Space");
    await expect(page.getByLabel("Importe en soles")).toBeVisible();

    expect(ruta.sinIndicador, "paradas sin foco visible").toEqual([]);
    expect(clics, "clics de ratón").toEqual([]);
  });

  test("apertura de la cuenta en dólares: se llega al botón y se abre con Enter", async ({
    page,
  }) => {
    const clics = await vigilarClics(page);
    await page.route(/\/api\/v1\/cuentas\/mia\?moneda=USD/, (ruta) =>
      ruta.fulfill({
        status: 404,
        contentType: "application/problem+json",
        json: { title: "Sin cuenta", status: 404, codigo: "SIN_CUENTA" },
      }),
    );
    await page.route("**/api/v1/cuentas/mia/dolares", (ruta) =>
      ruta.fulfill({ status: 201, json: CUENTA_EN_DOLARES }),
    );
    const ruta = recorrido(page);

    await page.goto("/banca/convertir");
    await expect(
      page.getByRole("heading", { name: "Aún no tienes cuenta en dólares" }),
    ).toBeVisible();
    await ruta.llegar(page.getByRole("button", { name: "Abrir cuenta en dólares" }));
    await page.keyboard.press("Enter");

    await expect(page.getByLabel("Importe en soles")).toBeVisible();
    expect(ruta.sinIndicador, "paradas sin foco visible").toEqual([]);
    expect(clics, "clics de ratón").toEqual([]);
  });

  test("depósito: importe y envío con teclado", async ({ page }) => {
    const clics = await vigilarClics(page);
    await page.route("**/api/v1/cuentas/mia/depositos-simulados", (ruta) =>
      ruta.fulfill({
        status: 201,
        json: {
          movimientoId: "m-9",
          tipo: "DEPOSITO_SIMULADO",
          importe: "100.00",
          moneda: "PEN",
          cuentaOrigen: "**********0000",
          cuentaDestino: "**********0031",
          concepto: "Depósito simulado",
          registradoEn: new Date().toISOString(),
          saldoDisponible: "950.00",
          sentido: "ABONO",
        },
      }),
    );
    const ruta = recorrido(page);

    await page.goto("/banca/depositar");
    await expect(page.getByLabel("Importe en soles")).toBeVisible();
    await ruta.llegar(page.getByRole("textbox", { name: "Importe en soles" }));
    await page.keyboard.type("100");
    await ruta.llegar(page.getByRole("button", { name: "Depositar" }));
    const enviado = page.waitForRequest("**/api/v1/cuentas/mia/depositos-simulados");
    await page.keyboard.press("Enter");
    await enviado;

    expect(ruta.sinIndicador, "paradas sin foco visible").toEqual([]);
    expect(clics, "clics de ratón").toEqual([]);
  });

  test("transferencia a otro banco: CCI, importe, segundo factor y envío", async ({ page }) => {
    const clics = await vigilarClics(page);
    await conSegundoFactor(page);
    await page.route("**/api/v1/interbancarias/validar-cci/**", (ruta) =>
      ruta.fulfill({ json: true }),
    );
    await page.route("**/api/v1/interbancarias", (ruta) =>
      ruta.fulfill({ status: 201, json: { id: "i-1", estado: "COMPLETADA" } }),
    );
    const ruta = recorrido(page);

    await page.goto("/banca/interbancaria");
    await expect(page.getByLabel("CCI de destino")).toBeVisible();
    await ruta.llegar(page.getByRole("textbox", { name: "CCI de destino" }));
    await page.keyboard.type("99900102000000000421");
    await ruta.llegar(page.getByRole("textbox", { name: "Importe en soles" }));
    await page.keyboard.type("100");
    const continuar = page.getByRole("button", { name: "Continuar" });
    await expect(continuar).toBeEnabled();
    await ruta.llegar(continuar);
    await page.keyboard.press("Enter");

    await expect(page.getByRole("heading", { name: "Confirma la transferencia" })).toBeVisible();
    await ruta.llegar(page.getByRole("button", { name: "Confirmar con mi segundo factor" }));
    await page.keyboard.press("Enter");

    await expect(page.getByRole("heading", { name: "Confirma que eres tú" })).toBeVisible();
    await ruta.llegar(page.getByRole("textbox", { name: "Código de verificación" }));
    await page.keyboard.type("123456");
    await ruta.llegar(page.getByRole("button", { name: "Confirmar y transferir" }));
    const enviado = page.waitForRequest("**/api/v1/interbancarias");
    await page.keyboard.press("Enter");
    await enviado;

    expect(ruta.sinIndicador, "paradas sin foco visible").toEqual([]);
    expect(clics, "clics de ratón").toEqual([]);
  });

  test("tarjeta: congelar con Enter, el interruptor con Espacio y el límite con Enter", async ({
    page,
  }) => {
    const clics = await vigilarClics(page);
    const servidor = {
      estado: "ACTIVA",
      ultimos4: "4821",
      moneda: "PEN",
      limiteDiario: "300.00",
      consumidoHoy: "120.00",
      comprasPorInternet: true,
    };
    await page.route("**/api/v1/tarjetas/mia", (ruta) =>
      ruta.fulfill({
        json: {
          ultimos4: servidor.ultimos4,
          numeroEnmascarado: "•••• •••• •••• 4821",
          vencimiento: "10/30",
          estado: servidor.estado,
        },
      }),
    );
    await page.route("**/api/v1/tarjetas/mia/**", async (ruta) => {
      const peticion = ruta.request();
      if (peticion.url().endsWith("/congelamiento")) {
        servidor.estado = peticion.method() === "POST" ? "CONGELADA" : "ACTIVA";
      } else if (peticion.method() === "PUT") {
        Object.assign(servidor, peticion.postDataJSON());
      }
      await ruta.fulfill({ json: servidor });
    });
    const ruta = recorrido(page);

    await page.goto("/banca/tarjeta");
    await expect(page.getByRole("heading", { name: "Mi tarjeta" })).toBeVisible();

    await ruta.llegar(page.getByRole("button", { name: "Congelar tarjeta" }));
    await page.keyboard.press("Enter");
    await expect(page.getByRole("status")).toContainText("Tu tarjeta quedó congelada");

    const interruptor = page.getByRole("switch", { name: "Permitir compras por internet" });
    await ruta.llegar(interruptor);
    await page.keyboard.press("Space");
    await expect(interruptor).toHaveAttribute("aria-checked", "false");

    await ruta.llegar(page.getByRole("textbox", { name: "Nuevo límite diario en soles" }));
    await page.keyboard.type("500");
    await ruta.llegar(page.getByRole("button", { name: "Guardar límite" }));
    await page.keyboard.press("Enter");
    await expect(page.getByText("Hoy llevas S/ 120.00 de S/ 500.00.")).toBeVisible();

    expect(ruta.sinIndicador, "paradas sin foco visible").toEqual([]);
    expect(clics, "clics de ratón").toEqual([]);
  });

  test("back-office: filtrar por acción con el teclado y pasar de página", async ({ page }) => {
    const clics = await vigilarClics(page);
    await conSesion(page, ["auditoria:leer"]);
    await page.route("**/api/v1/auditoria/eventos**", (ruta) => {
      const pagina = Number(new URL(ruta.request().url()).searchParams.get("pagina") ?? "0");
      return ruta.fulfill({
        json: {
          eventos: [
            {
              id: 7 - pagina,
              tipo: "PAN_REVELADO",
              usuarioId: "5d1f3c9e-1b2a-4c3d-8e9f-0a1b2c3d4e5f",
              movimientoId: "m-1",
              motivo: null,
              resultado: "EXITO",
              recurso: "POST /api/v1/tarjetas/mia/pan",
              ip: "203.0.113.5",
              agente: "Firefox",
              ocurridoEn: "2026-11-24T15:00:00Z",
            },
          ],
          pagina,
          tamano: 20,
          total: 45,
        },
      });
    });
    const ruta = recorrido(page);

    await page.goto("/backoffice/auditoria");
    await expect(page.getByRole("table")).toBeVisible();

    await ruta.llegar(page.getByRole("combobox", { name: "Acción" }));
    // En una lista desplegable, escribir las primeras letras elige la opción.
    await page.keyboard.type("PAN");
    await expect(page.getByRole("combobox", { name: "Acción" })).toHaveValue("PAN_REVELADO");
    await ruta.llegar(page.getByRole("button", { name: "Filtrar" }));
    await page.keyboard.press("Enter");

    const siguiente = page.getByRole("button", { name: "Siguiente" });
    await ruta.llegar(siguiente);
    await page.keyboard.press("Enter");
    await expect(page.getByText("Página 2 de 3")).toBeVisible();

    expect(ruta.sinIndicador, "paradas sin foco visible").toEqual([]);
    expect(clics, "clics de ratón").toEqual([]);
  });

  test("control: si alguien quita el contorno del foco, el recorrido lo detecta", async ({
    page,
  }) => {
    await page.route(/\/api\/v1\/cuentas\/mia\?moneda=USD/, (ruta) =>
      ruta.fulfill({ json: CUENTA_EN_DOLARES }),
    );
    await page.goto("/banca/convertir");
    await expect(page.getByLabel("Importe en soles")).toBeVisible();
    await page.addStyleTag({ content: "*:focus-visible { outline: none !important; }" });
    const ruta = recorrido(page);

    await ruta.llegar(page.getByRole("radio", { name: "Soles a dólares" }));

    expect(ruta.sinIndicador.length).toBeGreaterThan(0);
  });

  test("control: la prueba detecta un control al que no se llega con Tab", async ({ page }) => {
    await page.route(/\/api\/v1\/cuentas\/mia\?moneda=USD/, (ruta) =>
      ruta.fulfill({ json: CUENTA_EN_DOLARES }),
    );
    await page.goto("/banca/convertir");
    const campo = page.getByRole("textbox", { name: "Importe en soles" });
    await expect(campo).toBeVisible();
    await campo.evaluate((el) => el.setAttribute("tabindex", "-1"));

    await expect(tabularHasta(page, campo, 60)).rejects.toThrow(/no se llega/);
  });
});
