import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { descargarComprobante } from "@/lib/api";

import { DescargarComprobante } from "./DescargarComprobante";

vi.mock("@/componentes/banca/SesionDeBanca", () => ({
  useSesion: () => ({ token: async () => "token-de-acceso" }),
}));

vi.mock("@/lib/api", async (original) => ({
  ...(await original<typeof import("@/lib/api")>()),
  descargarComprobante: vi.fn(),
}));

const descargar = vi.mocked(descargarComprobante);

describe("DescargarComprobante (HU-07)", () => {
  const clic = vi.fn();

  beforeEach(() => {
    descargar.mockReset();
    clic.mockReset();
    URL.createObjectURL = vi.fn(() => "blob:comprobante");
    URL.revokeObjectURL = vi.fn();
    vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(clic);
  });

  afterEach(() => vi.restoreAllMocks());

  it("descarga el PDF con el token y un nombre de archivo propio", async () => {
    descargar.mockResolvedValue(new Blob(["%PDF-"], { type: "application/pdf" }));
    render(<DescargarComprobante movimientoId="m-1" />);

    await userEvent.setup().click(screen.getByRole("button", { name: "Descargar comprobante" }));

    expect(descargar).toHaveBeenCalledWith("token-de-acceso", "m-1");
    expect(clic).toHaveBeenCalledTimes(1);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:comprobante");
  });

  it("en la tabla es un enlace discreto y avisa si falla", async () => {
    descargar.mockRejectedValue(new Error("red"));
    render(<DescargarComprobante movimientoId="m-2" variante="enlace" />);

    await userEvent.setup().click(screen.getByRole("button", { name: "Comprobante" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("No pudimos descargarlo");
    expect(clic).not.toHaveBeenCalled();
  });
});
