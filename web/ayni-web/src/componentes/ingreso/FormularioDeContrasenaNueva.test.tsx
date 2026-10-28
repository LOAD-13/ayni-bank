import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ErrorDeApi, restablecerContrasena, validarEnlaceDeRecuperacion } from "@/lib/api";

import { FormularioDeContrasenaNueva } from "./FormularioDeContrasenaNueva";

vi.mock("@/lib/api", async (original) => ({
  ...(await original<typeof import("@/lib/api")>()),
  validarEnlaceDeRecuperacion: vi.fn(),
  restablecerContrasena: vi.fn(),
}));

const validar = vi.mocked(validarEnlaceDeRecuperacion);
const restablecer = vi.mocked(restablecerContrasena);

const TOKEN = "token-de-prueba-de-recuperacion-0000";
const BUENA = "Nueva!Clave2026#";

function abrirEnlace(fragmento: string) {
  window.history.replaceState(null, "", `/recuperar/nueva${fragmento}`);
}

async function escribir(nueva: string, repetida: string) {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText("Contraseña nueva"), nueva);
  await user.type(screen.getByLabelText("Repite la contraseña nueva"), repetida);
  await user.click(screen.getByRole("button", { name: "Guardar contraseña" }));
}

describe("FormularioDeContrasenaNueva", () => {
  beforeEach(() => {
    validar.mockReset();
    restablecer.mockReset();
  });
  afterEach(() => window.history.replaceState(null, "", "/"));

  it("lee el token del fragmento, lo borra de la barra y valida el enlace", async () => {
    validar.mockResolvedValue(undefined);
    abrirEnlace(`#t=${TOKEN}`);
    render(<FormularioDeContrasenaNueva />);

    expect(
      await screen.findByRole("heading", { name: "Crea tu contraseña nueva" }),
    ).toBeInTheDocument();
    expect(validar).toHaveBeenCalledWith(TOKEN);
    expect(window.location.hash).toBe("");
    expect(window.location.href).not.toContain(TOKEN);
  });

  it("sin token en el enlace muestra que no es válido sin llamar al servidor", async () => {
    abrirEnlace("");
    render(<FormularioDeContrasenaNueva />);

    expect(
      await screen.findByRole("heading", { name: "El enlace ya no es válido" }),
    ).toBeInTheDocument();
    expect(validar).not.toHaveBeenCalled();
    expect(screen.getByRole("link", { name: "Pedir un enlace nuevo" })).toHaveAttribute(
      "href",
      "/recuperar",
    );
  });

  it("un enlace caducado o usado (410) muestra que no es válido", async () => {
    validar.mockRejectedValue(new ErrorDeApi({ title: "x" }, 410));
    abrirEnlace(`#t=${TOKEN}`);
    render(<FormularioDeContrasenaNueva />);

    expect(
      await screen.findByRole("heading", { name: "El enlace ya no es válido" }),
    ).toBeInTheDocument();
  });

  it("sin conexión al validar deja intentar el cambio", async () => {
    validar.mockRejectedValue(new ErrorDeApi({ title: "x" }, 0));
    abrirEnlace(`#t=${TOKEN}`);
    render(<FormularioDeContrasenaNueva />);

    expect(
      await screen.findByRole("heading", { name: "Crea tu contraseña nueva" }),
    ).toBeInTheDocument();
  });

  it("no envía una contraseña que incumple la política ni dos que no coinciden", async () => {
    validar.mockResolvedValue(undefined);
    abrirEnlace(`#t=${TOKEN}`);
    render(<FormularioDeContrasenaNueva />);
    await screen.findByRole("heading", { name: "Crea tu contraseña nueva" });

    await escribir("corta", "corta");
    expect(screen.getByRole("alert")).toHaveTextContent("aún no cumple todos los requisitos");

    const user = userEvent.setup();
    await user.clear(screen.getByLabelText("Contraseña nueva"));
    await user.clear(screen.getByLabelText("Repite la contraseña nueva"));
    await escribir(BUENA, `${BUENA}x`);
    expect(screen.getByRole("alert")).toHaveTextContent("no coinciden");
    expect(restablecer).not.toHaveBeenCalled();
  });

  it("guarda la contraseña y avisa que el segundo factor sigue siendo necesario", async () => {
    validar.mockResolvedValue(undefined);
    restablecer.mockResolvedValue(undefined);
    abrirEnlace(`#t=${TOKEN}`);
    render(<FormularioDeContrasenaNueva />);
    await screen.findByRole("heading", { name: "Crea tu contraseña nueva" });

    await escribir(BUENA, BUENA);

    expect(restablecer).toHaveBeenCalledWith(TOKEN, BUENA);
    expect(
      await screen.findByRole("heading", { name: "Listo, tu contraseña cambió" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("segundo factor");
    expect(screen.getByRole("link", { name: "Ingresar" })).toHaveAttribute("href", "/ingresar");
  });

  it("si el enlace caduca mientras escribe, muestra que ya no es válido", async () => {
    validar.mockResolvedValue(undefined);
    restablecer.mockRejectedValue(new ErrorDeApi({ title: "x" }, 410));
    abrirEnlace(`#t=${TOKEN}`);
    render(<FormularioDeContrasenaNueva />);
    await screen.findByRole("heading", { name: "Crea tu contraseña nueva" });

    await escribir(BUENA, BUENA);

    expect(
      await screen.findByRole("heading", { name: "El enlace ya no es válido" }),
    ).toBeInTheDocument();
  });

  it("traduce el rechazo de la política y el fallo de conexión al guardar", async () => {
    validar.mockResolvedValue(undefined);
    restablecer.mockRejectedValueOnce(new ErrorDeApi({ title: "x" }, 400));
    abrirEnlace(`#t=${TOKEN}`);
    render(<FormularioDeContrasenaNueva />);
    await screen.findByRole("heading", { name: "Crea tu contraseña nueva" });

    await escribir(BUENA, BUENA);
    expect(await screen.findByRole("alert")).toHaveTextContent("no cumple la política");

    restablecer.mockRejectedValueOnce(new ErrorDeApi({ title: "x" }, 0));
    await userEvent.setup().click(screen.getByRole("button", { name: "Guardar contraseña" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("No pudimos conectar");
  });

  it("permite ver la contraseña que escribe", async () => {
    validar.mockResolvedValue(undefined);
    abrirEnlace(`#t=${TOKEN}`);
    render(<FormularioDeContrasenaNueva />);
    await screen.findByRole("heading", { name: "Crea tu contraseña nueva" });

    await userEvent.setup().click(screen.getByRole("button", { name: "Mostrar la contraseña" }));

    expect(screen.getByLabelText("Contraseña nueva")).toHaveAttribute("type", "text");
    expect(screen.getByRole("button", { name: "Ocultar la contraseña" })).toBeInTheDocument();
  });
});
