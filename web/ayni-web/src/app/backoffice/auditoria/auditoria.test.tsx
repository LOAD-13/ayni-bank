import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { consultarAuditoria, ErrorDeApi, type PaginaDeAuditoria } from "@/lib/api";

import PaginaDeAuditoriaDeBackoffice from "./page";

const sesion = vi.hoisted(() => ({ permisos: ["auditoria:leer"] as string[] }));

// El mismo `token` en cada render, como el de la sesión real: si cambiara, la página volvería a pedir sin parar.
const token = async () => `a.${btoa(JSON.stringify({ permisos: sesion.permisos }))}.c`;
vi.mock("@/componentes/banca/SesionDeBanca", () => ({ useSesion: () => ({ token }) }));

vi.mock("@/lib/api", async (original) => ({
  ...(await original<typeof import("@/lib/api")>()),
  consultarAuditoria: vi.fn(),
}));

const consultar = vi.mocked(consultarAuditoria);

const ANA = "5d1f3c9e-1b2a-4c3d-8e9f-0a1b2c3d4e5f";

function pagina(parcial: Partial<PaginaDeAuditoria> = {}): PaginaDeAuditoria {
  return {
    eventos: [
      {
        id: 7,
        tipo: "TRANSFERENCIA_REALIZADA",
        usuarioId: ANA,
        movimientoId: "m-1",
        motivo: null,
        resultado: "EXITO",
        recurso: "POST /api/v1/transferencias",
        ip: "203.0.113.5",
        agente: "Firefox",
        ocurridoEn: "2026-11-24T15:00:00Z",
      },
      {
        id: 6,
        tipo: "OPERACION_RECHAZADA",
        usuarioId: ANA,
        movimientoId: null,
        motivo: "SALDO_INSUFICIENTE",
        resultado: "RECHAZADO",
        recurso: null,
        ip: null,
        agente: null,
        ocurridoEn: "2026-11-24T14:00:00Z",
      },
    ],
    pagina: 0,
    tamano: 20,
    total: 2,
    ...parcial,
  };
}

describe("Pista de auditoría del back-office", () => {
  beforeEach(() => {
    consultar.mockReset();
    sesion.permisos = ["auditoria:leer"];
  });

  it("con el permiso lista los registros: actor, acción, recurso, IP, resultado y detalle", async () => {
    consultar.mockResolvedValue(pagina());
    render(<PaginaDeAuditoriaDeBackoffice />);

    const tabla = await screen.findByRole("table");

    expect(
      within(tabla)
        .getAllByRole("columnheader")
        .map((c) => c.textContent),
    ).toEqual(["Momento", "Usuario", "Acción", "Recurso", "IP", "Resultado", "Detalle"]);
    expect(within(tabla).getByText("POST /api/v1/transferencias")).toBeInTheDocument();
    expect(within(tabla).getByText("203.0.113.5")).toBeInTheDocument();
    expect(within(tabla).getByText("Rechazado")).toBeInTheDocument();
    expect(within(tabla).getByText("SALDO_INSUFICIENTE")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "2 registros" })).toBeInTheDocument();
  });

  it("es de solo lectura: no hay más botones que filtrar y paginar", async () => {
    consultar.mockResolvedValue(pagina());
    render(<PaginaDeAuditoriaDeBackoffice />);
    await screen.findByRole("table");

    expect(screen.getAllByRole("button").map((b) => b.textContent)).toEqual([
      "Filtrar",
      "Anterior",
      "Siguiente",
    ]);
  });

  it("sin el permiso no pide nada y explica por qué", async () => {
    sesion.permisos = ["cuenta:leer"];
    render(<PaginaDeAuditoriaDeBackoffice />);

    expect(await screen.findByRole("alert")).toHaveTextContent(/no permite leer/i);
    expect(consultar).not.toHaveBeenCalled();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  it("si el servidor responde 403 también se muestra sin acceso, aunque el token dijera otra cosa", async () => {
    consultar.mockRejectedValue(
      new ErrorDeApi({ codigo: "PERMISO_INSUFICIENTE", status: 403 }, 403),
    );
    render(<PaginaDeAuditoriaDeBackoffice />);

    expect(await screen.findByRole("alert")).toHaveTextContent(/no permite leer/i);
  });

  it("filtrar envía usuario, acción y fechas, y vuelve a la primera página", async () => {
    const user = userEvent.setup();
    consultar.mockResolvedValue(pagina());
    render(<PaginaDeAuditoriaDeBackoffice />);
    await screen.findByRole("table");

    await user.type(screen.getByLabelText("Usuario"), ANA);
    await user.selectOptions(screen.getByLabelText("Acción"), "PAN_REVELADO");
    await user.type(screen.getByLabelText("Desde"), "2026-11-01");
    await user.type(screen.getByLabelText("Hasta"), "2026-11-24");
    await user.click(screen.getByRole("button", { name: "Filtrar" }));

    expect(consultar).toHaveBeenLastCalledWith(expect.any(String), {
      usuarioId: ANA,
      tipo: "PAN_REVELADO",
      desde: "2026-11-01",
      hasta: "2026-11-24",
      pagina: 0,
      tamano: 20,
    });
  });

  it("un usuario mal escrito o un rango al revés no llegan al servidor y dicen cómo arreglarlo", async () => {
    const user = userEvent.setup();
    consultar.mockResolvedValue(pagina());
    render(<PaginaDeAuditoriaDeBackoffice />);
    await screen.findByRole("table");
    consultar.mockClear();

    await user.type(screen.getByLabelText("Usuario"), "ana");
    await user.type(screen.getByLabelText("Desde"), "2026-11-24");
    await user.type(screen.getByLabelText("Hasta"), "2026-11-01");
    await user.click(screen.getByRole("button", { name: "Filtrar" }));

    expect(consultar).not.toHaveBeenCalled();
    expect(screen.getByLabelText("Usuario")).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByLabelText("Hasta")).toHaveAttribute("aria-invalid", "true");
  });

  it("la paginación sigue con el filtro aplicado y no con lo que haya a medio escribir", async () => {
    const user = userEvent.setup();
    consultar.mockResolvedValue(pagina({ total: 45 }));
    render(<PaginaDeAuditoriaDeBackoffice />);
    await screen.findByRole("table");

    await user.selectOptions(screen.getByLabelText("Acción"), "PAN_REVELADO");
    await user.click(screen.getByRole("button", { name: "Filtrar" }));
    await user.selectOptions(screen.getByLabelText("Acción"), "DEPOSITO_SIMULADO");
    await user.click(screen.getByRole("button", { name: "Siguiente" }));

    expect(screen.getByText("Página 1 de 3")).toBeInTheDocument();
    expect(consultar).toHaveBeenLastCalledWith(
      expect.any(String),
      expect.objectContaining({ tipo: "PAN_REVELADO", pagina: 1 }),
    );
  });

  it("sin registros lo dice, y en la primera página «Anterior» está desactivado", async () => {
    consultar.mockResolvedValue(pagina({ eventos: [], total: 0 }));
    render(<PaginaDeAuditoriaDeBackoffice />);

    expect(await screen.findByText("No hay registros con esos filtros.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Anterior" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Siguiente" })).toBeDisabled();
  });
});
