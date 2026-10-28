import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";

import { LogroDePrimeraOperacion } from "./LogroDePrimeraOperacion";

describe("Logro de la primera operación (sesión 24)", () => {
  beforeEach(() => localStorage.clear());

  it("reconoce la primera operación y no se repite en las siguientes", async () => {
    const { unmount } = render(<LogroDePrimeraOperacion />);
    expect(await screen.findByRole("status")).toHaveTextContent("¡Primera operación completada!");
    expect(screen.getByRole("link", { name: /cuéntanos/i })).toHaveAttribute(
      "href",
      "/banca/opinion",
    );

    unmount();
    render(<LogroDePrimeraOperacion />);
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });
});
