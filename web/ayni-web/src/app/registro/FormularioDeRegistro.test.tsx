import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { FormularioDeRegistro } from "./FormularioDeRegistro";

const CONTRASENA_VALIDA = "Cont!rasena2026#";

/** Todo lo que el formulario aprobado pide, con valores que pasan la validación. */
const DATOS_VALIDOS = {
  nombres: "Ana Lucía",
  apellidos: "Quispe Mendoza",
  numeroDocumento: "45678912",
  fechaNacimiento: "1998-03-14",
  celular: "987654321",
  correo: "ana.quispe@ejemplo.pe",
  contrasena: CONTRASENA_VALIDA,
} as const;

type Campos = Partial<Record<keyof typeof DATOS_VALIDOS, string>>;

/**
 * Rellena el formulario entero y devuelve el botón de envío.
 *
 * Recibe sobrescrituras para que cada prueba invalide **un solo campo** y deje el resto
 * bien. Si se dejaran varios en blanco aparecerían varios avisos a la vez, y la prueba
 * pasaría por el error equivocado sin que nadie se enterara.
 */
async function rellenar(
  usuario: ReturnType<typeof userEvent.setup>,
  sobrescrituras: Campos = {},
  { aceptarTerminos = true } = {},
) {
  const datos = { ...DATOS_VALIDOS, ...sobrescrituras };

  await usuario.type(screen.getByLabelText(/^nombres$/i), datos.nombres);
  await usuario.type(screen.getByLabelText(/^apellidos$/i), datos.apellidos);
  await usuario.type(screen.getByLabelText(/número de documento/i), datos.numeroDocumento);

  // `fireEvent` y no `type`: en jsdom un input de tipo `date` no acepta que se le teclee
  // el valor carácter a carácter, y el campo se queda vacío sin que la prueba falle por
  // eso, sino por lo que venga después.
  fireEvent.change(screen.getByLabelText(/fecha de nacimiento/i), {
    target: { value: datos.fechaNacimiento },
  });

  await usuario.type(screen.getByLabelText(/^celular$/i), datos.celular);
  await usuario.type(screen.getByLabelText(/correo electrónico/i), datos.correo);
  await usuario.type(screen.getByLabelText(/^contraseña$/i), datos.contrasena);
  await usuario.type(screen.getByLabelText(/confirmar contraseña/i), datos.contrasena);

  if (aceptarTerminos) {
    await usuario.click(screen.getByRole("checkbox"));
  }

  return screen.getByRole("button", { name: /continuar con mi dni/i });
}

/**
 * El aviso que menciona lo que se busca.
 *
 * Cada campo pinta su propio `role="alert"`, así que `findByRole("alert")` falla en cuanto
 * hay más de un error en pantalla. Filtrar por el texto es lo que distingue «salió el aviso
 * que esperaba» de «salió alguno».
 */
async function avisoQueDice(texto: RegExp) {
  const avisos = await screen.findAllByRole("alert");
  const encontrado = avisos.find((nodo) => texto.test(nodo.textContent ?? ""));

  expect(encontrado, `ningún aviso coincide con ${texto}`).toBeDefined();
  return encontrado as HTMLElement;
}

