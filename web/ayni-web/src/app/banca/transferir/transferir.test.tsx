import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ErrorDeApi, iniciarConfirmacion, transferir, verificarConfirmacion } from "@/lib/api";

import PaginaDeTransferencia from "./page";

vi.mock("@/componentes/banca/SesionDeBanca", () => ({
  useSesion: () => ({ token: async () => "token-de-acceso" }),
}));

vi.mock("@/lib/api", async (original) => ({
  ...(await original<typeof import("@/lib/api")>()),
  iniciarConfirmacion: vi.fn(),
  verificarConfirmacion: vi.fn(),
  transferir: vi.fn(),
}));

const iniciar = vi.mocked(iniciarConfirmacion);
const verificar = vi.mocked(verificarConfirmacion);
const enviar = vi.mocked(transferir);

const COMPROBANTE = {
  movimientoId: "m-1",
  tipo: "TRANSFERENCIA",
  importe: "150.00",
  moneda: "PEN",
  cuentaOrigen: "**********0001",
  cuentaDestino: "**********0031",
  concepto: "Cena",
  registradoEn: "2026-10-11T15:00:00Z",
  saldoDisponible: "850.00",
} as never;

async function llegarAlCodigo(
  metodo: "APP_AUTENTICADORA" | "CORREO_ELECTRONICO" = "APP_AUTENTICADORA",
) {
  const user = userEvent.setup();
  iniciar.mockResolvedValue({ confirmacionId: "c-1", metodo, expiraEn: "2026-10-11T15:05:00Z" });
  render(<PaginaDeTransferencia />);
  await user.type(screen.getByLabelText("Cuenta Ayni de destino"), "001-1100000-0-031");
  await user.type(screen.getByLabelText("Importe en soles"), "150");
  await user.type(screen.getByLabelText("Concepto (opcional)"), "Cena");
  await user.click(screen.getByRole("button", { name: "Continuar" }));
  await user.click(screen.getByRole("button", { name: "Confirmar con mi segundo factor" }));
  await screen.findByRole("heading", { name: "Confirma que eres tú" });
  return user;
}

