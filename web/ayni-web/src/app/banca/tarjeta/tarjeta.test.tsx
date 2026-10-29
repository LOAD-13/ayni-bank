import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  configurarTarjeta,
  congelarTarjeta,
  consultarControlesDeTarjeta,
  descongelarTarjeta,
  ErrorDeApi,
  type ControlesDeTarjeta,
} from "@/lib/api";

import PaginaDeLaTarjeta from "./page";

// Siempre la misma función, como el `useCallback` de SesionDeBanca: si cambiara en cada
// render, la carga inicial se repetiría y pisaría lo que devolvió cada cambio.
const token = async () => "token-de-acceso";
vi.mock("@/componentes/banca/SesionDeBanca", () => ({
  useSesion: () => ({ token }),
}));

vi.mock("@/lib/api", async (original) => ({
  ...(await original<typeof import("@/lib/api")>()),
  consultarControlesDeTarjeta: vi.fn(),
  congelarTarjeta: vi.fn(),
  descongelarTarjeta: vi.fn(),
  configurarTarjeta: vi.fn(),
}));

const consultar = vi.mocked(consultarControlesDeTarjeta);
const congelar = vi.mocked(congelarTarjeta);
const descongelar = vi.mocked(descongelarTarjeta);
const configurar = vi.mocked(configurarTarjeta);

const ACTIVA: ControlesDeTarjeta = {
  estado: "ACTIVA",
  ultimos4: "4821",
  moneda: "PEN",
  limiteDiario: "300.00",
  consumidoHoy: "250.00",
  comprasPorInternet: true,
};

async function abrir(controles: ControlesDeTarjeta = ACTIVA) {
  consultar.mockResolvedValue(controles);
  const user = userEvent.setup();
  render(<PaginaDeLaTarjeta />);
  await screen.findByRole("heading", { name: "Estado de la tarjeta" });
  return user;
}

describe("Controles de la tarjeta · HU-10 · AYNI-252", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("muestra el estado, los cuatro últimos dígitos y lo consumido frente al límite", async () => {
    await abrir();

    expect(screen.getByText("Activa")).toBeInTheDocument();
    expect(screen.getByText(/terminada en/)).toBeInTheDocument();
    expect(screen.getByText("4821", { exact: false })).toBeInTheDocument();
    expect(screen.getByText("S/ 250.00")).toBeInTheDocument();
    expect(screen.getByText("S/ 300.00")).toBeInTheDocument();
    expect(screen.getByRole("switch", { name: "Permitir compras por internet" })).toHaveAttribute(
      "aria-checked",
      "true",
    );
  });

  it("escenario 1: congelar la deja CONGELADA al momento y lo anuncia", async () => {
    const user = await abrir();
    congelar.mockResolvedValue({ ...ACTIVA, estado: "CONGELADA" });

    await user.click(screen.getByRole("button", { name: "Congelar tarjeta" }));

    expect(congelar).toHaveBeenCalledWith("token-de-acceso");
    expect(await screen.findByText("Congelada")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("Tu tarjeta quedó congelada");
    expect(screen.getByRole("button", { name: "Descongelar tarjeta" })).toBeInTheDocument();
  });

  it("una tarjeta congelada se puede descongelar", async () => {
    const user = await abrir({ ...ACTIVA, estado: "CONGELADA" });
    descongelar.mockResolvedValue(ACTIVA);

    await user.click(screen.getByRole("button", { name: "Descongelar tarjeta" }));

    expect(await screen.findByText("Activa")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("Tu tarjeta quedó activa de nuevo.");
  });

  it("el interruptor deshabilita las compras por internet sin tocar el límite", async () => {
    const user = await abrir();
    configurar.mockResolvedValue({ ...ACTIVA, comprasPorInternet: false });

    await user.click(screen.getByRole("switch", { name: "Permitir compras por internet" }));

    expect(configurar).toHaveBeenCalledWith("token-de-acceso", {
      limiteDiario: "300.00",
      comprasPorInternet: false,
    });
    expect(await screen.findByRole("status")).toHaveTextContent("quedaron deshabilitadas");
    expect(screen.getByRole("switch")).toHaveAttribute("aria-checked", "false");
  });

  it("guarda un límite válido, normalizado, sin tocar las compras por internet", async () => {
    const user = await abrir();
    configurar.mockResolvedValue({ ...ACTIVA, limiteDiario: "500.50" });

    await user.type(screen.getByLabelText("Nuevo límite diario en soles"), "500,5");
    await user.click(screen.getByRole("button", { name: "Guardar límite" }));

    expect(configurar).toHaveBeenCalledWith("token-de-acceso", {
      limiteDiario: "500.50",
      comprasPorInternet: true,
    });
    expect(await screen.findByRole("status")).toHaveTextContent(
      "Tu límite diario ahora es S/ 500.50.",
    );
  });

  it("un límite vacío, cero o mal escrito no se envía y se explica junto al campo", async () => {
    const user = await abrir();
    const campo = screen.getByLabelText("Nuevo límite diario en soles");

    for (const escrito of ["", "0", "abc"]) {
      await user.clear(campo);
      if (escrito) await user.type(campo, escrito);
      await user.click(screen.getByRole("button", { name: "Guardar límite" }));
      expect(await screen.findByText(/Escribe un límite mayor que cero/)).toBeInTheDocument();
    }
    expect(configurar).not.toHaveBeenCalled();
    expect(campo).toHaveAttribute("aria-invalid", "true");
  });

  it("si el servidor rechaza el cambio, lo dice y la tarjeta sigue como estaba", async () => {
    const user = await abrir();
    congelar.mockRejectedValue(
      new ErrorDeApi(
        { title: "No disponible", detail: "Inténtalo en unos segundos.", status: 503 },
        503,
      ),
    );

    await user.click(screen.getByRole("button", { name: "Congelar tarjeta" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Inténtalo en unos segundos.");
    expect(screen.getByText("Activa")).toBeInTheDocument();
  });

  it("sin tarjeta todavía (404) lo dice en lugar de mostrar controles que no controlan nada", async () => {
    consultar.mockRejectedValue(
      new ErrorDeApi({ title: "Todavía no tienes tarjeta", status: 404 }, 404),
    );
    render(<PaginaDeLaTarjeta />);

    expect(
      await screen.findByRole("heading", { name: "Tu tarjeta aún no está lista" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Congelar tarjeta" })).not.toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});
