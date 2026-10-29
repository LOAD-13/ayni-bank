import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

import { PISTAS_DEL_PANEL } from "@/componentes/banca/pistas";

import { LARGO_MAXIMO_DE_PISTA, Pista } from "./Pista";

describe("Pista de los íconos principales (sesión 24)", () => {
  it("queda enlazada al control: el lector de pantalla la lee al enfocarlo", () => {
    render(
      <Pista texto="Copia tu número de cuenta.">
        <button type="button">Copiar</button>
      </Pista>,
    );
    expect(screen.getByRole("button", { name: "Copiar" })).toHaveAccessibleDescription(
      "Copia tu número de cuenta.",
    );
  });

  it("se muestra al enfocar y Escape la cierra sin mover el foco (WCAG 1.4.13)", async () => {
    const user = userEvent.setup();
    render(
      <Pista texto="Ayuda">
        <button type="button">Acción</button>
      </Pista>,
    );
    const pista = screen.getByRole("tooltip", { hidden: true });
    expect(pista.className).toMatch(/group-focus-within:block/);

    await user.tab();
    await user.keyboard("{Escape}");
    expect(pista.className).not.toMatch(/group-focus-within:block/);
    expect(screen.getByRole("button")).toHaveFocus();
  });

  it("las cinco del panel caben en 80 caracteres", () => {
    const textos = Object.values(PISTAS_DEL_PANEL);
    expect(textos).toHaveLength(5);
    for (const texto of textos) expect(texto.length).toBeLessThanOrEqual(LARGO_MAXIMO_DE_PISTA);
  });
});
