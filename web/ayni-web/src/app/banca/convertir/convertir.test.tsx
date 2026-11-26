import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  abrirCuentaEnDolares,
  consultarMiCuenta,
  convertirMoneda,
  cotizarConversion,
  ErrorDeApi,
  type ConversionRealizada,
  type CotizacionDeConversion,
  type CuentaAbierta,
} from "@/lib/api";

import PaginaDeConversion from "./page";

vi.mock("@/componentes/banca/SesionDeBanca", () => ({
  useSesion: () => ({ token: async () => "token-de-acceso" }),
}));

vi.mock("@/lib/api", async (original) => ({
  ...(await original<typeof import("@/lib/api")>()),
  abrirCuentaEnDolares: vi.fn(),
  consultarMiCuenta: vi.fn(),
  convertirMoneda: vi.fn(),
  cotizarConversion: vi.fn(),
}));

const cuenta = vi.mocked(consultarMiCuenta);
const abrir = vi.mocked(abrirCuentaEnDolares);
const cotizar = vi.mocked(cotizarConversion);
const convertir = vi.mocked(convertirMoneda);

const SOLES = { moneda: "PEN", saldo: "1000.00" } as CuentaAbierta;
const DOLARES = { moneda: "USD", saldo: "0.00" } as CuentaAbierta;

const COTIZACION: CotizacionDeConversion = {
  origen: "PEN",
  destino: "USD",
  tipoAplicado: "3.761380",
  tipoReferencia: "3.752000",
  margen: "0.50",
  segmento: "ESTANDAR",
  fuente: "BCRP",
  obtenidoEn: "2026-11-23T19:00:00Z",
  enCache: false,
  importeOrigen: "376.14",
  importeDestino: "100.00",
};

const HECHA = {
  conversionId: "c-1",
  movimientoId: "m-1",
  importeOrigen: "376.14",
  monedaOrigen: "PEN",
  importeDestino: "100.00",
  monedaDestino: "USD",
  tipoAplicado: "3.761380",
  tipoReferencia: "3.752000",
  margen: "0.50",
  segmento: "ESTANDAR",
  fuente: "BCRP",
  tipoObtenidoEn: "2026-11-23T19:00:00Z",
  enCache: false,
  registradaEn: "2026-11-23T19:00:05Z",
} as ConversionRealizada;

function conAmbasCuentas() {
  cuenta.mockImplementation(async (_t, moneda) => (moneda === "USD" ? DOLARES : SOLES));
}

async function simular(user: ReturnType<typeof userEvent.setup>, importe = "376.14") {
  await user.type(await screen.findByLabelText("Importe en soles"), importe);
  await user.click(screen.getByRole("button", { name: "Simular conversión" }));
}

