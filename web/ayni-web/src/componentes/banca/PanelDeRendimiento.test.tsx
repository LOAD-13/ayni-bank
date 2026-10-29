import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { CuentaAbierta, RendimientoDeCuenta } from "@/lib/api";

import { PanelDeRendimiento } from "./PanelDeRendimiento";

const cuenta = { saldo: "1000.00", trea: "4.50" } as CuentaAbierta;

const rendimiento: RendimientoDeCuenta = {
  tea: "4.00",
  trea: "4.00",
  treaIgualATea: true,
  tasaVigenteDesde: "2026-10-15",
  moneda: "PEN",
  devengadoDelMes: "0.24448978",
  devengadoDelMesRedondeado: "0.24",
  abonadoTotal: "4.20",
  devengos: [
    { fecha: "2026-10-15", saldoBase: "1000.00", tea: "4.00", importe: "0.10895236" },
    { fecha: "2026-10-14", saldoBase: "1000.00", tea: "5.00", importe: "0.13553742" },
  ],
};

describe("PanelDeRendimiento · HU-06", () => {
  it("muestra lo ganado de verdad, que TREA es igual a TEA y cada devengo con su tasa", () => {
    render(<PanelDeRendimiento cuenta={cuenta} rendimiento={rendimiento} oculto={false} />);

    expect(screen.getByText("Rendimiento del mes")).toBeInTheDocument();
    expect(screen.getByText("Este mes ya ganaste S/ 0.24")).toBeInTheDocument();
    expect(
      screen.getByText(/TREA 4.00 % = TEA 4.00 %. No cobramos comisión de mantenimiento/),
    ).toBeInTheDocument();
    expect(screen.getByText("S/ 4.20")).toBeInTheDocument();
    expect(screen.getByText("14/10/2026")).toBeInTheDocument();
    expect(screen.getByText("5.00 %")).toBeInTheDocument();
    expect(screen.getByText("4.00 %")).toBeInTheDocument();
    expect(screen.getByText("S/ 0.13553742")).toBeInTheDocument();
  });

  it("sin devengos todavía, muestra la estimación rotulada como tal", () => {
    render(
      <PanelDeRendimiento
        cuenta={cuenta}
        rendimiento={{ ...rendimiento, devengos: [] }}
        oculto={false}
      />,
    );

    expect(screen.getByText("Rendimiento estimado del mes")).toBeInTheDocument();
    expect(screen.getByText("Tu dinero gana unos S/ 3.33 al mes")).toBeInTheDocument();
    expect(screen.queryByText("Ver cómo se calcula cada día")).not.toBeInTheDocument();
  });

  it("si la consulta de rendimiento falló, usa la TREA de la cuenta", () => {
    render(<PanelDeRendimiento cuenta={cuenta} rendimiento={null} oculto={false} />);

    expect(screen.getByText(/TREA 4.50 % = TEA 4.50 %/)).toBeInTheDocument();
  });

  it("sin saldo ni tasa invita al primer depósito", () => {
    render(<PanelDeRendimiento cuenta={null} rendimiento={null} oculto={false} />);

    expect(
      screen.getByText("Tu dinero empieza a rendir con tu primer depósito"),
    ).toBeInTheDocument();
  });

  it("con los importes ocultos no se ve ninguna cifra", () => {
    const { container } = render(
      <PanelDeRendimiento cuenta={cuenta} rendimiento={rendimiento} oculto={true} />,
    );

    expect(screen.getByText("Este mes ya ganaste S/ ••••")).toBeInTheDocument();
    expect(container.textContent).not.toContain("0.13553742");
    expect(container.textContent).not.toContain("4.20");
  });

  it("si TREA y TEA difieren, las muestra por separado", () => {
    render(
      <PanelDeRendimiento
        cuenta={cuenta}
        rendimiento={{ ...rendimiento, trea: "4.20", treaIgualATea: false }}
        oculto={false}
      />,
    );

    expect(screen.getByText(/TREA 4.20 % · TEA 4.00 %/)).toBeInTheDocument();
  });
});
