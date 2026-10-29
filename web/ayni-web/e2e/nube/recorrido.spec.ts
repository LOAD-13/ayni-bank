import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";

import { rotular } from "../apoyo/rotulo";
import { codigoTotp } from "../apoyo/totp";

/**
 * Validación de la versión 2 en la nube (APF3, criterio 5 · T-26).
 *
 * Corre contra la aplicación desplegada en AWS, sin simular nada: gateway, servicios, RDS, SES,
 * S3 y la cámara reales. Se ejecuta solo con `BASE_URL_NUBE` y estas variables, que nunca van
 * al repositorio:
 *   NUBE_CORREO, NUBE_CONTRASENA     cuenta de prueba ya abierta
 *   NUBE_TOTP_SEMILLA                semilla Base32 de su segundo factor (app autenticadora)
 *   NUBE_CUENTA_DESTINO              cuenta Ayni de 14 dígitos que recibe la transferencia
 *   NUBE_CCI_DESTINO                 CCI de otro banco (por omisión, uno que la cámara acepta)
 */
const entorno = (nombre: string) => {
  const valor = process.env[nombre];
  if (!valor) throw new Error(`Falta la variable de entorno ${nombre}`);
  return valor;
};

let ultimoCodigo = "";

/** Un TOTP no se acepta dos veces: si ya se usó el de esta ventana, se espera al siguiente. */
async function codigoNuevo(page: Page) {
  let codigo = codigoTotp(entorno("NUBE_TOTP_SEMILLA"));
  while (codigo === ultimoCodigo) {
    await page.waitForTimeout(2000);
    codigo = codigoTotp(entorno("NUBE_TOTP_SEMILLA"));
  }
  ultimoCodigo = codigo;
  return codigo;
}

async function pegarCodigo(page: Page, codigo: string) {
  await page.getByLabel("Dígito 1 de 6").focus();
  await page.evaluate((texto) => {
    const datos = new DataTransfer();
    datos.setData("text", texto);
    document.activeElement?.dispatchEvent(
      new ClipboardEvent("paste", { clipboardData: datos, bubbles: true }),
    );
  }, codigo);
}

async function sinViolaciones(page: Page) {
  const { violations } = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
    .analyze();
  expect(violations.map((v) => `[${v.id}] ${v.help}`)).toEqual([]);
}

test.describe.configure({ mode: "serial" });

test("la v2 en la nube: ingreso, operaciones, tarjeta y accesibilidad", async ({ page }) => {
  test.setTimeout(240_000);

  await test.step("Portada pública servida desde AWS", async () => {
    await page.goto("/");
    await rotular(page, "Ayni Bank v2 en AWS", "La portada se sirve desde la nube, con HTTPS");
    await expect(page).toHaveTitle(/Ayni/);
    await sinViolaciones(page);
  });

  await test.step("Ingreso con contraseña y segundo factor (identity + SES/TOTP)", async () => {
    await page.goto("/ingresar");
    await rotular(
      page,
      "Ingreso con segundo factor",
      "Contraseña y código TOTP de la app autenticadora",
    );
    await page.getByLabel("Correo electrónico").fill(entorno("NUBE_CORREO"));
    await page.getByLabel("Contraseña", { exact: true }).fill(entorno("NUBE_CONTRASENA"));
    await page.getByRole("button", { name: "Ingresar" }).click();
    await pegarCodigo(page, await codigoNuevo(page));
    await page.getByRole("button", { name: "Verificar y entrar" }).click();
    await expect(page).toHaveURL(/\/banca/);
    const saltar = page.getByRole("button", { name: "Saltar" });
    if (await saltar.isVisible().catch(() => false)) await saltar.click();
    await expect(page.getByRole("heading", { name: /^Hola/ })).toBeVisible();
  });

  await test.step("Panel con el saldo derivado de los asientos (core-banking + RDS)", async () => {
    await rotular(
      page,
      "Panel de la banca",
      "Saldo y movimientos leídos de RDS a través del gateway",
    );
    await expect(page.getByTestId("saldo-total")).toBeVisible();
    await sinViolaciones(page);
  });

  await test.step("Depósito simulado con su comprobante", async () => {
    await page.goto("/banca/depositar");
    await rotular(page, "Depósito", "Partida doble e idempotencia en core-banking");
    await page.getByLabel("Importe en soles").fill("50");
    await page.getByRole("button", { name: "Depositar" }).click();
    await expect(page.getByRole("heading", { name: "Depósito realizado" })).toBeVisible();
  });

  await test.step("Transferencia entre cuentas Ayni con segundo factor", async () => {
    await page.goto("/banca/transferir");
    await rotular(page, "Transferencia", "Datos → revisión → código → comprobante");
    await page.getByLabel("Cuenta Ayni de destino").fill(entorno("NUBE_CUENTA_DESTINO"));
    await page.getByLabel("Importe en soles").fill("10");
    await page.getByLabel("Concepto (opcional)").fill("Validación APF3");
    await page.getByRole("button", { name: "Continuar" }).click();
    await page.getByRole("button", { name: "Confirmar con mi segundo factor" }).click();
    await page.getByLabel("Código de verificación").fill(await codigoNuevo(page));
    await page.getByRole("button", { name: "Confirmar y transferir" }).click();
    await expect(page.getByRole("heading", { name: "Transferencia realizada" })).toBeVisible();
  });

  await test.step("Transferencia a otro banco por la cámara de compensación", async () => {
    await page.goto("/banca/interbancaria");
    await rotular(
      page,
      "Interbancaria",
      "core-banking habla con la cámara por su contrato OpenAPI",
    );
    await page
      .getByLabel("CCI de destino")
      .fill(process.env.NUBE_CCI_DESTINO ?? "00219300001234567886");
    await page.getByLabel("Importe en soles").fill("5");
    await page.getByRole("button", { name: "Continuar" }).click();
    await page.getByRole("button", { name: /segundo factor/i }).click();
    await page.getByLabel("Código de verificación").fill(await codigoNuevo(page));
    await page.getByRole("button", { name: /confirmar/i }).click();
    await expect(page.locator("#titulo-resultado")).toBeVisible();
  });

  await test.step("Tarjeta de débito virtual", async () => {
    await page.goto("/banca/tarjeta");
    await rotular(page, "Tarjeta virtual", "PAN cifrado: solo se muestran los últimos 4 dígitos");
    await expect(page.getByText(/•{4}/).first()).toBeVisible();
    await sinViolaciones(page);
  });
});

test("el gateway en la nube exige sesión para consultar cuentas (401 sin token)", async ({
  request,
}) => {
  const respuesta = await request.get("/api/v1/cuentas/mia");
  expect(respuesta.status()).toBe(401);
});
