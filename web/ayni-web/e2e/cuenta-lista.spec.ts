import { expect, test, type Page } from "@playwright/test";

/**
 * Paso 5 del onboarding · «Tu cuenta está lista».
 *
 * La API se simula porque lo que se comprueba aquí es cómo se comporta la pantalla ante
 * cada respuesta: mientras la cuenta se abre, cuando ya está, y cuando tarda demasiado. Las
 * dos primeras son difíciles de provocar contra el sistema real —el evento tarda segundos,
 * no minutos— y la tercera es directamente imposible sin romper algo a propósito.
 */
const TITULAR = "f5cd13ec-532a-4667-8af7-e98201ba0889";
const RUTA = `/registro/listo?titular=${TITULAR}`;

const CUENTA = {
  cuentaId: "8a0e0000-0000-4000-8000-000000000001",
  numero: "00111000000002",
  cci: "99900101100000020",
  cciFormateado: "999-001-0110-0000-0002-08",
  numeroFormateado: "001-1100000-0-002",
  moneda: "PEN",
  estado: "ACTIVA",
  saldo: "0.00",
  trea: "4.50",
  comisionDeMantenimiento: "0.00",
};

const TITULAR_RESUMEN = {
  nombreDePila: "Ana",
  correo: "a**@ejemplo.pe",
  estado: "ACTIVO",
};

async function responderCon(page: Page, status: number, cuerpo: unknown) {
  await page.route("**/api/v1/usuarios/**", (ruta) =>
    ruta.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(TITULAR_RESUMEN),
    }),
  );
  await page.route("**/api/v1/cuentas/titular/**", (ruta) =>
    ruta.fulfill({
      status,
      contentType: status === 200 ? "application/json" : "application/problem+json",
      body: JSON.stringify(cuerpo),
    }),
  );
}

test.describe("Cuenta lista", () => {
  test("muestra el número, el CCI y el saldo cuando la cuenta ya existe", async ({ page }) => {
    await responderCon(page, 200, CUENTA);
    await page.goto(RUTA);

    await expect(
      page.getByRole("heading", { name: /listo, ana\. tu cuenta ya está abierta/i }),
    ).toBeVisible();
    await expect(page.getByText("001-1100000-0-002")).toBeVisible();
    await expect(page.getByText("999-001-0110-0000-0002-08")).toBeVisible();
    await expect(page.getByText("TREA 4.50 %")).toBeVisible();
    // La proyeccion se calcula desde la TREA que devuelve el catalogo, no esta escrita
    // en la pantalla: 10 000 al 4.50 % son 10 450 al cabo de un ano.
    await expect(page.getByText(/10.450,00|10,450.00/)).toBeVisible();
    await expect(page.getByText("Lo que sigue")).toBeVisible();
  });

  test("mientras se abre, avisa en lugar de dar un error", async ({ page }) => {
    // El 404 es lo esperado durante los segundos que el evento tarda en llegar a
    // core-banking. Tratarlo como un fallo asustaría a quien acaba de darse de alta.
    await responderCon(page, 404, { title: "La cuenta todavia no esta lista", status: 404 });
    await page.goto(RUTA);

    await expect(page.getByRole("heading", { name: "Abriendo tu cuenta" })).toBeVisible();
    await expect(page.getByRole("status")).toBeVisible();
  });

  test("el CCI se puede copiar al portapapeles", async ({ page, context }) => {
    await context.grantPermissions(["clipboard-read", "clipboard-write"]);
    await responderCon(page, 200, CUENTA);
    await page.goto(RUTA);

    await page.getByRole("button", { name: /copiar código de cuenta interbancario/i }).click();

    await expect(
      page.getByRole("button", { name: /código de cuenta interbancario \(cci\) copiado/i }),
    ).toBeVisible();
    // Se copia el CCI sin guiones: es lo que hay que pegar en el formulario de otro banco.
    expect(await page.evaluate(() => navigator.clipboard.readText())).toBe(CUENTA.cci);
  });

  test("sin titular en la URL no se queda cargando para siempre", async ({ page }) => {
    await page.goto("/registro/listo");

    await expect(
      page.getByRole("heading", { name: /no sabemos de qué cuenta/i }),
    ).toBeVisible();
    await expect(page.getByRole("link", { name: "Iniciar sesión" })).toBeVisible();
  });
});
