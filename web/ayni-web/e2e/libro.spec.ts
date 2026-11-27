import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

/** Libro de reclamaciones: validación anunciada sin recargar, envío y constancia. */
test.describe("Libro de reclamaciones", () => {
  test("avisa lo que falta, registra la hoja y entrega el código con el plazo", async ({
    page,
  }) => {
    let recibido: Record<string, string> | null = null;
    await page.route("**/api/v1/reclamaciones", async (ruta) => {
      recibido = ruta.request().postDataJSON();
      await ruta.fulfill({
        status: 201,
        json: {
          codigo: "LR-2026-000007",
          tipo: "RECLAMO",
          registradaEn: "2026-11-19T20:00:00Z",
          responderAntesDe: "2026-12-10",
        },
      });
    });
    await page.goto("/libro-de-reclamaciones");

    // AC-03: el aviso de una validación rechazada aparece en una región dinámica, sin recargar.
    await page.getByRole("button", { name: "Registrar mi reclamo" }).click();
    const aviso = page.getByText("El DNI tiene 8 dígitos.");
    await expect(aviso).toBeVisible();
    await expect(aviso).toHaveAttribute("aria-live", "assertive");
    expect(recibido).toBeNull();

    await page.getByLabel("Nombre completo").fill("Ana Quispe");
    await page.getByLabel("Número de documento").fill("45678912");
    await page.getByLabel("Correo electrónico").fill("ana.quispe@ejemplo.pe");
    await page.getByLabel("¿Cuál?").fill("Transferencia a otro banco del 18 de noviembre");
    await page.getByLabel("Monto reclamado (opcional)").fill("150,5");
    await page.getByLabel("Detalle").fill("La transferencia figura como enviada pero no llegó.");
    await page.getByLabel("Qué solución esperas").fill("Que me devuelvan el importe.");
    await page.getByRole("button", { name: "Registrar mi reclamo" }).click();

    await expect(page.getByRole("heading", { name: "Registramos tu reclamo" })).toBeFocused();
    await expect(page.getByText("LR-2026-000007").first()).toBeVisible();
    await expect(page.getByText(/10 de diciembre de 2026/)).toBeVisible();
    expect(recibido).toMatchObject({ tipo: "RECLAMO", montoReclamado: "150.50", telefono: "" });

    const { violations } = await new AxeBuilder({ page })
      .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
      .analyze();
    expect(violations.map((v) => `[${v.id}] ${v.help}`)).toEqual([]);
  });
});
