import { expect, test, type Page } from "@playwright/test";

/**
 * Las dos pantallas del ingreso · HU-04.
 *
 * **La API se simula con `page.route`.** Estas pruebas comprueban la interfaz —que el
 * segundo paso aparezca, que el bloqueo pinte su cuenta atrás, que el código se reparta al
 * pegarlo—, y para eso levantar el backend entero las haría lentas y, peor, dependientes de
 * que exista un usuario concreto en una base de datos concreta. Que el backend haga lo suyo
 * ya lo comprueban las 203 pruebas de `ayni-identity-service`.
 */
const CORREO = "ana.quispe@ejemplo.pe";
const CONTRASENA = "Cont!rasena2026#";

async function rellenarCredenciales(page: Page) {
  await page.getByLabel("Correo electrónico").fill(CORREO);
  await page.getByLabel("Contraseña", { exact: true }).fill(CONTRASENA);
  await page.getByRole("button", { name: "Ingresar" }).click();
}

test.describe("Ingreso", () => {
  test("el primer paso muestra lo que aprueba el diseño", async ({ page }) => {
    await page.goto("/ingresar");

    await expect(page.getByRole("heading", { name: "Entra a tu banca" })).toBeVisible();
    await expect(page.getByLabel("Correo electrónico")).toBeVisible();
    await expect(page.getByLabel("Recordar este dispositivo")).toBeVisible();
    await expect(page.getByRole("link", { name: /olvidaste tu contraseña/i })).toBeVisible();
    await expect(page.getByRole("link", { name: /biometría del dispositivo/i })).toBeVisible();
  });

  test("la contraseña se puede mostrar y volver a ocultar", async ({ page }) => {
    await page.goto("/ingresar");
    const contrasena = page.getByLabel("Contraseña", { exact: true });
    await contrasena.fill(CONTRASENA);

    await expect(contrasena).toHaveAttribute("type", "password");
    await page.getByRole("button", { name: "Mostrar la contraseña" }).click();
    await expect(contrasena).toHaveAttribute("type", "text");
    await page.getByRole("button", { name: "Ocultar la contraseña" }).click();
    await expect(contrasena).toHaveAttribute("type", "password");
  });

  test("tras las credenciales correctas pide el código de seis dígitos", async ({ page }) => {
    await page.route("**/api/v1/sesion", (ruta) =>
      ruta.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          desafioId: "3f1c2b90-0000-4000-8000-000000000001",
          requiereInscripcion: false,
          uriDeAprovisionamiento: null,
        }),
      }),
    );

    await page.goto("/ingresar");
    await rellenarCredenciales(page);

    await expect(page.getByRole("heading", { name: "Confirma que eres tú" })).toBeVisible();
    await expect(page.getByLabel("Dígito 1 de 6")).toBeVisible();
    await expect(page.getByLabel("Dígito 6 de 6")).toBeVisible();
  });

  test("la primera vez ofrece el QR para dar de alta el segundo factor", async ({ page }) => {
    await page.route("**/api/v1/sesion", (ruta) =>
      ruta.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          desafioId: "3f1c2b90-0000-4000-8000-000000000002",
          requiereInscripcion: true,
          uriDeAprovisionamiento:
            "otpauth://totp/Ayni?secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA&issuer=Ayni",
        }),
      }),
    );

    await page.goto("/ingresar");
    await rellenarCredenciales(page);

    await expect(page.getByText("Activa tu segundo factor")).toBeVisible();
    await expect(page.getByRole("img", { name: /código qr para dar de alta/i })).toBeVisible();

    // Quien no puede escanear tiene que poder teclear el secreto: hay lectores que fallan
    // y escritorios sin cámara enfrente.
    await page.getByRole("button", { name: "No puedo escanearlo" }).click();
    await expect(page.getByText("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")).toBeVisible();
  });

  test("pegar el código entero lo reparte entre las seis casillas", async ({ page }) => {
    await page.route("**/api/v1/sesion", (ruta) =>
      ruta.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          desafioId: "3f1c2b90-0000-4000-8000-000000000003",
          requiereInscripcion: false,
          uriDeAprovisionamiento: null,
        }),
      }),
    );

    await page.goto("/ingresar");
    await rellenarCredenciales(page);

    // Es lo que hace todo el mundo: copiar los seis dígitos de la aplicación de golpe.
    await page.getByLabel("Dígito 1 de 6").focus();
    await page.evaluate(() => {
      const datos = new DataTransfer();
      datos.setData("text", "049713");
      document.activeElement?.dispatchEvent(
        new ClipboardEvent("paste", { clipboardData: datos, bubbles: true }),
      );
    });

    for (const [indice, digito] of [..."049713"].entries()) {
      await expect(page.getByLabel(`Dígito ${indice + 1} de 6`)).toHaveValue(digito);
    }
  });

  test("el ingreso pausado muestra la cuenta atrás y desactiva el botón", async ({ page }) => {
    await page.route("**/api/v1/sesion", (ruta) =>
      ruta.fulfill({
        status: 423,
        contentType: "application/problem+json",
        body: JSON.stringify({
          type: "https://ayni.pe/problemas/ingreso-pausado",
          title: "Ingreso pausado por seguridad",
          status: 423,
          esperaSegundos: 277,
        }),
      }),
    );

    await page.goto("/ingresar");
    await rellenarCredenciales(page);

    await expect(page.getByText("Ingreso pausado por seguridad")).toBeVisible();
    await expect(page.getByText("04:37")).toBeVisible();
    await expect(page.getByRole("button", { name: "Ingresar" })).toBeDisabled();
  });

  test("un error de credenciales no revela si el correo existe", async ({ page }) => {
    await page.route("**/api/v1/sesion", (ruta) =>
      ruta.fulfill({
        status: 401,
        contentType: "application/problem+json",
        body: JSON.stringify({
          title: "No pudimos verificar tus datos",
          status: 401,
          detail: "El correo o la contrasena no son correctos.",
        }),
      }),
    );

    await page.goto("/ingresar");
    await rellenarCredenciales(page);

    // El propio Next inserta un `role="alert"` vacío para anunciar los cambios de ruta,
    // así que hay que quedarse con el del formulario y no con «el único».
    const aviso = page.getByRole("alert").filter({ hasText: /correo|contrase/i });
    await expect(aviso).toContainText("El correo o la contrasena no son correctos.");
    await expect(aviso).not.toContainText(/no existe|no está registrad|no encontrad/i);
  });
});
