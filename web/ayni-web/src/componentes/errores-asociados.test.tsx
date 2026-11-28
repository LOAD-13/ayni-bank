import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { violacionesDeAccesibilidad } from "@/test/accesibilidad";

import { PasoDeConfirmacion } from "./banca/PasoDeConfirmacion";
import { PasoDeCredenciales } from "./ingreso/PasoDeCredenciales";

/**
 * HU-17 · Etiquetas y errores asociados a su campo (AYNI-291).
 *
 * Un mensaje de error con `role="alert"` se anuncia una vez, al aparecer. Si además no está
 * enlazado al campo, quien vuelve a ese campo con el lector de pantalla oye solo la etiqueta
 * y no sabe qué falló. Estas pruebas fijan el enlace: el campo se marca inválido y su
 * descripción accesible es el propio mensaje.
 */
describe("Errores asociados a su campo", () => {
  it("en el ingreso, el error describe al correo y a la contraseña", async () => {
    const { container } = render(
      <PasoDeCredenciales
        onEnviar={vi.fn()}
        enviando={false}
        error="El correo o la contraseña no coinciden."
        esperaSegundos={null}
        onExpirarLaEspera={vi.fn()}
      />,
    );

    for (const etiqueta of ["Correo electrónico", "Contraseña"]) {
      const campo = screen.getByLabelText(etiqueta);
      expect(campo).toBeInvalid();
      expect(campo).toHaveAccessibleDescription("El correo o la contraseña no coinciden.");
    }
    expect(await violacionesDeAccesibilidad(container)).toEqual([]);
  });

  it("en el ingreso, sin error los campos no se marcan inválidos", () => {
    render(
      <PasoDeCredenciales
        onEnviar={vi.fn()}
        enviando={false}
        error={null}
        esperaSegundos={null}
        onExpirarLaEspera={vi.fn()}
      />,
    );

    expect(screen.getByLabelText("Correo electrónico")).not.toBeInvalid();
    expect(screen.getByLabelText("Correo electrónico")).not.toHaveAccessibleDescription();
  });

  it("al confirmar una transferencia, el código incorrecto queda descrito en el campo", async () => {
    const { container } = render(
      <PasoDeConfirmacion
        metodo="APP_AUTENTICADORA"
        onVerificar={vi.fn()}
        onCancelar={vi.fn()}
        enviando={false}
        error="El código no es correcto. Escribe el que muestra ahora tu app."
      />,
    );

    const campo = screen.getByLabelText("Código de verificación");
    expect(campo).toBeInvalid();
    expect(campo).toHaveAccessibleDescription(
      "El código no es correcto. Escribe el que muestra ahora tu app.",
    );
    expect(await violacionesDeAccesibilidad(container)).toEqual([]);
  });
});
