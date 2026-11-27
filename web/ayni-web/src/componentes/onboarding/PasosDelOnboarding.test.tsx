import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { violacionesDeAccesibilidad } from "@/test/accesibilidad";

import { PasosDelOnboarding } from "./PasosDelOnboarding";

/** HU-17 · AYNI-287: la barra de pasos, con teclado y con lector de pantalla. */
describe("Barra de pasos del registro", () => {
  it("se alcanza con el teclado aunque se desplace en un celular", () => {
    render(<PasosDelOnboarding actual={6} completado />);

    expect(screen.getByRole("navigation", { name: "Progreso del registro" })).toHaveAttribute(
      "tabindex",
      "0",
    );
  });

  it("anuncia los pasos cumplidos y marca el que está en curso", async () => {
    const { container } = render(<PasosDelOnboarding actual={3} />);

    const pasos = screen.getAllByRole("listitem");
    expect(pasos[0]).toHaveTextContent("Tus datos (completado)");
    expect(pasos[1]).toHaveTextContent("Segundo factor (completado)");
    expect(pasos[2]).toHaveAttribute("aria-current", "step");
    expect(pasos[2]).not.toHaveTextContent("(completado)");
    expect(await violacionesDeAccesibilidad(container)).toEqual([]);
  });
});
