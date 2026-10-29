import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ErrorDeApi, solicitarRecuperacion } from "@/lib/api";

import { FormularioDeRecuperacion } from "./FormularioDeRecuperacion";

vi.mock("@/lib/api", async (original) => ({
  ...(await original<typeof import("@/lib/api")>()),
  solicitarRecuperacion: vi.fn(),
}));

const solicitar = vi.mocked(solicitarRecuperacion);

describe("FormularioDeRecuperacion", () => {
  beforeEach(() => solicitar.mockReset());

  it("pide el enlace y muestra la misma confirmación para cualquier correo", async () => {
    const user = userEvent.setup();
    solicitar.mockResolvedValue({ mensaje: "Si el correo corresponde…" });
    render(<FormularioDeRecuperacion />);

    await user.type(screen.getByLabelText("Correo electrónico"), "  ana.quispe@example.pe ");
    await user.click(screen.getByRole("button", { name: "Enviar enlace" }));

    expect(solicitar).toHaveBeenCalledWith("ana.quispe@example.pe");
    expect(await screen.findByRole("heading", { name: "Revisa tu correo" })).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      "Si el correo corresponde a una cuenta de Ayni",
    );
    expect(screen.getByRole("link", { name: "Volver a ingresar" })).toHaveAttribute(
      "href",
      "/ingresar",
    );
  });

  it("no llama al servidor con un correo mal escrito", async () => {
    const user = userEvent.setup();
    render(<FormularioDeRecuperacion />);

    await user.type(screen.getByLabelText("Correo electrónico"), "ana@");
    await user.click(screen.getByRole("button", { name: "Enviar enlace" }));

    expect(solicitar).not.toHaveBeenCalled();
    expect(screen.getByRole("alert")).toHaveTextContent("Escribe un correo válido");
  });

  it("distingue un fallo de conexión de un correo rechazado", async () => {
    const user = userEvent.setup();
    solicitar.mockRejectedValueOnce(new ErrorDeApi({ title: "x" }, 0));
    render(<FormularioDeRecuperacion />);

    await user.type(screen.getByLabelText("Correo electrónico"), "ana.quispe@example.pe");
    await user.click(screen.getByRole("button", { name: "Enviar enlace" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("No pudimos conectar");

    solicitar.mockRejectedValueOnce(new ErrorDeApi({ title: "x" }, 400));
    await user.click(screen.getByRole("button", { name: "Enviar enlace" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("No pudimos procesar el correo");
  });
});
