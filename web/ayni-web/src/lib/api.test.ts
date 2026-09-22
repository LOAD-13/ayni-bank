import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  consultarCuenta,
  consultarTitular,
  ErrorDeApi,
  generarDesafioCodigo,
  presentarCredenciales,
  reenviarCodigoRegistro,
  registrar,
  seleccionarMetodoSegundoFactor,
  solicitarUrlDeSubida,
  subirDocumento,
  verificarCodigoRegistro,
  verificarDesafioCodigo,
  verificarSegundoFactor,
} from "./api";

describe("api client", () => {
  const fetchOriginal = globalThis.fetch;

  beforeEach(() => {
    globalThis.fetch = vi.fn();
  });

  afterEach(() => {
    globalThis.fetch = fetchOriginal;
  });

  it("ErrorDeApi porCampo mapea errores por nombre de campo", () => {
    const err = new ErrorDeApi(
      {
        title: "Bad Request",
        status: 400,
        errores: [
          { campo: "correo", mensaje: "Correo inválido" },
          { campo: "correo", mensaje: "Correo repetido" },
          { campo: "celular", mensaje: "Celular inválido" },
        ],
      },
      400,
    );

    const porCampo = err.porCampo();
    expect(porCampo).toEqual({
      correo: "Correo inválido",
      celular: "Celular inválido",
    });
  });

  it("seleccionarMetodoSegundoFactor hace un POST al endpoint de método de 2FA", async () => {
    const mockRespuesta = {
      id: "m-123",
      usuarioId: "u-123",
      tipo: "CORREO_ELECTRONICO",
      secreto: null,
      confirmado: true,
    };

    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockRespuesta,
    });

    const res = await seleccionarMetodoSegundoFactor("u-123", "CORREO_ELECTRONICO");
    expect(res).toEqual(mockRespuesta);
    expect(globalThis.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/usuarios/u-123/segundo-factor/metodo"),
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ tipo: "CORREO_ELECTRONICO" }),
      }),
    );
  });

  it("generarDesafioCodigo hace un POST para solicitar un desafío OTP", async () => {
    const mockDesafio = {
      id: "d-123",
      usuarioId: "u-123",
      tipoFactor: "CORREO_ELECTRONICO",
      expiraEn: "2026-09-22T00:00:00Z",
      intentosRealizados: 0,
      verificado: false,
    };

    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockDesafio,
    });

    const res = await generarDesafioCodigo("u-123", "CORREO_ELECTRONICO");
    expect(res).toEqual(mockDesafio);
    expect(globalThis.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/segundo-factor/desafio/usuario/u-123/generar"),
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("verificarDesafioCodigo valida un código OTP", async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => ({}),
    });

    await verificarDesafioCodigo("d-123", "123456");
    expect(globalThis.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/segundo-factor/desafio/d-123/verificar"),
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ codigo: "123456" }),
      }),
    );
  });

  it("reenviarCodigoRegistro y verificarCodigoRegistro funcionan correctamente", async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: async () => ({}),
    });

    await reenviarCodigoRegistro("u-123", "CORREO_ELECTRONICO");
    expect(globalThis.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/registro/u-123/codigo/reenviar"),
      expect.objectContaining({ method: "POST" }),
    );

    await verificarCodigoRegistro("u-123", "CORREO_ELECTRONICO", "654321");
    expect(globalThis.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/registro/u-123/codigo/verificar"),
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ tipoFactor: "CORREO_ELECTRONICO", codigo: "654321" }),
      }),
    );
  });

  it("consultarTitular y consultarCuenta hacen GET requests", async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: async () => ({}),
    });

    await consultarTitular("u-123");
    expect(globalThis.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/usuarios/u-123/resumen"),
      expect.objectContaining({ method: "GET" }),
    );

    await consultarCuenta("u-123");
    expect(globalThis.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/cuentas/titular/u-123"),
      expect.objectContaining({ method: "GET" }),
    );
  });

  it("registrar, presentarCredenciales y verificarSegundoFactor hacen POST", async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: async () => ({}),
    });

    await registrar({
      nombres: "Ana",
      apellidos: "Quispe",
      tipoDocumento: "DNI",
      numeroDocumento: "12345678",
      fechaNacimiento: "1995-01-01",
      correo: "ana@example.com",
      celular: "987654321",
      contrasena: "Pass!123456",
      aceptaTerminos: true,
    });
    expect(globalThis.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/registro"),
      expect.objectContaining({ method: "POST" }),
    );

    await presentarCredenciales("ana@example.com", "Pass!123456");
    expect(globalThis.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/sesion"),
      expect.objectContaining({ method: "POST" }),
    );

    await verificarSegundoFactor("d-1", "123456");
    expect(globalThis.fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/sesion/segundo-factor"),
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("solicitarUrlDeSubida y subirDocumento funcionan", async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => ({ url: "http://minio/upload", expiraEn: "5m" }),
    });

    const resUrl = await solicitarUrlDeSubida("sol-1", "ANVERSO", "png");
    expect(resUrl.url).toBe("http://minio/upload");

    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
    });

    const testFile = new File(["test"], "test.png", { type: "image/png" });
    await subirDocumento("http://minio/upload", testFile);
    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://minio/upload",
      expect.objectContaining({ method: "PUT" }),
    );
  });

  it("lanza ErrorDeApi cuando la respuesta HTTP no es ok", async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: false,
      status: 401,
      json: async () => ({ title: "No autorizado", status: 401 }),
    });

    await expect(presentarCredenciales("ana@example.com", "wrong")).rejects.toThrow(ErrorDeApi);
  });

  it("lanza ErrorDeApi por fallo de red", async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockRejectedValueOnce(
      new Error("Network error"),
    );

    await expect(consultarTitular("u-123")).rejects.toThrow("No pudimos conectar con Ayni");
  });
});
