import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { PasoDeSegundoFactor } from "./PasoDeSegundoFactor";

describe("PasoDeSegundoFactor", () => {
  it("muestra el formulario de 6 dígitos para el código 2FA", () => {
    render(
      <PasoDeSegundoFactor
        uriDeAprovisionamiento={null}
        onEnviar={vi.fn()}
        onVolver={vi.fn()}
        enviando={false}
        error={null}
      />,
    );

    expect(screen.getByRole("heading", { name: "Confirma que eres tú" })).toBeInTheDocument();
    expect(screen.getByLabelText("Dígito 1 de 6")).toBeInTheDocument();
    expect(screen.getByLabelText("Dígito 6 de 6")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Verificar y entrar" })).toBeDisabled();
  });

  it("permite escribir los 6 dígitos y enviar el código", async () => {
    const user = userEvent.setup();
    const onEnviar = vi.fn().mockResolvedValue(undefined);

    render(
      <PasoDeSegundoFactor
        uriDeAprovisionamiento={null}
        onEnviar={onEnviar}
        onVolver={vi.fn()}
        enviando={false}
        error={null}
      />,
    );

    for (let i = 1; i <= 6; i++) {
      await user.type(screen.getByLabelText(`Dígito ${i} de 6`), String(i));
    }

    const botonEnviar = screen.getByRole("button", { name: "Verificar y entrar" });
    expect(botonEnviar).not.toBeDisabled();
    await user.click(botonEnviar);

    expect(onEnviar).toHaveBeenCalledWith("123456");
  });

  it("muestra mensaje de error cuando error es enviado", () => {
    render(
      <PasoDeSegundoFactor
        uriDeAprovisionamiento={null}
        onEnviar={vi.fn()}
        onVolver={vi.fn()}
        enviando={false}
        error="El código ingresado es incorrecto."
      />,
    );

    expect(screen.getByRole("alert")).toHaveTextContent("El código ingresado es incorrecto.");
  });

  it("permite seleccionar otro método de verificación si onSeleccionarMetodo está definido", async () => {
    const user = userEvent.setup();
    const onSeleccionarMetodo = vi.fn().mockResolvedValue(undefined);

    render(
      <PasoDeSegundoFactor
        uriDeAprovisionamiento={null}
        onEnviar={vi.fn()}
        onVolver={vi.fn()}
        enviando={false}
        error={null}
        onSeleccionarMetodo={onSeleccionarMetodo}
      />,
    );

    const botonOtroMetodo = screen.getByRole("button", {
      name: "Usar otro método de verificación",
    });
    await user.click(botonOtroMetodo);

    expect(screen.getByText("Elige tu método de segundo factor (2FA):")).toBeInTheDocument();

    await user.click(screen.getByText("Correo Electrónico"));
    expect(onSeleccionarMetodo).toHaveBeenCalledWith("CORREO_ELECTRONICO");
  });

  it("permite reenviar el código para canales OTP (Correo/SMS)", async () => {
    const user = userEvent.setup();
    const onReenviarCodigo = vi.fn().mockResolvedValue(undefined);

    render(
      <PasoDeSegundoFactor
        uriDeAprovisionamiento={null}
        onEnviar={vi.fn()}
        onVolver={vi.fn()}
        enviando={false}
        error={null}
        tipoMetodo="CORREO_ELECTRONICO"
        onReenviarCodigo={onReenviarCodigo}
      />,
    );

    const botonReenviar = screen.getByRole("button", { name: "Reenviar código" });
    await user.click(botonReenviar);

    expect(onReenviarCodigo).toHaveBeenCalledTimes(1);
    expect(screen.getByText("Nuevo código enviado correctamente.")).toBeInTheDocument();
  });
});
