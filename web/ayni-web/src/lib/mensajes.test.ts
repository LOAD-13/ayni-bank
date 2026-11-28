import { describe, expect, it } from "vitest";

import { falloInesperado } from "./mensajes";

describe("falloInesperado (HU-17, AYNI-292)", () => {
  it("dice qué pasó, qué hacer y a dónde acudir", () => {
    expect(falloInesperado("subir tu foto")).toBe(
      "No pudimos subir tu foto. Revisa tu conexión y vuelve a intentarlo en unos minutos. " +
        "Si sigue fallando, escríbenos desde la sección Ayuda.",
    );
  });

  it("si mueve dinero, pide revisar los movimientos antes de repetir", () => {
    expect(falloInesperado("completar la transferencia", { mueveDinero: true })).toMatch(
      /^No pudimos completar la transferencia\. Antes de repetirla, revisa en tus movimientos si ya se hizo\./,
    );
  });
});
