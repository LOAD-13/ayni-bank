import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { CapturaDeSelfie } from "./CapturaDeSelfie";

// Mock de la librería face-api
vi.mock("@vladmandic/face-api", () => {
  return {
    nets: {
      tinyFaceDetector: {
        loadFromUri: vi.fn().mockResolvedValue(undefined),
      },
      faceExpressionNet: {
        loadFromUri: vi.fn().mockResolvedValue(undefined),
      },
    },
    detectAllFaces: vi.fn().mockReturnValue({
      withFaceExpressions: vi.fn().mockResolvedValue([])
    }),
    TinyFaceDetectorOptions: vi.fn(),
  };
});

describe("CapturaDeSelfie", () => {
  it("muestra el estado de carga inicial de los modelos de IA", () => {
    render(<CapturaDeSelfie solicitudId="sol-123" onCompletado={vi.fn()} />);
    
    // Verifica que se muestra el título
    expect(screen.getByText("Selfie de verificación")).toBeInTheDocument();
    
    // Verifica que se muestra el texto inicial (ya sea cargando modelos o cámara)
    expect(
      screen.getByText(/Iniciando motor de IA...|Pidiendo acceso a tu cámara/i)
    ).toBeInTheDocument();
  });
});
