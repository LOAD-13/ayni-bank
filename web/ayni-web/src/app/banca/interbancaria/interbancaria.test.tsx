import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  beneficiariosFrecuentes,
  borrarBeneficiario,
  guardarBeneficiario,
  iniciarConfirmacion,
  transferirInterbancaria,
  validarCci,
  verificarConfirmacion,
} from "@/lib/api";

import PaginaInterbancaria from "./page";

// Estable entre renders, como el useCallback de la sesión real: si cambiara en cada render,
// la validación en vivo del CCI se repetiría sin fin.
const token = async () => "token-de-acceso";
vi.mock("@/componentes/banca/SesionDeBanca", () => ({
  useSesion: () => ({ token }),
}));

vi.mock("@/lib/api", async (original) => ({
  ...(await original<typeof import("@/lib/api")>()),
  beneficiariosFrecuentes: vi.fn(),
  borrarBeneficiario: vi.fn(),
  guardarBeneficiario: vi.fn(),
  iniciarConfirmacion: vi.fn(),
  transferirInterbancaria: vi.fn(),
  validarCci: vi.fn(),
  verificarConfirmacion: vi.fn(),
}));

const CCI = "00219300001234567886";

describe("Transferencia interbancaria (HU-11)", () => {
  beforeEach(() => {
    vi.mocked(beneficiariosFrecuentes).mockReset().mockResolvedValue([]);
    vi.mocked(validarCci)
      .mockReset()
      .mockImplementation(async (_t, cci) => cci === CCI);
    vi.mocked(iniciarConfirmacion).mockReset();
    vi.mocked(verificarConfirmacion).mockReset();
    vi.mocked(transferirInterbancaria).mockReset();
    vi.mocked(guardarBeneficiario).mockReset();
    vi.mocked(borrarBeneficiario).mockReset();
  });

  it("valida el CCI mientras se escribe y dice si su dígito de control no cuadra", async () => {
    const user = userEvent.setup();
    render(<PaginaInterbancaria />);
    const campo = screen.getByLabelText("CCI de destino");

    await user.type(campo, "0021930000123456788");
    expect(screen.getByText("Los 20 dígitos del CCI, con o sin guiones.")).toBeInTheDocument();
    expect(validarCci).not.toHaveBeenCalled();

    await user.type(campo, "7");
    expect(
      await screen.findAllByText("Este CCI no es válido: revisa los dos últimos dígitos."),
    ).not.toHaveLength(0);

    await user.clear(campo);
    await user.type(campo, CCI);
    expect(await screen.findByText("CCI válido.")).toBeInTheDocument();
  });

  it("no deja continuar con un CCI sin validar ni con un importe mal escrito", async () => {
    const user = userEvent.setup();
    render(<PaginaInterbancaria />);

    await user.type(screen.getByLabelText("Importe en soles"), "12,5,0");
    await user.click(screen.getByRole("button", { name: "Continuar" }));

    expect(
      screen.getByText("Revisa el CCI: son 20 dígitos y los dos últimos lo validan."),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Escribe un importe válido, con hasta dos decimales."),
    ).toBeInTheDocument();
    expect(iniciarConfirmacion).not.toHaveBeenCalled();
  });

  it("confirma con el segundo factor y envía solo el CCI, el importe y las cabeceras: nunca un usuario", async () => {
    const user = userEvent.setup();
    vi.mocked(iniciarConfirmacion).mockResolvedValue({
      confirmacionId: "c-1",
      metodo: "APP_AUTENTICADORA",
      expiraEn: "2026-10-28T15:05:00Z",
    });
    vi.mocked(verificarConfirmacion).mockResolvedValue({
      token: "token-de-confirmacion",
      expiraEn: "2026-10-28T15:05:00Z",
    });
    vi.mocked(transferirInterbancaria).mockResolvedValue({
      id: "a1b2c3d4-0000",
      estado: "COMPLETADA",
    });
    render(<PaginaInterbancaria />);

    await user.type(screen.getByLabelText("CCI de destino"), "002-193-000012345678-86");
    await screen.findByText("CCI válido.");
    await user.type(screen.getByLabelText("Importe en soles"), "120.5");
    await user.click(screen.getByRole("button", { name: "Continuar" }));
    expect(screen.getByText("002-193-000012345678-86")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Confirmar con mi segundo factor" }));

    expect(iniciarConfirmacion).toHaveBeenCalledWith("token-de-acceso", {
      destino: CCI,
      importe: expect.stringMatching(/^120\.5/),
      moneda: "PEN",
      claveIdempotencia: expect.any(String),
    });

    await user.type(await screen.findByRole("textbox", { name: /código/i }), "123456");
    await user.click(screen.getByRole("button", { name: /verificar|confirmar/i }));

    expect(
      await screen.findByRole("heading", { name: "Transferencia enviada" }),
    ).toBeInTheDocument();
    const llamada = vi.mocked(transferirInterbancaria).mock.calls[0];
    expect(llamada[0]).toBe("token-de-acceso");
    expect(llamada[1]).toBe(CCI);
    expect(llamada[5]).toBe("token-de-confirmacion");
    expect(JSON.stringify(llamada)).not.toContain("usuario");
  });

  it("si el otro banco aún no confirma, lo dice y promete avisar por correo", async () => {
    const user = userEvent.setup();
    vi.mocked(iniciarConfirmacion).mockResolvedValue({
      confirmacionId: "c-1",
      metodo: "APP_AUTENTICADORA",
      expiraEn: "2026-10-28T15:05:00Z",
    });
    vi.mocked(verificarConfirmacion).mockResolvedValue({
      token: "t",
      expiraEn: "2026-10-28T15:05:00Z",
    });
    vi.mocked(transferirInterbancaria).mockResolvedValue({
      id: "e5f6a7b8-0000",
      estado: "PENDIENTE",
    });
    render(<PaginaInterbancaria />);

    await user.type(screen.getByLabelText("CCI de destino"), CCI);
    await screen.findByText("CCI válido.");
    await user.type(screen.getByLabelText("Importe en soles"), "80");
    await user.click(screen.getByRole("button", { name: "Continuar" }));
    await user.click(screen.getByRole("button", { name: "Confirmar con mi segundo factor" }));
    await user.type(await screen.findByRole("textbox", { name: /código/i }), "123456");
    await user.click(screen.getByRole("button", { name: /verificar|confirmar/i }));

    expect(
      await screen.findByRole("heading", { name: "Transferencia en camino" }),
    ).toBeInTheDocument();
    expect(screen.getByText(/Te avisaremos por correo/)).toBeInTheDocument();
  });

  it("usa, guarda y quita beneficiarios frecuentes", async () => {
    const user = userEvent.setup();
    vi.mocked(beneficiariosFrecuentes).mockResolvedValue([{ id: "b-1", alias: "Mamá", cci: CCI }]);
    vi.mocked(guardarBeneficiario).mockResolvedValue({ id: "b-1", alias: "Mamá BCP", cci: CCI });
    vi.mocked(borrarBeneficiario).mockResolvedValue(undefined);
    render(<PaginaInterbancaria />);

    await user.click(await screen.findByRole("button", { name: "Mamá · termina en 7886" }));
    expect(screen.getByLabelText("CCI de destino")).toHaveValue(CCI);
    await screen.findByText("CCI válido.");

    await user.type(screen.getByLabelText("Guardar como beneficiario (opcional)"), "Mamá BCP");
    await user.click(screen.getByRole("button", { name: "Guardar" }));
    expect(guardarBeneficiario).toHaveBeenCalledWith("token-de-acceso", "Mamá BCP", CCI);
    expect(
      await screen.findByText("Guardamos a Mamá BCP entre tus beneficiarios."),
    ).toBeInTheDocument();

    await user.click(
      screen.getByRole("button", { name: "Quitar a Mamá BCP de tus beneficiarios" }),
    );
    expect(borrarBeneficiario).toHaveBeenCalledWith("token-de-acceso", "b-1");
    expect(
      await screen.findByText("Quitamos a Mamá BCP de tus beneficiarios."),
    ).toBeInTheDocument();
  });
});
