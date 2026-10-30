import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";

import { rotular } from "../apoyo/rotulo";

/**
 * Validación de la versión 2 en la nube, parte pública (APF3, criterio 5 · T-26).
 *
 * Lo que cualquier visitante ve de la aplicación desplegada en AWS, sin cuenta: la portada y
 * las cabeceras de seguridad que pone Caddy, las validaciones del registro y del ingreso, la
 * recuperación de contraseña y que el gateway no entrega datos sin sesión. No crea nada en
 * producción: ningún formulario llega a enviarse con datos válidos.
 */
async function sinViolaciones(page: Page) {
  const { violations } = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
    .analyze();
  expect(violations.map((v) => `[${v.id}] ${v.help}`)).toEqual([]);
}

test.describe.configure({ mode: "serial" });

test("la portada se sirve por HTTPS con las cabeceras de seguridad", async ({ page }) => {
  const respuesta = await page.goto("/");
  await rotular(page, "Ayni Bank v2 en AWS", "EC2 + RDS + S3, servida por HTTPS desde la nube");
  await expect(page).toHaveTitle(/Banca digital/);
  expect(page.url()).toMatch(/^https:/);

  const cabeceras = respuesta!.headers();
  await rotular(page, "Cabeceras de seguridad", "HSTS, CSP, X-Frame-Options: DENY y nosniff");
  expect(cabeceras["strict-transport-security"]).toContain("max-age=");
  expect(cabeceras["content-security-policy"]).toContain("frame-ancestors 'none'");
  expect(cabeceras["x-frame-options"]).toBe("DENY");
  expect(cabeceras["x-content-type-options"]).toBe("nosniff");

  await rotular(page, "Accesibilidad de la portada", "axe-core · WCAG 2.1 AA sin violaciones");
  await sinViolaciones(page);
});

test("el registro valida en el navegador sin enviar nada al servidor", async ({ page }) => {
  await page.goto("/registro");
  await rotular(page, "Registro · HU-01", "Campos vacíos: se marcan sin llamar a la API");
  await page.getByRole("button", { name: /continuar con mi dni/i }).click();
  await expect(page.getByText("Escribe tus nombres.")).toBeVisible();
  await expect(page.getByText("El DNI tiene ocho dígitos.")).toBeVisible();
  await expect(page.getByText("Indica tu fecha de nacimiento.")).toBeVisible();

  await rotular(page, "Registro · mayoría de edad", "Un menor de 18 años no puede abrir cuenta");
  const hace10Anos = new Date();
  hace10Anos.setFullYear(hace10Anos.getFullYear() - 10);
  await page.getByLabel("Fecha de nacimiento").fill(hace10Anos.toISOString().slice(0, 10));
  await page.getByRole("button", { name: /continuar con mi dni/i }).click();
  await expect(page.getByText(/al menos 18 años/i)).toBeVisible();

  await rotular(
    page,
    "Registro · política de contraseña",
    "Cinco requisitos, cada uno se marca al cumplirse",
  );
  const contrasena = page.getByLabel("Contraseña", { exact: true });
  await contrasena.fill("corta");
  await expect(page.getByText("Falta").first()).toBeVisible();
  await contrasena.fill("Cont!rasena2026#");
  await expect(page.getByText("Cumplido")).toHaveCount(5);
  await sinViolaciones(page);
});

test("el ingreso y la recuperación se muestran y son accesibles", async ({ page }) => {
  await page.goto("/ingresar");
  await rotular(page, "Ingreso · HU-04", "Correo, contraseña y después el segundo factor");
  await expect(page.getByRole("heading", { name: "Entra a tu banca" })).toBeVisible();
  const contrasena = page.getByLabel("Contraseña", { exact: true });
  await contrasena.fill("Cont!rasena2026#");
  await page.getByRole("button", { name: "Mostrar la contraseña" }).click();
  await expect(contrasena).toHaveAttribute("type", "text");
  await page.getByRole("button", { name: "Ocultar la contraseña" }).click();
  await expect(contrasena).toHaveAttribute("type", "password");
  await sinViolaciones(page);

  await page.getByRole("link", { name: /olvidaste tu contraseña/i }).click();
  await expect(page).toHaveURL(/\/recuperar/);
  await rotular(page, "Recuperar contraseña · HU-21", "El código llega por correo (Amazon SES)");
  await sinViolaciones(page);
});

test("el gateway en la nube exige sesión para consultar cuentas (401 sin token)", async ({
  page,
  request,
}) => {
  await page.goto("/");
  await rotular(page, "Gateway · T-37", "Sin token, ninguna consulta de cuentas llega al servicio");
  for (const ruta of ["/api/v1/cuentas/mia", "/api/v1/cuentas/mia/movimientos"]) {
    expect((await request.get(ruta)).status()).toBe(401);
  }
});
