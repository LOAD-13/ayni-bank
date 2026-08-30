import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { violacionesDeAccesibilidad } from "@/test/accesibilidad";

import Home from "./page";

// Los ficheros `*.a11y.test.tsx` son los que ejecuta `npm run test:a11y`, el
// paso de accesibilidad del pipeline. Cada pantalla que se anada al producto
// trae el suyo: es la forma de que WCAG 2.1 AA sea una comprobacion y no una
// intencion declarada en un documento.
describe("Accesibilidad de la pagina de inicio", () => {
  it("no presenta violaciones de WCAG 2.1 AA detectables por axe-core", async () => {
    const { container } = render(<Home />);

    const violaciones = await violacionesDeAccesibilidad(container);

    expect(violaciones.join("\n")).toBe("");
  });
});