describe("Conversión de moneda", () => {
  beforeEach(() => {
    cuenta.mockReset();
    abrir.mockReset();
    cotizar.mockReset();
    convertir.mockReset();
  });

  it("sin cuenta en dólares ofrece abrirla, y al abrirla aparece el formulario", async () => {
    const user = userEvent.setup();
    cuenta.mockImplementation(async (_t, moneda) => {
      if (moneda === "USD") throw new ErrorDeApi({ codigo: "SIN_CUENTA", status: 404 }, 404);
      return SOLES;
    });
    abrir.mockResolvedValue(DOLARES);
    render(<PaginaDeConversion />);

    await user.click(await screen.findByRole("button", { name: "Abrir cuenta en dólares" }));

    expect(abrir).toHaveBeenCalledOnce();
    expect(await screen.findByLabelText("Importe en soles")).toBeInTheDocument();
  });

  it("simular enseña lo que se recibe, el tipo aplicado, la referencia y el margen", async () => {
    const user = userEvent.setup();
    conAmbasCuentas();
    cotizar.mockResolvedValue(COTIZACION);
    render(<PaginaDeConversion />);

    await simular(user);

    expect(cotizar).toHaveBeenCalledWith("token-de-acceso", "PEN", "376.14");
    expect(await screen.findByText("US$ 100.00")).toBeInTheDocument();
    expect(screen.getByText("S/ 3.761380 por US$ 1")).toBeInTheDocument();
    expect(screen.getByText("S/ 3.752000 por US$ 1")).toBeInTheDocument();
    expect(screen.getByText(/0\.50 % · tarifa estándar/)).toBeInTheDocument();
    // Con el tipo del día no hay aviso: avisar siempre enseñaría a no leerlo.
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("con el último tipo conocido avisa, y el aviso queda enlazado al botón de confirmar", async () => {
    const user = userEvent.setup();
    conAmbasCuentas();
    cotizar.mockResolvedValue({ ...COTIZACION, enCache: true, obtenidoEn: "2026-11-20T18:00:00Z" });
    render(<PaginaDeConversion />);

    await simular(user);

    const aviso = await screen.findByRole("status");
    expect(aviso).toHaveTextContent(/último tipo de cambio conocido/i);
    expect(screen.getByRole("button", { name: "Confirmar conversión" })).toHaveAttribute(
      "aria-describedby",
      aviso.id,
    );
  });

  it("confirmar envía el tipo que se vio y muestra lo recibido", async () => {
    const user = userEvent.setup();
    conAmbasCuentas();
    cotizar.mockResolvedValue(COTIZACION);
    convertir.mockResolvedValue(HECHA);
    render(<PaginaDeConversion />);

    await simular(user);
    await user.click(await screen.findByRole("button", { name: "Confirmar conversión" }));

    expect(convertir).toHaveBeenCalledWith(
      "token-de-acceso",
      { origen: "PEN", importe: "376.14", tipoMostrado: "3.761380", enCacheMostrado: false },
      expect.any(String),
    );
    expect(await screen.findByText("Conversión realizada")).toBeInTheDocument();
    expect(screen.getByText("US$ 100.00")).toBeInTheDocument();
  });

  it("si el tipo cambió no se convierte: se enseña el tipo nuevo para decidir con él", async () => {
    const user = userEvent.setup();
    conAmbasCuentas();
    cotizar.mockResolvedValueOnce(COTIZACION);
    cotizar.mockResolvedValueOnce({
      ...COTIZACION,
      tipoAplicado: "3.770000",
      importeDestino: "99.77",
    });
    convertir.mockRejectedValue(
      new ErrorDeApi({ codigo: "TIPO_DE_CAMBIO_CAMBIO", status: 409 }, 409),
    );
    render(<PaginaDeConversion />);

    await simular(user);
    await user.click(await screen.findByRole("button", { name: "Confirmar conversión" }));

    expect(await screen.findByText(/cambió mientras confirmabas/i)).toBeInTheDocument();
    expect(screen.getByText("S/ 3.770000 por US$ 1")).toBeInTheDocument();
    expect(screen.queryByText("Conversión realizada")).not.toBeInTheDocument();
  });

  it("si la red falla y se confirma lo mismo, viaja la misma clave", async () => {
    const user = userEvent.setup();
    conAmbasCuentas();
    cotizar.mockResolvedValue(COTIZACION);
    convertir.mockRejectedValueOnce(new ErrorDeApi({ title: "No pudimos conectar" }, 0));
    convertir.mockResolvedValueOnce(HECHA);
    render(<PaginaDeConversion />);

    await simular(user);
    await user.click(await screen.findByRole("button", { name: "Confirmar conversión" }));
    await screen.findByRole("alert");
    await user.click(screen.getByRole("button", { name: "Confirmar conversión" }));

    expect(convertir).toHaveBeenCalledTimes(2);
    expect(convertir.mock.calls[1][2]).toBe(convertir.mock.calls[0][2]);
  });

  it("un importe no válido no llega al servidor y dice cómo arreglarlo", async () => {
    const user = userEvent.setup();
    conAmbasCuentas();
    render(<PaginaDeConversion />);

    await simular(user, "12.345");

    expect(cotizar).not.toHaveBeenCalled();
    expect(screen.getByLabelText("Importe en soles")).toHaveAttribute("aria-invalid", "true");
  });

  it("al cambiar el sentido se pide el importe en dólares y se descarta lo simulado", async () => {
    const user = userEvent.setup();
    conAmbasCuentas();
    cotizar.mockResolvedValue(COTIZACION);
    render(<PaginaDeConversion />);

    await simular(user);
    await screen.findByRole("button", { name: "Confirmar conversión" });
    await user.click(screen.getByLabelText("Dólares a soles"));

    expect(screen.getByLabelText("Importe en dólares")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Confirmar conversión" })).not.toBeInTheDocument();
  });
});
