import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { depositarSimulado } from "@/lib/api";

import PaginaDeDeposito from "./page";

vi.mock("@/componentes/banca/SesionDeBanca", () => ({
  useSesion: () => ({ token: async () => "token-de-acceso" }),
}));

vi.mock("@/lib/api", async (original) => ({
  ...(await original<typeof import("@/lib/api")>()),
  depositarSimulado: vi.fn(),
}));

const depositar = vi.mocked(depositarSimulado);

const COMPROBANTE = {
  movimientoId: "m-1",
  tipo: "DEPOSITO_SIMULADO",
  importe: "100.00",
  moneda: "PEN",
  cuentaOrigen: "**********0000",
  cuentaDestino: "**********0031",
  concepto: "Depósito simulado",
  registradoEn: "2026-10-27T15:00:00Z",
  saldoDisponible: "950.00",
} as never;

async function intentar(user: ReturnType<typeof userEvent.setup>, importe: string) {
  const campo = screen.getByLabelText("Importe en soles");
  await user.clear(campo);
  await user.type(campo, importe);
  await user.click(screen.getByRole("button", { name: "Depositar" }));
}

describe("Depósito simulado", () => {
  beforeEach(() => {
    depositar.mockReset();
  });

  it("si la red falla y se reintenta el mismo depósito, viaja la misma clave", async () => {
    // La página promete «sin cobros dobles: si la conexión falla y vuelves a confirmar, la
    // operación no se repite». Solo es cierto si el reintento lleva la clave del primer
    // intento: si el servidor ya lo registró, devuelve ese comprobante en lugar de otro.
    const user = userEvent.setup();
    depositar.mockRejectedValueOnce(new TypeError("Failed to fetch"));
    depositar.mockResolvedValueOnce(COMPROBANTE);
    render(<PaginaDeDeposito />);

    await intentar(user, "100");
    expect(await screen.findByRole("alert")).toHaveTextContent(/inténtalo de nuevo/i);
    await intentar(user, "100");

    expect(depositar).toHaveBeenCalledTimes(2);
    expect(depositar.mock.calls[1][2]).toBe(depositar.mock.calls[0][2]);
  });

  it("un importe distinto es otra operación y lleva otra clave", async () => {
    const user = userEvent.setup();
    depositar.mockRejectedValueOnce(new TypeError("Failed to fetch"));
    depositar.mockResolvedValueOnce(COMPROBANTE);
    render(<PaginaDeDeposito />);

    await intentar(user, "100");
    await screen.findByRole("alert");
    await intentar(user, "150");

    expect(depositar.mock.calls[1][2]).not.toBe(depositar.mock.calls[0][2]);
  });

  it("un importe no válido no llega al servidor y dice cómo arreglarlo", async () => {
    const user = userEvent.setup();
    render(<PaginaDeDeposito />);

    await intentar(user, "12.345");

    expect(screen.getByLabelText("Importe en soles")).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByText(/hasta dos decimales/i)).toBeInTheDocument();
    expect(depositar).not.toHaveBeenCalled();
  });
});
