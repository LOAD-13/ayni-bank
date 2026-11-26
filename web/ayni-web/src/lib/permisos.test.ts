import { describe, expect, it } from "vitest";

import { permisosDelToken } from "./sesion";

const jwtCon = (carga: unknown) => `a.${btoa(JSON.stringify(carga))}.c`;

describe("Permisos del token (AYNI-281)", () => {
  it("lee el claim permisos", () => {
    expect(
      permisosDelToken(jwtCon({ sub: "u", permisos: ["cuenta:leer", "auditoria:leer"] })),
    ).toEqual(["cuenta:leer", "auditoria:leer"]);
  });

  it("un token antiguo sin el claim, o con algo que no es una lista, no da permisos", () => {
    expect(permisosDelToken(jwtCon({ sub: "u" }))).toEqual([]);
    expect(permisosDelToken(jwtCon({ permisos: "auditoria:leer" }))).toEqual([]);
  });

  it("descarta lo que no sea texto y tolera un token que no es un JWT", () => {
    expect(permisosDelToken(jwtCon({ permisos: ["cuenta:leer", 7, null] }))).toEqual([
      "cuenta:leer",
    ]);
    expect(permisosDelToken("no-es-un-jwt")).toEqual([]);
  });
});
