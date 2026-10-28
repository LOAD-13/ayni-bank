import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  confirmarDatosDelDni,
  consultarCuenta,
  consultarTitular,
  ErrorDeApi,
  evaluarCaptura,
  extraerDatosDelDni,
  generarDesafioCodigo,
  presentarCredenciales,
  reenviarCodigoRegistro,
  registrar,
  restablecerContrasena,
  seleccionarMetodoSegundoFactor,
  solicitarRecuperacion,
  solicitarUrlDeSubida,
  subirDocumento,
  validarEnlaceDeRecuperacion,
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

  it("solicitarUrlDeSubida devuelve el formulario y subirDocumento lo envía por POST", async () => {
    const destino = {
      url: "http://minio/ayni-kyc-documentos",
      campos: { key: "kyc/s/anverso-x.png", "Content-Type": "image/png", policy: "p" },
      claveDeObjeto: "kyc/s/anverso-x.png",
      expiraEn: "2026-09-12T10:05:00Z",
    };
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => destino,
    });

    const resUrl = await solicitarUrlDeSubida("sol-1", "ANVERSO", "png");
    expect(resUrl.claveDeObjeto).toBe("kyc/s/anverso-x.png");

    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({ ok: true });

    const testFile = new File(["test"], "test.png", { type: "image/png" });
    await subirDocumento(resUrl, testFile);

    const [url, opciones] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[1];
    expect(url).toBe("http://minio/ayni-kyc-documentos");
    expect(opciones.method).toBe("POST");
    const formulario = opciones.body as FormData;
    // Los campos de la política primero y el archivo al final: S3 ignora lo que va detrás.
    expect([...formulario.keys()]).toEqual(["key", "Content-Type", "policy", "file"]);
    expect(formulario.get("policy")).toBe("p");
  });

  it.each([400, 403])(
    "subirDocumento explica un %i de la política como archivo que no cumple",
    async (estado) => {
      // 400 = EntityTooLarge (más de 5 MB), 403 = tipo distinto del firmado: medido contra MinIO.
      (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
        ok: false,
        status: estado,
      });

      await expect(
        subirDocumento(
          { url: "http://minio/b", campos: {}, claveDeObjeto: "k", expiraEn: "" },
          new File(["x"], "x.jpg", { type: "image/jpeg" }),
        ),
      ).rejects.toMatchObject({ problema: { detail: expect.stringMatching(/5 MB/) } });
    },
  );

  it("evaluarCaptura, extraerDatosDelDni y confirmarDatosDelDni llaman a sus rutas por POST", async () => {
    const fetchSimulado = globalThis.fetch as ReturnType<typeof vi.fn>;
    fetchSimulado
      .mockResolvedValueOnce({ ok: true, json: async () => ({ estado: "ACEPTADO" }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ estado: "VERIFICACION_DIFERIDA" }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ estado: "ACEPTADO" }) });

    await evaluarCaptura("sol-1", "REVERSO", "kyc/sol-1/reverso-x.jpg");
    await extraerDatosDelDni("sol-1");
    await confirmarDatosDelDni("sol-1", {
      nombres: "Ana",
      apellidos: "Quispe",
      fechaNacimiento: "1990-05-15",
      sexo: "F",
      fechaEmision: "2021-08-20",
    });

    const llamadas = fetchSimulado.mock.calls;
    expect(llamadas[0][0]).toMatch(/\/api\/v1\/solicitudes\/sol-1\/documentos$/);
    expect(JSON.parse(llamadas[0][1].body)).toEqual({
      tipoDocumento: "REVERSO",
      claveDeObjeto: "kyc/sol-1/reverso-x.jpg",
    });
    // La lectura lanza el OCR: es un POST aunque no lleve datos, no una consulta.
    expect(llamadas[1][0]).toMatch(/\/documentos\/extraccion$/);
    expect(llamadas[1][1].method).toBe("POST");
    expect(llamadas[2][0]).toMatch(/\/identidad\/confirmacion$/);
    expect(JSON.parse(llamadas[2][1].body)).not.toHaveProperty("numero");
  });

  it("la recuperacion de contrasena manda el token en el cuerpo, nunca en la URL", async () => {
    const fetchFalso = globalThis.fetch as ReturnType<typeof vi.fn>;
    fetchFalso.mockResolvedValueOnce({
      ok: true,
      status: 202,
      json: async () => ({ mensaje: "m" }),
    });
    await expect(solicitarRecuperacion("ana@example.com")).resolves.toEqual({ mensaje: "m" });
    expect(fetchFalso).toHaveBeenLastCalledWith(
      expect.stringMatching(/\/api\/v1\/recuperacion$/),
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ correo: "ana@example.com" }),
      }),
    );

    fetchFalso.mockResolvedValue({ ok: true, status: 204, json: async () => ({}) });
    await validarEnlaceDeRecuperacion("tok");
    expect(fetchFalso).toHaveBeenLastCalledWith(
      expect.stringMatching(/\/api\/v1\/recuperacion\/validacion$/),
      expect.objectContaining({ body: JSON.stringify({ token: "tok" }) }),
    );

    await restablecerContrasena("tok", "Nueva!Clave2026#");
    expect(fetchFalso).toHaveBeenLastCalledWith(
      expect.stringMatching(/\/api\/v1\/recuperacion\/confirmacion$/),
      expect.objectContaining({
        body: JSON.stringify({ token: "tok", contrasenaNueva: "Nueva!Clave2026#" }),
      }),
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