describe("Formulario de registro · HU-01", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("pide los nueve campos del diseño aprobado", () => {
    render(<FormularioDeRegistro />);

    for (const etiqueta of [
      /^nombres$/i,
      /^apellidos$/i,
      /tipo de documento/i,
      /número de documento/i,
      /fecha de nacimiento/i,
      /^celular$/i,
      /correo electrónico/i,
      /^contraseña$/i,
      /confirmar contraseña/i,
    ]) {
      expect(screen.getByLabelText(etiqueta)).toBeInTheDocument();
    }
  });

  it("el celular lleva el prefijo +51 fuera del valor que se escribe", async () => {
    // El prefijo se ve pero no forma parte del valor: el dominio espera nueve dígitos y no
    // tiene que limpiar nada, y nadie puede borrarlo por accidente.
    const usuario = userEvent.setup();
    render(<FormularioDeRegistro />);

    await usuario.type(screen.getByLabelText(/^celular$/i), "987654321");

    expect(screen.getByText("+51")).toBeInTheDocument();
    expect(screen.getByLabelText(/^celular$/i)).toHaveValue("987654321");
  });

  it("muestra la política de contraseña antes de que se escriba nada", () => {
    // Revelar los requisitos según se incumplen convierte el registro en adivinanzas.
    render(<FormularioDeRegistro />);

    expect(screen.getByText(/mínimo 12 caracteres/i)).toBeInTheDocument();
    expect(screen.getByText(/un símbolo/i)).toBeInTheDocument();
  });

  it("marca los requisitos según se van cumpliendo", async () => {
    const usuario = userEvent.setup();
    render(<FormularioDeRegistro />);

    await usuario.type(screen.getByLabelText(/^contraseña$/i), CONTRASENA_VALIDA);

    // Cada requisito lleva su marca textual además del icono: el color solo no es
    // accesible para quien no distingue verde de rojo.
    expect(screen.getAllByText("Cumplido")).toHaveLength(5);
    expect(screen.queryByText("Falta")).not.toBeInTheDocument();
    expect(screen.getByText(/fortaleza: fuerte/i)).toBeInTheDocument();
  });

  it("la casilla de consentimiento nunca viene marcada por defecto", () => {
    // Un consentimiento premarcado no es consentimiento informado, y la Ley N.o 29733
    // no lo admite.
    render(<FormularioDeRegistro />);

    expect(screen.getByRole("checkbox")).not.toBeChecked();
  });

  it("escenario 4 · no envía nada si no se aceptan los términos", async () => {
    const usuario = userEvent.setup();
    render(<FormularioDeRegistro />);

    await usuario.click(await rellenar(usuario, {}, { aceptarTerminos: false }));

    await avisoQueDice(/debes autorizar el tratamiento/i);
    expect(globalThis.fetch).not.toHaveBeenCalled();
  });

  it("escenario 3 · no envía si la contraseña incumple la política", async () => {
    const usuario = userEvent.setup();
    render(<FormularioDeRegistro />);

    await usuario.click(await rellenar(usuario, { contrasena: "corta" }));

    await avisoQueDice(/aún faltan/i);
    expect(globalThis.fetch).not.toHaveBeenCalled();
  });

  it("rechaza un celular que no es móvil peruano", async () => {
    const usuario = userEvent.setup();
    render(<FormularioDeRegistro />);

    await usuario.click(await rellenar(usuario, { celular: "12345678" }));

    await avisoQueDice(/nueve dígitos/i);
    expect(globalThis.fetch).not.toHaveBeenCalled();
  });

  it("rechaza un documento que no tiene ocho dígitos", async () => {
    const usuario = userEvent.setup();
    render(<FormularioDeRegistro />);

    await usuario.click(await rellenar(usuario, { numeroDocumento: "123" }));

    await avisoQueDice(/ocho dígitos/i);
    expect(globalThis.fetch).not.toHaveBeenCalled();
  });

  it("no deja abrir una cuenta a un menor de edad", async () => {
    // En el Perú no se puede celebrar un contrato bancario a nombre propio antes de los
    // dieciocho. El servidor lo vuelve a comprobar; esto solo evita el viaje.
    const hace10Anos = new Date();
    hace10Anos.setFullYear(hace10Anos.getFullYear() - 10);

    const usuario = userEvent.setup();
    render(<FormularioDeRegistro />);

    await usuario.click(
      await rellenar(usuario, { fechaNacimiento: hace10Anos.toISOString().slice(0, 10) }),
    );

    await avisoQueDice(/al menos 18 años/i);
    expect(globalThis.fetch).not.toHaveBeenCalled();
  });

  it("escenario 1 · envía al gateway y muestra el mensaje neutro", async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue({
      ok: true,
      json: async () => ({
        solicitudId: "6b1f9c2a-0d3e-4a7b-8c11-5f2e9a4d7c31",
        estado: "PENDIENTE_VERIFICACION",
        mensaje: "Si el correo está disponible, te enviaremos un enlace de verificación.",
      }),
    } as Response);

    const usuario = userEvent.setup();
    render(<FormularioDeRegistro />);
    await usuario.click(await rellenar(usuario));

    await waitFor(() => {
      expect(screen.getByText(/revisa tu correo/i)).toBeInTheDocument();
    });

    const [url, opciones] = vi.mocked(globalThis.fetch).mock.calls[0];
    expect(String(url)).toContain("/api/v1/registro");

    // Los datos de identidad viajan al servidor: son el término de comparación del OCR en
    // HU-02. Ver ADR-0009.
    expect(JSON.parse(String((opciones as RequestInit).body))).toEqual({
      nombres: DATOS_VALIDOS.nombres,
      apellidos: DATOS_VALIDOS.apellidos,
      tipoDocumento: "DNI",
      numeroDocumento: DATOS_VALIDOS.numeroDocumento,
      fechaNacimiento: DATOS_VALIDOS.fechaNacimiento,
      correo: DATOS_VALIDOS.correo,
      celular: DATOS_VALIDOS.celular,
      contrasena: CONTRASENA_VALIDA,
      aceptaTerminos: true,
    });
  });

  it("escenario 2 · un correo ya registrado se ve exactamente igual", async () => {
    // El servidor responde 202 con el mismo cuerpo exista o no la cuenta. La interfaz
    // no puede introducir la diferencia que el backend evita. Ver ADR-0008.
    vi.mocked(globalThis.fetch).mockResolvedValue({
      ok: true,
      json: async () => ({
        solicitudId: "05a289a2-3290-4ecc-b876-fde9ea80f150",
        estado: "PENDIENTE_VERIFICACION",
        mensaje: "Si el correo está disponible, te enviaremos un enlace de verificación.",
      }),
    } as Response);

    const usuario = userEvent.setup();
    render(<FormularioDeRegistro />);
    await usuario.click(await rellenar(usuario));

    const aviso = await screen.findByRole("status");
    expect(aviso).toHaveTextContent(/si el correo está disponible/i);
    expect(aviso).not.toHaveTextContent(/ya existe|ya está registrado|en uso/i);
  });

  it("pinta bajo cada campo los errores que devuelve el servidor", async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue({
      ok: false,
      status: 400,
      json: async () => ({
        type: "https://ayni.pe/problemas/validacion",
        title: "Los datos enviados no son válidos",
        status: 400,
        errores: [{ campo: "celular", mensaje: "El celular debe tener nueve dígitos." }],
      }),
    } as Response);

    const usuario = userEvent.setup();
    render(<FormularioDeRegistro />);
    await usuario.click(await rellenar(usuario));

    await avisoQueDice(/nueve dígitos/i);
  });

  it("explica qué hacer cuando la red falla, sin culpar a quien lo lee", async () => {
    vi.mocked(globalThis.fetch).mockRejectedValue(new TypeError("Failed to fetch"));

    const usuario = userEvent.setup();
    render(<FormularioDeRegistro />);
    await usuario.click(await rellenar(usuario));

    await avisoQueDice(/revisa tu conexión/i);
  });
});
