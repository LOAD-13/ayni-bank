import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";

import { confirmarDatosDelDni, ErrorDeApi, extraerDatosDelDni } from "@/lib/api";

import { ConfirmacionDeDatosDelDni } from "./ConfirmacionDeDatosDelDni";

vi.mock("@/lib/api", async (importOriginal) => {
  const real = await importOriginal<typeof import("@/lib/api")>();
  return { ...real, extraerDatosDelDni: vi.fn(), confirmarDatosDelDni: vi.fn() };
});

const SOLICITUD = "11111111-1111-1111-1111-111111111111";

const LEIDOS = {
  numeroEnmascarado: "****6677",
  nombres: "ANA LUCIA",
  apellidos: "QUISPE MAMANI",
  fechaNacimiento: "1990-05-15",
  sexo: "F" as const,
  fechaEmision: "2021-08-20",
  confiable: true,
};

/** HU-02 · el titular revisa, corrige y confirma lo que leyó el OCR. */
describe("ConfirmacionDeDatosDelDni", () => {
  const onConfirmado = vi.fn();
  const onDerivada = vi.fn();

  afterEach(() => {
    vi.clearAllMocks();
  });

  function renderizar() {
    render(
      <ConfirmacionDeDatosDelDni
        solicitudId={SOLICITUD}
        onConfirmado={onConfirmado}
        onDerivada={onDerivada}
      />,
    );
  }

  it("muestra lo leído con el número enmascarado, listo para confirmar", async () => {
    vi.mocked(extraerDatosDelDni).mockResolvedValue({ estado: "ACEPTADO", datos: LEIDOS });

    renderizar();

    expect(await screen.findByText("****6677")).toBeInTheDocument();
    expect(screen.getByLabelText("Nombres")).toHaveValue("ANA LUCIA");
    expect(screen.getByLabelText("Fecha de emisión")).toHaveValue("2021-08-20");
    expect(screen.getByText(/dígitos de control/i)).toBeInTheDocument();
    // Una sola lectura: cada una lanza el OCR y, si falla, gasta un intento.
    expect(extraerDatosDelDni).toHaveBeenCalledTimes(1);
  });

  it("confirma lo corregido sin enviar el número si no se tocó", async () => {
    const usuario = userEvent.setup();
    vi.mocked(extraerDatosDelDni).mockResolvedValue({ estado: "ACEPTADO", datos: LEIDOS });
    vi.mocked(confirmarDatosDelDni).mockResolvedValue({ estado: "ACEPTADO" });
    renderizar();

    const nombres = await screen.findByLabelText("Nombres");
    await usuario.clear(nombres);
    await usuario.type(nombres, "Ana Lucía");
    await usuario.click(screen.getByRole("button", { name: /confirmar y continuar/i }));

    await waitFor(() => expect(onConfirmado).toHaveBeenCalledTimes(1));
    expect(confirmarDatosDelDni).toHaveBeenCalledWith(SOLICITUD, {
      numero: undefined,
      nombres: "Ana Lucía",
      apellidos: "QUISPE MAMANI",
      fechaNacimiento: "1990-05-15",
      sexo: "F",
      fechaEmision: "2021-08-20",
    });
  });

  it("permite reescribir el número entero si el leído está mal", async () => {
    const usuario = userEvent.setup();
    vi.mocked(extraerDatosDelDni).mockResolvedValue({ estado: "ACEPTADO", datos: LEIDOS });
    vi.mocked(confirmarDatosDelDni).mockResolvedValue({ estado: "EN_REVISION_MANUAL" });
    renderizar();

    await usuario.click(await screen.findByRole("button", { name: /el número no es correcto/i }));
    await usuario.type(screen.getByLabelText("Número de DNI"), "44556678");
    await usuario.click(screen.getByRole("button", { name: /confirmar y continuar/i }));

    await waitFor(() => expect(onDerivada).toHaveBeenCalledWith("EN_REVISION_MANUAL"));
    expect(vi.mocked(confirmarDatosDelDni).mock.calls[0][1].numero).toBe("44556678");
  });

  it("pide la fecha de emisión si el OCR no la encontró", async () => {
    const usuario = userEvent.setup();
    vi.mocked(extraerDatosDelDni).mockResolvedValue({
      estado: "ACEPTADO",
      datos: { ...LEIDOS, fechaEmision: undefined },
    });
    renderizar();

    await usuario.click(await screen.findByRole("button", { name: /confirmar y continuar/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/fecha de emisión/i);
    expect(confirmarDatosDelDni).not.toHaveBeenCalled();
  });

  it("si no se pudo leer, pide volver a fotografiar el reverso con los intentos que quedan", async () => {
    vi.mocked(extraerDatosDelDni).mockResolvedValue({ estado: "RECHAZADO", intentosRestantes: 1 });

    renderizar();

    expect(await screen.findByRole("alert")).toHaveTextContent(/te queda 1 intento/i);
    expect(screen.getByRole("link", { name: /volver a fotografiar el reverso/i })).toHaveAttribute(
      "href",
      `/registro/dni-reverso?solicitudId=${SOLICITUD}`,
    );
  });

  it("si la solicitud se deriva al leer, lo avisa hacia arriba", async () => {
    vi.mocked(extraerDatosDelDni).mockResolvedValue({ estado: "EN_REVISION_MANUAL" });

    renderizar();

    await waitFor(() => expect(onDerivada).toHaveBeenCalledWith("EN_REVISION_MANUAL"));
  });

  it("muestra el error del servidor si la lectura falla", async () => {
    vi.mocked(extraerDatosDelDni).mockRejectedValue(
      new ErrorDeApi(
        { title: "Falta un paso anterior", detail: "Falta la foto del reverso del DNI." },
        409,
      ),
    );

    renderizar();

    expect(await screen.findByRole("alert")).toHaveTextContent(/falta la foto del reverso/i);
  });
});
