import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { SelectorMetodoSegundoFactor } from "./SelectorMetodoSegundoFactor";

describe("SelectorMetodoSegundoFactor", () => {
  it("muestra las 3 opciones de segundo factor", () => {
    render(
      <SelectorMetodoSegundoFactor
        metodoSeleccionado="APP_AUTENTICADORA"
        onSeleccionarMetodo={vi.fn()}
      />,
    );

    expect(screen.getByText("App Autenticadora")).toBeInTheDocument();
    expect(screen.getByText("Correo Electrónico")).toBeInTheDocument();
    expect(screen.getByText("Mensaje SMS")).toBeInTheDocument();
  });

  it("llama a onSeleccionarMetodo al hacer clic en una opción", async () => {
    const user = userEvent.setup();
    const onSeleccionarMetodo = vi.fn();

    render(
      <SelectorMetodoSegundoFactor
        metodoSeleccionado="APP_AUTENTICADORA"
        onSeleccionarMetodo={onSeleccionarMetodo}
      />,
    );

    await user.click(screen.getByText("Correo Electrónico"));
    expect(onSeleccionarMetodo).toHaveBeenCalledWith("CORREO_ELECTRONICO");

    await user.click(screen.getByText("Mensaje SMS"));
    expect(onSeleccionarMetodo).toHaveBeenCalledWith("SMS");
  });

  it("deshabilita los botones cuando deshabilitado es true", () => {
    render(
      <SelectorMetodoSegundoFactor
        metodoSeleccionado="APP_AUTENTICADORA"
        onSeleccionarMetodo={vi.fn()}
        deshabilitado={true}
      />,
    );

    const botones = screen.getAllByRole("button");
    expect(botones).toHaveLength(3);
    botones.forEach((boton) => {
      expect(boton).toBeDisabled();
    });
  });
});
