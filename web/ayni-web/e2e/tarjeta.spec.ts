import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";

/**
 * Controles de la tarjeta · HU-10 (AYNI-252), con la API simulada: el servidor se imita
 * con estado, de modo que lo que se ve tras cada clic es lo que «respondió», como en la
 * aplicación real.
 */

const USUARIO = "6f1c0d2e-0000-4000-8000-000000000001";
const TOKEN = [
  "eyJhbGciOiJIUzI1NiJ9",
  Buffer.from(JSON.stringify({ sub: USUARIO })).toString("base64url"),
  "firma",
].join(".");

interface Controles {
  estado: "ACTIVA" | "CONGELADA";
  ultimos4: string;
  moneda: string;
  limiteDiario: string;
  consumidoHoy: string;
  comprasPorInternet: boolean;
}

async function conTarjeta(page: Page, tarjeta: Controles | null) {
  await page.route("**/api/v1/sesion/renovacion", (ruta) =>
    ruta.fulfill({
      json: { tokenDeAcceso: TOKEN, expiraEn: new Date(Date.now() + 15 * 60_000).toISOString() },
    }),
  );
  await page.route(`**/api/v1/usuarios/${USUARIO}/resumen`, (ruta) =>
    ruta.fulfill({ json: { nombreDePila: "Ana", correo: "a***@example.pe", estado: "ACTIVO" } }),
  );

  const servidor = tarjeta ? { ...tarjeta } : null;
  await page.route("**/api/v1/tarjetas/mia/**", async (ruta) => {
    if (!servidor) {
      return ruta.fulfill({
        status: 404,
        contentType: "application/problem+json",
        json: { title: "Todavía no tienes tarjeta", status: 404 },
      });
    }
    const peticion = ruta.request();
    if (peticion.url().endsWith("/congelamiento")) {
      servidor.estado = peticion.method() === "POST" ? "CONGELADA" : "ACTIVA";
    } else if (peticion.method() === "PUT") {
      Object.assign(servidor, peticion.postDataJSON());
    }
    return ruta.fulfill({ json: servidor });
  });
}

const ACTIVA: Controles = {
  estado: "ACTIVA",
  ultimos4: "4821",
  moneda: "PEN",
  limiteDiario: "300.00",
  consumidoHoy: "250.00",
  comprasPorInternet: true,
};

test.describe("Controles de la tarjeta", () => {
  test("congela, descongela, apaga las compras por internet y cambia el límite", async ({
    page,
  }) => {
    await conTarjeta(page, ACTIVA);
    await page.goto("/banca/tarjeta");

    await expect(page.getByRole("heading", { name: "Mi tarjeta" })).toBeVisible();
    await expect(page.getByText("Hoy llevas S/ 250.00 de S/ 300.00.")).toBeVisible();

    await page.getByRole("button", { name: "Congelar tarjeta" }).click();
    await expect(page.getByText("Congelada", { exact: true })).toBeVisible();
    await expect(page.getByRole("status")).toContainText("Tu tarjeta quedó congelada");

    await page.getByRole("button", { name: "Descongelar tarjeta" }).click();
    await expect(page.getByText("Activa", { exact: true })).toBeVisible();

    const interruptor = page.getByRole("switch", { name: "Permitir compras por internet" });
    await interruptor.click();
    await expect(interruptor).toHaveAttribute("aria-checked", "false");

    await page.getByLabel("Nuevo límite diario en soles").fill("500");
    await page.getByRole("button", { name: "Guardar límite" }).click();
    await expect(page.getByText("Hoy llevas S/ 250.00 de S/ 500.00.")).toBeVisible();
    // El PUT lleva el valor vigente del otro control: cambiar el límite no reactivó internet.
    await expect(interruptor).toHaveAttribute("aria-checked", "false");
  });

  test("sin tarjeta todavía lo dice, sin controles", async ({ page }) => {
    await conTarjeta(page, null);
    await page.goto("/banca/tarjeta");

    await expect(page.getByRole("heading", { name: "Tu tarjeta aún no está lista" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Congelar tarjeta" })).toHaveCount(0);
  });

  test("la barra lateral lleva a la tarjeta", async ({ page }) => {
    await conTarjeta(page, ACTIVA);
    await page.goto("/banca/tarjeta");

    await expect(page.locator('a[href="/banca/tarjeta"]').first()).toBeAttached();
  });

  test("no presenta violaciones de WCAG 2.1 AA, tampoco con la tarjeta congelada", async ({
    page,
  }) => {
    await conTarjeta(page, { ...ACTIVA, estado: "CONGELADA", comprasPorInternet: false });
    await page.goto("/banca/tarjeta");
    await expect(page.getByRole("heading", { name: "Estado de la tarjeta" })).toBeVisible();

    const { violations } = await new AxeBuilder({ page })
      .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
      .analyze();

    const detalle = violations
      .map(
        (v) =>
          `  [${v.id}] ${v.help}\n` +
          v.nodes.map((n) => `      ${n.html.slice(0, 160)}`).join("\n"),
      )
      .join("\n");
    expect(detalle, "/banca/tarjeta incumple WCAG 2.1 AA").toBe("");
  });
});
