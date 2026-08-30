import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { FormularioDeRegistro } from "./FormularioDeRegistro";

const CONTRASENA_VALIDA = "Cont!rasena2026#";

/** Rellena el formulario con datos válidos y devuelve el botón de envío. */
async function rellenarConDatosValidos(usuario: ReturnType<typeof userEvent.setup>) {
  await usuario.type(screen.getByLabelText(/correo electrónico/i), "ana.quispe@ejemplo.pe");
  await usuario.type(screen.getByLabelText(/celular/i), "987654321");
  await usuario.type(screen.getByLabelText(/^contraseña$/i), CONTRASENA_VALIDA);
  await usuario.click(screen.getByRole("checkbox"));
  return screen.getByRole("button", { name: /continuar con mi dni/i });
}

describe("Formulario de registro · HU-01", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
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

  it("escenario 4 · no envía nada si no se aceptan los términos", async () => {
    const usuario = userEvent.setup();
    render(<FormularioDeRegistro />);

    await usuario.type(screen.getByLabelText(/correo/i), "ana.quispe@ejemplo.pe");
    await usuario.type(screen.getByLabelText(/celular/i), "987654321");
    await usuario.type(screen.getByLabelText(/^contraseña$/i), CONTRASENA_VALIDA);
    await usuario.click(screen.getByRole("button", { name: /continuar con mi dni/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/debes aceptar los términos/i);
    expect(globalThis.fetch).not.toHaveBeenCalled();
  });

  it("la casilla de consentimiento nunca viene marcada por defecto", () => {
    // Un consentimiento premarcado no es consentimiento informado, y la Ley N.o 29733
    // no lo admite.
    render(<FormularioDeRegistro />);

    expect(screen.getByRole("checkbox")).not.toBeChecked();
  });

  it("escenario 3 · no envía si la contraseña incumple la política", async () => {
    const usuario = userEvent.setup();
    render(<FormularioDeRegistro />);

    await usuario.type(screen.getByLabelText(/correo/i), "ana.quispe@ejemplo.pe");
    await usuario.type(screen.getByLabelText(/celular/i), "987654321");
    await usuario.type(screen.getByLabelText(/^contraseña$/i), "corta");
    await usuario.click(screen.getByRole("checkbox"));
    await usuario.click(screen.getByRole("button", { name: /continuar con mi dni/i }));

    expect(globalThis.fetch).not.toHaveBeenCalled();
  });

  it("rechaza un celular que no es móvil peruano", async () => {
    const usuario = userEvent.setup();
    render(<FormularioDeRegistro />);

    await usuario.type(screen.getByLabelText(/correo/i), "ana.quispe@ejemplo.pe");
    await usuario.type(screen.getByLabelText(/celular/i), "12345678");
    await usuario.type(screen.getByLabelText(/^contraseña$/i), CONTRASENA_VALIDA);
    await usuario.click(screen.getByRole("checkbox"));
    await usuario.click(screen.getByRole("button", { name: /continuar con mi dni/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/nueve dígitos/i);
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
    await usuario.click(await rellenarConDatosValidos(usuario));

    await waitFor(() => {
      expect(screen.getByText(/revisa tu correo/i)).toBeInTheDocument();
    });

    const [url, opciones] = vi.mocked(globalThis.fetch).mock.calls[0];
    expect(String(url)).toContain("/api/v1/registro");
    expect(JSON.parse(String((opciones as RequestInit).body))).toEqual({
      correo: "ana.quispe@ejemplo.pe",
      celular: "987654321",
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
    await usuario.click(await rellenarConDatosValidos(usuario));

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
    await usuario.click(await rellenarConDatosValidos(usuario));

    expect(await screen.findByRole("alert")).toHaveTextContent(/nueve dígitos/i);
  });

  it("explica qué hacer cuando la red falla, sin culpar a quien lo lee", async () => {
    vi.mocked(globalThis.fetch).mockRejectedValue(new TypeError("Failed to fetch"));

    const usuario = userEvent.setup();
    render(<FormularioDeRegistro />);
    await usuario.click(await rellenarConDatosValidos(usuario));

    expect(await screen.findByRole("alert")).toHaveTextContent(/revisa tu conexión/i);
  });
});
