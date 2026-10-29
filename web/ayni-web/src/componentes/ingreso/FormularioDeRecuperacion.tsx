"use client";

import { MailCheck, TriangleAlert } from "lucide-react";
import Link from "next/link";
import { useId, useState, type FormEvent } from "react";

import { esCorreoValido } from "@/dominio/politicaDeContrasena";
import { ErrorDeApi, solicitarRecuperacion } from "@/lib/api";

/**
 * HU-21 · Paso 1: pedir el enlace.
 *
 * La confirmación es la **misma** exista o no la cuenta, igual que la respuesta del
 * servidor (ADR-0008): si la pantalla dijera «no encontramos ese correo», la protección
 * del servidor no serviría de nada. El correo no se pone en la URL de la confirmación:
 * es dato personal y las URL acaban en el historial y en los registros.
 */
export function FormularioDeRecuperacion() {
  const idCorreo = useId();
  const [correo, setCorreo] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [enviado, setEnviado] = useState(false);

  async function alEnviar(evento: FormEvent) {
    evento.preventDefault();
    if (!esCorreoValido(correo.trim())) {
      setError("Escribe un correo válido, como ana.quispe@ejemplo.pe.");
      return;
    }
    setEnviando(true);
    setError(null);
    try {
      await solicitarRecuperacion(correo.trim());
      setEnviado(true);
    } catch (fallo) {
      setError(
        fallo instanceof ErrorDeApi && fallo.estado !== 0
          ? "No pudimos procesar el correo. Revisa que esté bien escrito."
          : "No pudimos conectar con Ayni. Revisa tu conexión e inténtalo de nuevo.",
      );
    } finally {
      setEnviando(false);
    }
  }

  if (enviado) {
    return (
      <>
        <span className="inline-flex h-12 w-12 items-center justify-center rounded-full bg-azul-050 text-azul-700">
          <MailCheck aria-hidden="true" className="h-6 w-6" />
        </span>
        <h2 className="mt-5 text-[28px] leading-tight font-bold text-azul-700">Revisa tu correo</h2>
        <p role="status" className="mt-3 text-[14.5px] leading-[1.6] text-gris-700">
          Si el correo corresponde a una cuenta de Ayni, te enviamos un enlace para cambiar tu
          contraseña. Vence en 30 minutos y sirve una sola vez.
        </p>
        <ul className="mt-5 flex list-disc flex-col gap-1.5 pl-5 text-[13.5px] text-gris-700">
          <li>Si no lo ves en unos minutos, revisa la carpeta de spam.</li>
          <li>Si pides otro enlace, solo vale el último.</li>
          <li>Nadie de Ayni te pedirá la contraseña ni tus códigos.</li>
        </ul>
        <Link
          href="/ingresar"
          className="mt-7 inline-flex min-h-[48px] w-full items-center justify-center rounded-full bg-azul-700 px-7 text-[15px] font-bold text-blanco hover:bg-azul-800"
        >
          Volver a ingresar
        </Link>
      </>
    );
  }

  return (
    <>
      <h2 className="text-[28px] leading-tight font-bold text-azul-700">Recupera tu contraseña</h2>
      <p className="mt-2 text-[14.5px] text-gris-700">
        Escribe el correo de tu cuenta y te enviaremos un enlace para crear una contraseña nueva.
      </p>

      {error && (
        <p
          role="alert"
          className="mt-6 flex items-start gap-2.5 rounded-[12px] border border-error bg-blanco p-4 text-[13.5px] text-error"
        >
          <TriangleAlert aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0" />
          {error}
        </p>
      )}

      <form onSubmit={alEnviar} noValidate className="mt-7 flex flex-col gap-5">
        <div className="flex flex-col gap-1.5">
          <label htmlFor={idCorreo} className="text-[13.5px] font-semibold text-gris-700">
            Correo electrónico
          </label>
          <div className="campo flex min-h-[48px] items-center rounded-[12px] border border-gris-300 bg-blanco">
            <input
              id={idCorreo}
              type="email"
              inputMode="email"
              autoComplete="username"
              placeholder="ana.quispe@ejemplo.pe"
              value={correo}
              onChange={(e) => setCorreo(e.target.value)}
              className="min-w-0 flex-1 bg-transparent px-4 text-[15px] text-gris-900 outline-none placeholder:text-gris-500"
            />
          </div>
        </div>

        <button
          type="submit"
          disabled={enviando}
          aria-busy={enviando}
          className="inline-flex min-h-[48px] w-full items-center justify-center rounded-full bg-azul-700 px-7 text-[15px] font-bold text-blanco hover:bg-azul-800 disabled:opacity-60"
        >
          {enviando ? "Un momento…" : "Enviar enlace"}
        </button>
      </form>

      <p className="mt-6 text-center text-[14px] text-gris-700">
        ¿La recordaste?{" "}
        <Link href="/ingresar" className="font-semibold text-azul-600 hover:underline">
          Vuelve a ingresar
        </Link>
      </p>
    </>
  );
}
