import { expect, test } from "@playwright/test";

/**
 * Paso final del onboarding · «Verificación aprobada» (ADR-0038).
 *
 * La pantalla ya no consulta la cuenta: el número y el CCI solo se ven con la sesión abierta.
 * Se comprueba que confirme la verificación, lleve a iniciar sesión y no pida nada a
 * `/api/v1/cuentas` sin sesión.
 */
const TITULAR = "f5cd13ec-532a-4667-8af7-e98201ba0889";
const RUTA = `/registro/listo?titular=${TITULAR}`;

test.describe("Verificación aprobada", () => {
  test("confirma la verificación y lleva a iniciar sesión, sin consultar la cuenta", async ({
    page,
  }) => {
    const consultasDeCuenta: string[] = [];
    page.on("request", (peticion) => {
      if (peticion.url().includes("/api/v1/cuentas")) consultasDeCuenta.push(peticion.url());
    });
    await page.route("**/api/v1/usuarios/**", (ruta) =>
      ruta.fulfill({ json: { nombreDePila: "Ana", correo: "a**@ejemplo.pe", estado: "ACTIVO" } }),
    );
    await page.goto(RUTA);

    await expect(
      page.getByRole("heading", { name: /listo, ana\. verificamos tu identidad/i }),
    ).toBeVisible();
    await expect(page.getByText(/a\*\*@ejemplo\.pe/)).toBeVisible();
    await expect(page.getByText("Lo que sigue")).toBeVisible();
    await expect(
      page.getByRole("link", { name: /iniciar sesión para ver mi cuenta/i }),
    ).toHaveAttribute("href", "/ingresar");
    expect(consultasDeCuenta).toEqual([]);
  });

  test("si no llega el nombre, la pantalla sigue siendo correcta", async ({ page }) => {
    await page.route("**/api/v1/usuarios/**", (ruta) =>
      ruta.fulfill({ status: 503, json: { title: "No disponible", status: 503 } }),
    );
    await page.goto(RUTA);

    await expect(
      page.getByRole("heading", { name: "Listo. Verificamos tu identidad." }),
    ).toBeVisible();
    await expect(
      page.getByRole("link", { name: /iniciar sesión para ver mi cuenta/i }),
    ).toBeVisible();
  });
});
