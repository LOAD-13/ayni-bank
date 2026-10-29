import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  ErrorDeApi,
  consultarTarjeta,
  cvvDeLaTarjeta,
  iniciarConfirmacion,
  revelarPan,
  verificarConfirmacion,
} from "@/lib/api";

import { TarjetaVirtual } from "./TarjetaVirtual";

// Estable entre renders, como el useCallback de la sesión real.
const token = async () => "token-de-acceso";
vi.mock("@/componentes/banca/SesionDeBanca", () => ({ useSesion: () => ({ token }) }));

vi.mock("@/lib/api", async (original) => ({
  ...(await original<typeof import("@/lib/api")>()),
  consultarTarjeta: vi.fn(),
  cvvDeLaTarjeta: vi.fn(),
  iniciarConfirmacion: vi.fn(),
  revelarPan: vi.fn(),
  verificarConfirmacion: vi.fn(),
}));

const TARJETA = {
  ultimos4: "0007",
  numeroEnmascarado: "•••• •••• •••• 0007",
  vencimiento: "10/30",
  estado: "ACTIVA" as const,
};

describe("Tarjeta de débito virtual (HU-09)", () => {
  beforeEach(() => {
    vi.mocked(consultarTarjeta).mockReset().mockResolvedValue(TARJETA);
    vi.mocked(cvvDeLaTarjeta).mockReset();
    vi.mocked(iniciarConfirmacion).mockReset();
    vi.mocked(verificarConfirmacion).mockReset();
    vi.mocked(revelarPan).mockReset();
  });

  it("muestra solo los últimos 4 dígitos y el vencimiento, y avisa de que la tarjeta existe", async () => {
    const alCargar = vi.fn();
    render(<TarjetaVirtual alCargar={alCargar} />);

    expect(await screen.findByRole("img", { name: /terminada en 0007/ })).toBeInTheDocument();
    expect(screen.getByText(/•••• •••• •••• 0007 · vence 10\/30 · Activa/)).toBeInTheDocument();
    expect(alCargar).toHaveBeenCalledWith(true);
  });

  it("sin tarjeta no muestra nada y avisa a la página", async () => {
    vi.mocked(consultarTarjeta).mockRejectedValue(
      new ErrorDeApi({ title: "Sin tarjeta", status: 404 }, 404),
    );
    const alCargar = vi.fn();
    const { container } = render(<TarjetaVirtual alCargar={alCargar} />);

    await vi.waitFor(() => expect(alCargar).toHaveBeenCalledWith(false));
    expect(container).toBeEmptyDOMElement();
  });

  it("muestra el CVV dinámico solo cuando se pide", async () => {
    const user = userEvent.setup();
    vi.mocked(cvvDeLaTarjeta).mockResolvedValue({
      cvv: "123",
      validoHasta: "2026-10-28T15:05:00Z",
    });
    render(<TarjetaVirtual />);

    await user.click(await screen.findByRole("button", { name: "Ver CVV" }));

    expect(await screen.findByText("123")).toBeInTheDocument();
    expect(screen.getByText(/cambia a las/)).toBeInTheDocument();
  });

  it("revela el número completo tras el segundo factor y lo oculta al vencer el plazo", async () => {
    const user = userEvent.setup();
    vi.mocked(iniciarConfirmacion).mockResolvedValue({
      confirmacionId: "c-1",
      metodo: "APP_AUTENTICADORA",
      expiraEn: "2026-10-28T15:05:00Z",
    });
    vi.mocked(verificarConfirmacion).mockResolvedValue({ token: "confirmada", expiraEn: "x" });
    // El plazo se calcula al revelar, como hace el servidor.
    vi.mocked(revelarPan).mockImplementation(async () => ({
      numero: "4999 9900 0000 0007",
      visibleHasta: new Date(Date.now() + 500).toISOString(),
    }));
    render(<TarjetaVirtual />);

    await user.click(await screen.findByRole("button", { name: "Ver número completo" }));
    expect(iniciarConfirmacion).toHaveBeenCalledWith("token-de-acceso", {
      destino: "REVELAR-PAN-0007",
      importe: "0.01",
      moneda: "PEN",
      claveIdempotencia: expect.any(String),
    });
    await user.type(await screen.findByLabelText("Código de verificación"), "123456");
    await user.click(screen.getByRole("button", { name: "Confirmar y ver el número" }));

    expect(await screen.findByText("4999 9900 0000 0007")).toBeInTheDocument();
    expect(revelarPan).toHaveBeenCalledWith("token-de-acceso", expect.any(String), "confirmada");
    await act(() => new Promise((fin) => setTimeout(fin, 700)));
    expect(screen.queryByText("4999 9900 0000 0007")).not.toBeInTheDocument();
  });

  it("si la confirmación ya no vale, lo dice y deja volver a pedirla", async () => {
    const user = userEvent.setup();
    vi.mocked(iniciarConfirmacion).mockResolvedValue({
      confirmacionId: "c-1",
      metodo: "APP_AUTENTICADORA",
      expiraEn: "2026-10-28T15:05:00Z",
    });
    vi.mocked(verificarConfirmacion).mockRejectedValue(
      new ErrorDeApi({ title: "Vencida", status: 410 }, 410),
    );
    render(<TarjetaVirtual />);

    await user.click(await screen.findByRole("button", { name: "Ver número completo" }));
    await user.type(await screen.findByLabelText("Código de verificación"), "123456");
    await user.click(screen.getByRole("button", { name: "Confirmar y ver el número" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Tu confirmación ya no es válida");
  });
});
