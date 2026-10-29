import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { violacionesDeAccesibilidad } from "@/test/accesibilidad";

import { PasoDeSegundoFactorRegistro } from "./PasoDeSegundoFactorRegistro";

const pushMock = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: pushMock }),
}));

describe("PasoDeSegundoFactorRegistro · HU-22 (AYNI-124)", () => {
  beforeEach(() => {
    pushMock.mockReset();
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("muestra aviso cuando no hay solicitudId en la URL", () => {
    render(<PasoDeSegundoFactorRegistro />);

    expect(screen.getByText(/no sabemos de qué solicitud hablas/i)).toBeInTheDocument();
  });

  it("muestra el paso 2 de 6 con las tres opciones y SMS como Próximamente sin violaciones WCAG 2.1 AA", async () => {
    const { container } = render(
      <PasoDeSegundoFactorRegistro solicitudId="6b1f9c2a-0d3e-4a7b-8c11-5f2e9a4d7c31" />,
    );

    expect(screen.getByLabelText(/paso 2 de 6: segundo factor/i)).toBeInTheDocument();
    expect(screen.getByText("App Autenticadora")).toBeInTheDocument();
    expect(screen.getByText("Correo Electrónico")).toBeInTheDocument();
    expect(screen.getByText("Mensaje SMS")).toBeInTheDocument();
    expect(screen.getByText("Próximamente")).toBeInTheDocument();

    const violaciones = await violacionesDeAccesibilidad(container);
    expect(violaciones).toEqual([]);
  });

  it("inscribe App Autenticadora, muestra la clave manual y verifica el código de 6 dígitos hacia DNI anverso", async () => {
    const solicitudId = "6b1f9c2a-0d3e-4a7b-8c11-5f2e9a4d7c31";
    const fetchMock = vi.mocked(globalThis.fetch);
    fetchMock
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          id: "11111111-1111-1111-1111-111111111111",
          usuarioId: solicitudId,
          tipo: "APP_AUTENTICADORA",
          secreto: "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP",
          uriDeAprovisionamiento:
            "otpauth://totp/Ayni%20Bank?secret=JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP&issuer=Ayni%20Bank",
          confirmado: false,
        }),
      } as Response)
      .mockResolvedValueOnce({
        ok: true,
        status: 204,
        text: async () => "",
        json: async () => undefined,
      } as Response);

    const usuario = userEvent.setup();
    render(<PasoDeSegundoFactorRegistro solicitudId={solicitudId} />);

    await usuario.click(screen.getByRole("button", { name: /continuar con este método/i }));

    await waitFor(() => {
      expect(screen.getByText(/vincula tu app autenticadora/i)).toBeInTheDocument();
    });

    await usuario.click(screen.getByRole("button", { name: /no puedo escanearlo/i }));
    expect(screen.getByText("JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP")).toBeInTheDocument();

    for (let i = 1; i <= 6; i++) {
      await usuario.type(screen.getByLabelText(`Dígito ${i} de 6`), String(i));
    }

    await usuario.click(screen.getByRole("button", { name: /verificar y continuar con mi dni/i }));

    await waitFor(() => {
      expect(pushMock).toHaveBeenCalledWith(`/registro/dni-anverso?solicitudId=${solicitudId}`);
    });
  });

  it("permite elegir Correo Electrónico, reenviar código y verificar el contacto", async () => {
    const solicitudId = "6b1f9c2a-0d3e-4a7b-8c11-5f2e9a4d7c31";
    const fetchMock = vi.mocked(globalThis.fetch);
    fetchMock
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          id: "22222222-2222-2222-2222-222222222222",
          usuarioId: solicitudId,
          tipo: "CORREO_ELECTRONICO",
          secreto: null,
          uriDeAprovisionamiento: null,
          confirmado: false,
        }),
      } as Response)
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          id: "33333333-3333-3333-3333-333333333333",
          usuarioId: solicitudId,
          tipoFactor: "CORREO_ELECTRONICO",
          expiraEn: "2026-10-18T19:00:00Z",
          intentosRealizados: 0,
          verificado: false,
        }),
      } as Response)
      .mockResolvedValueOnce({
        ok: true,
        status: 204,
        text: async () => "",
        json: async () => undefined,
      } as Response);

    const usuario = userEvent.setup();
    render(<PasoDeSegundoFactorRegistro solicitudId={solicitudId} />);

    await usuario.click(screen.getByText("Correo Electrónico"));
    await usuario.click(screen.getByRole("button", { name: /continuar con este método/i }));

    await waitFor(() => {
      expect(screen.getByText(/verifica tu correo electrónico/i)).toBeInTheDocument();
    });

    await usuario.click(screen.getByRole("button", { name: /reenviar código/i }));
    await waitFor(() => {
      expect(screen.getByText(/enviamos un nuevo código de 6 dígitos/i)).toBeInTheDocument();
    });

    for (let i = 1; i <= 6; i++) {
      await usuario.type(screen.getByLabelText(`Dígito ${i} de 6`), String(i));
    }

    await usuario.click(screen.getByRole("button", { name: /verificar y continuar con mi dni/i }));

    await waitFor(() => {
      expect(pushMock).toHaveBeenCalledWith(`/registro/dni-anverso?solicitudId=${solicitudId}`);
    });
  });
});