describe("Transferencia con segundo factor (HU-07)", () => {
  beforeEach(() => {
    iniciar.mockReset();
    verificar.mockReset();
    enviar.mockReset();
  });

  it("pide la confirmación con la operación revisada y transfiere con el token de identity", async () => {
    const user = await llegarAlCodigo();
    verificar.mockResolvedValue({
      token: "token-de-confirmacion",
      expiraEn: "2026-10-11T15:05:00Z",
    });
    enviar.mockResolvedValue(COMPROBANTE);

    expect(iniciar).toHaveBeenCalledWith("token-de-acceso", {
      destino: "00111000000031",
      importe: expect.stringMatching(/^150/),
      moneda: "PEN",
      claveIdempotencia: expect.any(String),
    });
    const clave = iniciar.mock.calls[0][1].claveIdempotencia;

    await user.type(screen.getByLabelText("Código de verificación"), "123456");
    await user.click(screen.getByRole("button", { name: "Confirmar y transferir" }));

    expect(verificar).toHaveBeenCalledWith("token-de-acceso", "c-1", "123456");
    expect(enviar).toHaveBeenCalledWith(
      "token-de-acceso",
      "00111000000031",
      expect.stringMatching(/^150/),
      "Cena",
      clave,
      "token-de-confirmacion",
    );
  });

  it("con el correo como método indica que el código llegó al correo", async () => {
    await llegarAlCodigo("CORREO_ELECTRONICO");
    expect(screen.getByText(/Te enviamos un código de 6 dígitos a tu correo/)).toBeInTheDocument();
  });

  it("no envía hasta tener 6 dígitos y solo acepta números", async () => {
    const user = await llegarAlCodigo();
    const campo = screen.getByLabelText("Código de verificación");
    await user.type(campo, "12a4");
    expect(campo).toHaveValue("124");
    expect(screen.getByRole("button", { name: "Confirmar y transferir" })).toBeDisabled();
  });

  it("un código incorrecto dice cuántos intentos quedan y no transfiere", async () => {
    const user = await llegarAlCodigo();
    verificar.mockRejectedValueOnce(new ErrorDeApi({ title: "x", intentosRestantes: 2 }, 422));

    await user.type(screen.getByLabelText("Código de verificación"), "000000");
    await user.click(screen.getByRole("button", { name: "Confirmar y transferir" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Te quedan 2 intentos");
    expect(enviar).not.toHaveBeenCalled();
  });

  it("si la confirmación vence, vuelve a la revisión para pedir otra", async () => {
    const user = await llegarAlCodigo();
    verificar.mockRejectedValueOnce(new ErrorDeApi({ title: "x" }, 410));

    await user.type(screen.getByLabelText("Código de verificación"), "123456");
    await user.click(screen.getByRole("button", { name: "Confirmar y transferir" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("La confirmación venció");
    expect(
      screen.getByRole("button", { name: "Confirmar con mi segundo factor" }),
    ).toBeInTheDocument();
  });

  it("explica los demás rechazos: sin 2FA, pausa y fallo de red", async () => {
    const user = userEvent.setup();
    iniciar.mockRejectedValueOnce(new ErrorDeApi({ title: "x" }, 409));
    render(<PaginaDeTransferencia />);
    await user.type(screen.getByLabelText("Cuenta Ayni de destino"), "00111000000031");
    await user.type(screen.getByLabelText("Importe en soles"), "10");
    await user.click(screen.getByRole("button", { name: "Continuar" }));
    await user.click(screen.getByRole("button", { name: "Confirmar con mi segundo factor" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Activa tu segundo factor");

    iniciar.mockRejectedValueOnce(new ErrorDeApi({ title: "x" }, 423));
    await user.click(screen.getByRole("button", { name: "Confirmar con mi segundo factor" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Pausamos las operaciones");

    iniciar.mockRejectedValueOnce(new Error("red"));
    await user.click(screen.getByRole("button", { name: "Confirmar con mi segundo factor" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("No pudimos completar");
  });

  it("corregir vuelve al formulario con lo que la persona ya escribió", async () => {
    // Si al corregir el formulario aparece vacío, arreglar un dígito obliga a teclear de
    // nuevo la cuenta, el importe y el concepto: el error se corrige con más esfuerzo del
    // que costó cometerlo (ISO 25010, protección contra errores del usuario).
    const user = userEvent.setup();
    render(<PaginaDeTransferencia />);
    await user.type(screen.getByLabelText("Cuenta Ayni de destino"), "001-1100000-0-031");
    await user.type(screen.getByLabelText("Importe en soles"), "150,5");
    await user.type(screen.getByLabelText("Concepto (opcional)"), "Cena");
    await user.click(screen.getByRole("button", { name: "Continuar" }));

    await user.click(screen.getByRole("button", { name: "Corregir" }));

    expect(screen.getByLabelText("Cuenta Ayni de destino")).toHaveValue("001-1100000-0-031");
    expect(screen.getByLabelText("Importe en soles")).toHaveValue("150,5");
    expect(screen.getByLabelText("Concepto (opcional)")).toHaveValue("Cena");
    expect(iniciar).not.toHaveBeenCalled();
  });

  it("valida mientras se escribe, tras 400 ms, sin marcar los campos aún vacíos", async () => {
    const user = userEvent.setup();
    render(<PaginaDeTransferencia />);
    expect(screen.getByRole("group", { name: "Paso 1 de 4: Datos" })).toBeInTheDocument();

    // Pasar al importe antes de los 400 ms no puede dejar la cuenta sin revisar.
    const cuenta = screen.getByLabelText("Cuenta Ayni de destino");
    await user.type(cuenta, "001-11");
    await user.type(screen.getByLabelText("Importe en soles"), "15,555");
    expect(
      await screen.findByText("El número de cuenta Ayni tiene 14 dígitos."),
    ).toBeInTheDocument();
    expect(screen.getByText("Escribe un importe válido, con hasta dos decimales.")).toBeVisible();
    expect(screen.getByLabelText("Concepto (opcional)")).not.toHaveAttribute("aria-invalid");

    await user.type(cuenta, "00000-0-031");
    await waitFor(() =>
      expect(
        screen.queryByText("El número de cuenta Ayni tiene 14 dígitos."),
      ).not.toBeInTheDocument(),
    );
  });

  it("muestra en qué paso va la transferencia", async () => {
    await llegarAlCodigo();
    expect(screen.getByRole("group", { name: "Paso 3 de 4: Código" })).toBeInTheDocument();
  });

  it("cancelar el código vuelve a la revisión sin transferir", async () => {
    const user = await llegarAlCodigo();
    await user.click(screen.getByRole("button", { name: "Cancelar" }));
    expect(screen.getByRole("heading", { name: "Confirma la transferencia" })).toBeInTheDocument();
    expect(enviar).not.toHaveBeenCalled();
  });
});
