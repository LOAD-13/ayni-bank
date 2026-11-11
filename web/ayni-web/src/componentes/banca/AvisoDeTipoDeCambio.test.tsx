import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { violacionesDeAccesibilidad } from "@/test/accesibilidad";

import { AvisoDeTipoDeCambio } from "./AvisoDeTipoDeCambio";

/** HU-12, escenario 2 · AYNI-269. */
describe("AvisoDeTipoDeCambio", () => {
  it("con un tipo en caché dice la fecha y la hora de Lima en que se obtuvo", () => {
    // 23:05 UTC del 10 de noviembre son las 18:05 del 10 en Lima (UTC−5).
    render(<AvisoDeTipoDeCambio enCache obtenidoEn="2026-11-10T23:05:00Z" />);

    const aviso = screen.getByRole("status");
    expect(aviso).toHaveTextContent(/10 nov\.? 2026/);
    expect(aviso).toHaveTextContent(/06:05\s?p\.\s?m\./i);
    expect(aviso).toHaveTextContent("La fuente oficial (SUNAT/BCRP) no respondió");
    expect(aviso).toHaveTextContent("Revísalo antes de confirmar");
  });

  it("con el tipo del día no muestra nada", () => {
    const { container } = render(
      <AvisoDeTipoDeCambio enCache={false} obtenidoEn="2026-11-11T13:00:00Z" />,
    );

    expect(container).toBeEmptyDOMElement();
  });

  it("se puede enlazar con el botón de confirmar", () => {
    render(
      <>
        <AvisoDeTipoDeCambio id="aviso-cambio" enCache obtenidoEn="2026-11-10T23:05:00Z" />
        <button type="button" aria-describedby="aviso-cambio">
          Confirmar conversión
        </button>
      </>,
    );

    expect(
      screen.getByRole("button", { name: "Confirmar conversión" }),
    ).toHaveAccessibleDescription(/Tipo de cambio del 10 nov\.? 2026/);
  });

  it("no presenta violaciones de WCAG 2.1 AA", async () => {
    const { container } = render(<AvisoDeTipoDeCambio enCache obtenidoEn="2026-11-10T23:05:00Z" />);

    expect(await violacionesDeAccesibilidad(container)).toEqual([]);
  });
});
