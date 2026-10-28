import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import PaginaDeContrasenaNueva, { metadata as metadatosNueva } from "./nueva/page";
import PaginaDeRecuperacion, { metadata } from "./page";

vi.mock("@/lib/api", async (original) => ({
  ...(await original<typeof import("@/lib/api")>()),
  validarEnlaceDeRecuperacion: vi.fn(),
}));

describe("páginas de recuperación", () => {
  it("la de pedir el enlace va en el marco de ingreso y no se indexa", () => {
    render(<PaginaDeRecuperacion />);

    expect(screen.getByRole("heading", { name: "Recupera tu contraseña" })).toBeInTheDocument();
    expect(screen.getByRole("main")).toBeInTheDocument();
    expect(metadata.robots).toEqual({ index: false, follow: false });
  });

  it("la de la contraseña nueva no envía el Referer ni se indexa", async () => {
    window.history.replaceState(null, "", "/recuperar/nueva");
    render(<PaginaDeContrasenaNueva />);

    expect(
      await screen.findByRole("heading", { name: "El enlace ya no es válido" }),
    ).toBeInTheDocument();
    expect(metadatosNueva.referrer).toBe("no-referrer");
    expect(metadatosNueva.robots).toEqual({ index: false, follow: false });
  });
});
