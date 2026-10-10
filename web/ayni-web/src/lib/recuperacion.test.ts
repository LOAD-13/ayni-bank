import { describe, expect, it } from "vitest";

import { tokenDelFragmento } from "./recuperacion";

const TOKEN = "Qm9uaXRhLWNsYXZlLWRlLTMyLWJ5dGVzLWFsZWF0b3Jp";

describe("tokenDelFragmento", () => {
  it("lee el token del fragmento, con o sin almohadilla", () => {
    expect(tokenDelFragmento(`#t=${TOKEN}`)).toBe(TOKEN);
    expect(tokenDelFragmento(`t=${TOKEN}`)).toBe(TOKEN);
  });

  it("acepta los caracteres del alfabeto Base64 URL", () => {
    expect(tokenDelFragmento("#t=abc_DEF-123_456-7890")).toBe("abc_DEF-123_456-7890");
  });

  it("devuelve null si falta, está vacío o no tiene forma de token", () => {
    expect(tokenDelFragmento("")).toBeNull();
    expect(tokenDelFragmento("#t=")).toBeNull();
    expect(tokenDelFragmento("#otro=valor")).toBeNull();
    expect(tokenDelFragmento("#t=corto")).toBeNull();
    expect(tokenDelFragmento("#t=<script>alert(1)</script>")).toBeNull();
    expect(tokenDelFragmento(`#t=${"a".repeat(129)}`)).toBeNull();
  });
});
