import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { MarcoDeBackoffice } from "./MarcoDeBackoffice";

const sesion = vi.hoisted(() => ({ permisos: [] as string[] }));
const token = async () => `a.${btoa(JSON.stringify({ permisos: sesion.permisos }))}.c`;

vi.mock("next/navigation", () => ({ usePathname: () => "/backoffice/auditoria" }));
vi.mock("@/componentes/banca/SesionDeBanca", () => ({ useSesion: () => ({ token }) }));

describe("Marco del back-office", () => {
  beforeEach(() => {
    sesion.permisos = [];
  });

  it("enseña la pista de auditoría solo a quien trae auditoria:leer", async () => {
    sesion.permisos = ["auditoria:leer"];
    render(<MarcoDeBackoffice>contenido</MarcoDeBackoffice>);

    expect(await screen.findByRole("link", { name: "Pista de auditoría" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.getByText("contenido")).toBeInTheDocument();
  });

  it("a quien no lo trae no le enseña la opción", async () => {
    sesion.permisos = ["cuenta:leer"];
    render(<MarcoDeBackoffice>contenido</MarcoDeBackoffice>);

    expect(await screen.findByText("contenido")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Pista de auditoría" })).not.toBeInTheDocument();
  });
});
