import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import Home from "./page";

describe("Pagina de inicio", () => {
  it("presenta un unico encabezado de primer nivel", () => {
    render(<Home />);

    // Una pagina con dos <h1> o con ninguno rompe la navegacion por
    // encabezados, que es como recorre el sitio quien usa lector de pantalla.
    const encabezados = screen.getAllByRole("heading", { level: 1 });
    expect(encabezados).toHaveLength(1);
    expect(encabezados[0]).toHaveTextContent("Ayni Bank");
  });

  it("no anuncia supervision regulatoria que Ayni no tiene", () => {
    render(<Home />);

    // Ayni es un proyecto academico. Afirmar supervision de la SBS, cobertura
    // del Fondo de Seguro de Depositos o pertenencia a ASBANC seria una
    // declaracion falsa, no una licencia estetica.
    const texto = document.body.textContent ?? "";
    for (const afirmacion of ["SBS", "Fondo de Seguro", "ASBANC", "ISO 27001"]) {
      expect(texto).not.toContain(afirmacion);
    }
  });
});
