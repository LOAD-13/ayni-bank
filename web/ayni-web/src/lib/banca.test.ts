import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  cerrarSesion,
  consultarMiCuenta,
  consultarMovimientos,
  depositarSimulado,
  renovarSesion,
  iniciarConfirmacion,
  transferir,
  verificarConfirmacion,
} from "./api";
import { formatearFecha, formatearImporte, normalizarImporte, nuevaClave } from "./formato";
import {
  guardarSesion,
  obtenerToken,
  olvidarSesion,
  tokenVigente,
  usuarioDelToken,
} from "./sesion";

function respuesta(estado: number, cuerpo?: unknown): Response {
  return {
    ok: estado >= 200 && estado < 300,
    status: estado,
    json: async () => cuerpo,
  } as Response;
}

/** JWT sin firmar con el `sub` indicado: al cliente solo le interesa leerlo. */
function jwtCon(sub: string): string {
  const carga = btoa(JSON.stringify({ sub, iss: "ayni-identity-service" }))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
  return `cabecera.${carga}.firma`;
}

describe("formato de importes", () => {
  it("agrupa los miles y fija dos decimales sin pasar por coma flotante", () => {
    expect(formatearImporte("12480.65")).toBe("S/ 12 480.65");
    expect(formatearImporte("0.1")).toBe("S/ 0.10");
    expect(formatearImporte("1000000")).toBe("S/ 1 000 000.00");
    expect(formatearImporte("-1000.00")).toBe("−S/ 1 000.00");
    expect(formatearImporte("5", "USD")).toBe("US$ 5.00");
  });

  it("normaliza lo que escribe la persona o lo rechaza", () => {
    expect(normalizarImporte("1 250,5")).toBe("1250.50");
    expect(normalizarImporte("0050")).toBe("50.00");
    expect(normalizarImporte("10.555")).toBeNull();
    expect(normalizarImporte("-5")).toBeNull();
    expect(normalizarImporte("abc")).toBeNull();
    expect(normalizarImporte("")).toBeNull();
  });

  it("muestra la fecha en hora de Lima aunque llegue en UTC", () => {
    expect(formatearFecha("2026-10-02T15:04:00Z")).toMatch(/2 oct.* 2026.*10:04/);
  });

  it("cada clave de idempotencia es distinta", () => {
    expect(nuevaClave()).not.toBe(nuevaClave());
  });
});

describe("sesión en memoria", () => {
  const fetchOriginal = globalThis.fetch;

  beforeEach(() => {
    globalThis.fetch = vi.fn();
    olvidarSesion();
  });

  afterEach(() => {
    globalThis.fetch = fetchOriginal;
  });

  it("un token a punto de caducar no se usa: margen de 30 segundos", () => {
    const ahora = new Date("2026-10-02T15:00:00Z");
    guardarSesion({ tokenDeAcceso: "t", expiraEn: "2026-10-02T15:00:20Z" });
    expect(tokenVigente(ahora)).toBeNull();
    guardarSesion({ tokenDeAcceso: "t", expiraEn: "2026-10-02T15:15:00Z" });
    expect(tokenVigente(ahora)).toBe("t");
  });

  it("sin token en memoria, lo recupera con la cookie de renovación", async () => {
    vi.mocked(fetch).mockResolvedValue(
      respuesta(200, { tokenDeAcceso: "nuevo", expiraEn: "2999-01-01T00:00:00Z" }),
    );

    await expect(obtenerToken()).resolves.toBe("nuevo");
    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/sesion/renovacion"),
      expect.objectContaining({ method: "POST", credentials: "include" }),
    );
    // Ya en memoria: la segunda vez no vuelve a pedirlo.
    await obtenerToken();
    expect(fetch).toHaveBeenCalledTimes(1);
  });

  it("si la renovación falla, no hay sesión", async () => {
    vi.mocked(fetch).mockResolvedValue(respuesta(401, { title: "Sesión expirada" }));
    await expect(obtenerToken()).resolves.toBeNull();
  });

  it("lee el usuario del token y tolera tokens mal formados", () => {
    expect(usuarioDelToken(jwtCon("5d1f3c9e-1b2a-4c3d-8e9f-0a1b2c3d4e5f"))).toBe(
      "5d1f3c9e-1b2a-4c3d-8e9f-0a1b2c3d4e5f",
    );
    expect(usuarioDelToken("no-es-un-jwt")).toBeNull();
    expect(usuarioDelToken(`a.${btoa(JSON.stringify({ sub: 42 }))}.c`)).toBeNull();
  });
});

