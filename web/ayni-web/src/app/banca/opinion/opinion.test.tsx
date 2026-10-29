import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

import PaginaDeOpinion from "./page";

describe("Encuesta de opinión con el SUS (sesión 24)", () => {
  it("si falta alguna afirmación dice cuál, y con todas da la puntuación", async () => {
    const user = userEvent.setup();
    render(<PaginaDeOpinion />);
    const grupos = screen.getAllByRole("group");
    expect(grupos).toHaveLength(10);

    const elegir = (grupo: HTMLElement) =>
      user.click(grupo.querySelector<HTMLInputElement>('input[value="3"]') as HTMLInputElement);
    for (const grupo of grupos.slice(0, 9)) await elegir(grupo);
    await user.click(screen.getByRole("button", { name: "Enviar mi opinión" }));
    expect(screen.getByRole("alert")).toHaveTextContent("Falta responder la afirmación 10");

    await elegir(grupos[9]);
    await user.click(screen.getByRole("button", { name: "Enviar mi opinión" }));
    expect(screen.getByRole("status")).toHaveTextContent("50 de 100");
  });
});
