import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";

import { MARCAS } from "@/lib/marcas";

import { AsistenteDeBienvenida, PASOS_DE_LA_BIENVENIDA } from "./AsistenteDeBienvenida";

describe("Asistente de bienvenida (sesión 24)", () => {
  beforeEach(() => localStorage.clear());

  it("en el primer ingreso guía de 3 a 5 pasos con su progreso y no vuelve a salir", async () => {
    const user = userEvent.setup();
    const total = PASOS_DE_LA_BIENVENIDA.length;
    expect(total).toBeGreaterThanOrEqual(3);
    expect(total).toBeLessThanOrEqual(5);

    const { unmount } = render(<AsistenteDeBienvenida />);
    expect(await screen.findByRole("dialog")).toHaveAccessibleName(
      PASOS_DE_LA_BIENVENIDA[0].titulo,
    );
    expect(screen.getByRole("heading")).toHaveFocus();

    for (let paso = 1; paso < total; paso++) {
      expect(screen.getByRole("progressbar")).toHaveAttribute("aria-valuenow", String(paso));
      await user.click(screen.getByRole("button", { name: "Siguiente" }));
    }
    await user.click(screen.getByRole("button", { name: "Empezar" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(localStorage.getItem(MARCAS.bienvenida)).not.toBeNull();

    unmount();
    render(<AsistenteDeBienvenida />);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("Escape lo cierra y cuenta como visto", async () => {
    const user = userEvent.setup();
    render(<AsistenteDeBienvenida />);
    await screen.findByRole("dialog");
    await user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(localStorage.getItem(MARCAS.bienvenida)).not.toBeNull();
  });

  it("el tabulador no se escapa del diálogo", async () => {
    const user = userEvent.setup();
    render(
      <>
        <button type="button">Detrás</button>
        <AsistenteDeBienvenida />
      </>,
    );
    await screen.findByRole("dialog");
    await user.click(screen.getByRole("button", { name: "Siguiente" }));
    for (let i = 0; i < 4; i++) {
      await user.tab();
      expect(screen.getByRole("button", { name: "Detrás" })).not.toHaveFocus();
    }
  });
});
