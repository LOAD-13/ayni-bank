import { virtual } from "@guidepup/virtual-screen-reader";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";

import { ErrorDeApi, iniciarConfirmacion } from "@/lib/api";

import PaginaDeTransferencia from "./page";

/**
 * HU-17 · AYNI-294, parte automatizada: un lector de pantalla virtual recorre la transferencia y
 * se comprueba lo que anuncia. No sustituye la prueba con personas reales (docs/calidad/
 * prueba-con-lector-de-pantalla.md): la prepara, para que esa sesión no tropiece con fallos ya
 * detectables.
 */
vi.mock("@/componentes/banca/SesionDeBanca", () => ({
  useSesion: () => ({ token: async () => "token-de-acceso" }),
}));

vi.mock("@/lib/api", async (original) => ({
  ...(await original<typeof import("@/lib/api")>()),
  iniciarConfirmacion: vi.fn(),
}));

/** Avanza el lector por la página y devuelve todo lo que anunció. */
async function recorrer(pasos: number) {
  for (let i = 0; i < pasos; i++) {
    await virtual.next();
  }
  return (await virtual.spokenPhraseLog()).join(" | ");
}

describe("Transferencia con lector de pantalla (HU-17)", () => {
  afterEach(async () => {
    await virtual.stop();
  });

  it("anuncia cada campo con su etiqueta y el botón para continuar", async () => {
    const { container } = render(<PaginaDeTransferencia />);
    await virtual.start({ container });

    const dicho = await recorrer(40);

    expect(dicho).toMatch(/Cuenta Ayni de destino/);
    expect(dicho).toMatch(/Importe en soles/);
    expect(dicho).toMatch(/Concepto \(opcional\)/);
    expect(dicho).toMatch(/button, Continuar/);
  });

  it("anuncia el error al confirmar y dice cómo seguir", async () => {
    const user = userEvent.setup();
    vi.mocked(iniciarConfirmacion).mockRejectedValueOnce(new ErrorDeApi({ title: "x" }, 409));
    const { container } = render(<PaginaDeTransferencia />);
    await user.type(screen.getByLabelText("Cuenta Ayni de destino"), "00111000000031");
    await user.type(screen.getByLabelText("Importe en soles"), "10");
    await user.click(screen.getByRole("button", { name: "Continuar" }));
    await user.click(screen.getByRole("button", { name: "Confirmar con mi segundo factor" }));
    await screen.findByRole("alert");

    await virtual.start({ container });
    const dicho = await recorrer(60);

    // El aviso va en role="alert": el lector lo lee como alerta, con el texto que explica qué hacer.
    expect(dicho).toMatch(/alert/);
    expect(dicho).toMatch(/Activa tu segundo factor/);
  });
});