describe("llamadas de la banca", () => {
  const fetchOriginal = globalThis.fetch;

  beforeEach(() => {
    globalThis.fetch = vi.fn();
  });

  afterEach(() => {
    globalThis.fetch = fetchOriginal;
  });

  function cabecerasDe(llamada = 0): Record<string, string> {
    return vi.mocked(fetch).mock.calls[llamada][1]!.headers as Record<string, string>;
  }

  it("las consultas llevan el token como Bearer", async () => {
    vi.mocked(fetch).mockResolvedValue(respuesta(200, []));

    await consultarMiCuenta("tk");
    await consultarMovimientos("tk", 5);

    expect(cabecerasDe(0).Authorization).toBe("Bearer tk");
    expect(vi.mocked(fetch).mock.calls[1][0]).toContain("/api/v1/cuentas/mia/movimientos?limite=5");
  });

  it("transferir y depositar viajan con la clave de idempotencia", async () => {
    vi.mocked(fetch).mockResolvedValue(respuesta(201, { movimientoId: "m" }));

    await transferir("tk", "00111000000031", "150.00", "Cena", "clave-1", "confirmacion-1");
    await depositarSimulado("tk", "100.00", "clave-2");

    expect(cabecerasDe(0)["Idempotency-Key"]).toBe("clave-1");
    expect(cabecerasDe(0)["X-Ayni-Confirmacion"]).toBe("confirmacion-1");
    expect(JSON.parse(vi.mocked(fetch).mock.calls[0][1]!.body as string)).toEqual({
      cuentaDestino: "00111000000031",
      importe: "150.00",
      concepto: "Cena",
    });
    expect(cabecerasDe(1)["Idempotency-Key"]).toBe("clave-2");
  });

  it("cerrar sesión es un DELETE que acepta 204 sin cuerpo", async () => {
    vi.mocked(fetch).mockResolvedValue(respuesta(204));

    await expect(cerrarSesion()).resolves.toBeUndefined();
    expect(vi.mocked(fetch).mock.calls[0][1]!.method).toBe("DELETE");
  });

  it("renovar es un POST con credenciales para que viaje la cookie", async () => {
    vi.mocked(fetch).mockResolvedValue(respuesta(200, { tokenDeAcceso: "x", expiraEn: "y" }));

    await renovarSesion();

    expect(vi.mocked(fetch).mock.calls[0][1]).toMatchObject({
      method: "POST",
      credentials: "include",
    });
  });

  it("la confirmacion con segundo factor va a identity con el token de acceso", async () => {
    vi.mocked(fetch).mockResolvedValue(
      respuesta(201, { confirmacionId: "c-1", metodo: "APP_AUTENTICADORA" }),
    );
    await iniciarConfirmacion("tk", {
      destino: "1",
      importe: "2",
      moneda: "PEN",
      claveIdempotencia: "k",
    });
    vi.mocked(fetch).mockResolvedValue(respuesta(200, { token: "t", expiraEn: "x" }));
    await expect(verificarConfirmacion("tk", "c-1", "123456")).resolves.toEqual({
      token: "t",
      expiraEn: "x",
    });

    const llamadas = vi.mocked(fetch).mock.calls;
    expect(llamadas.at(-2)![0]).toContain("/api/v1/confirmaciones");
    expect(llamadas.at(-1)![0]).toContain("/api/v1/confirmaciones/c-1/verificacion");
    expect(JSON.parse(llamadas.at(-1)![1]!.body as string)).toEqual({ codigo: "123456" });
  });
});
