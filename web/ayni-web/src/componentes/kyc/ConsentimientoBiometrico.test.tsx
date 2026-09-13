import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { ConsentimientoBiometrico } from "./ConsentimientoBiometrico";

describe("ConsentimientoBiometrico", () => {
  it("deshabilita el boton Continuar hasta que se aceptan ambos checks", () => {
    const onConsentimientoOtorgado = vi.fn();
    render(<ConsentimientoBiometrico onConsentimientoOtorgado={onConsentimientoOtorgado} onRechazado={vi.fn()} />);
    
    const botonContinuar = screen.getByRole("button", { name: "Continuar" });
    expect(botonContinuar).toBeDisabled();

    // Como el height es simulado por jsdom, leidoHastaElFinal puede ser true inicialmente
    const checkTerminos = screen.getByLabelText(/Términos y Condiciones/i);
    const checkPoliticas = screen.getByLabelText(/Política de Privacidad/i);

    fireEvent.click(checkTerminos);
    expect(botonContinuar).toBeDisabled();

    fireEvent.click(checkPoliticas);
    expect(botonContinuar).not.toBeDisabled();

    fireEvent.click(botonContinuar);
    expect(onConsentimientoOtorgado).toHaveBeenCalledOnce();
  });
});
