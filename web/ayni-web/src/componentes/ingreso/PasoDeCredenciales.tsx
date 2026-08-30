"use client";

import { Eye, EyeOff, Lock, ScanFace, TriangleAlert } from "lucide-react";
import Link from "next/link";
import { useId, useState, type FormEvent } from "react";

import { CuentaAtras } from "./CuentaAtras";

interface Props {
  onEnviar: (correo: string, contrasena: string) => Promise<void>;
  enviando: boolean;
  error: string | null;
  /** Segundos que faltan para poder reintentar, cuando el ingreso está pausado. */
  esperaSegundos: number | null;
  onExpirarLaEspera: () => void;
}

/**
 * Pantalla «Entra a tu banca», según el diseño aprobado en pen.dev.
 *
 * **Dos elementos del prototipo quedan fuera del Sprint 1 y llevan a la página de
 * pendientes**, en lugar de fingir que funcionan: «¿Olvidaste tu contraseña?», que es
 * HU-06, y «Entrar con la biometría del dispositivo», que exige WebAuthn y no está en
 * ninguna historia de este sprint.
 */
export function PasoDeCredenciales({
  onEnviar,
  enviando,
  error,
  esperaSegundos,
  onExpirarLaEspera,
}: Props) {
  const idCorreo = useId();
  const idContrasena = useId();

  const [correo, setCorreo] = useState("");
  const [contrasena, setContrasena] = useState("");
  const [recordar, setRecordar] = useState(false);
  const [visible, setVisible] = useState(false);

  const pausado = esperaSegundos !== null;

  async function alEnviar(evento: FormEvent) {
    evento.preventDefault();
    if (!pausado) {
      await onEnviar(correo, contrasena);
    }
  }

  return (
    <>
      <h2 className="text-[28px] leading-tight font-bold text-azul-700">Entra a tu banca</h2>
      <p className="mt-2 text-[14.5px] text-gris-700">
        Usa el correo con el que abriste tu cuenta.
      </p>

      {pausado && (
        <div role="alert" className="mt-6 rounded-[14px] border border-error/30 bg-error/5 p-5">
          <p className="flex items-center gap-2.5 text-[14px] font-bold text-error">
            <Lock aria-hidden="true" className="h-4 w-4 shrink-0" />
            Ingreso pausado por seguridad
          </p>
          <p className="mt-2 text-[13.5px] leading-[1.5] text-gris-700">
            Detectamos varios intentos seguidos que no coincidieron. Es una protección automática de
            la cuenta, no un bloqueo definitivo.
          </p>
          <CuentaAtras segundos={esperaSegundos} onTerminar={onExpirarLaEspera} />
        </div>
      )}

      {error && !pausado && (
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

        <div className="flex flex-col gap-1.5">
          <label htmlFor={idContrasena} className="text-[13.5px] font-semibold text-gris-700">
            Contraseña
          </label>
          <div className="campo flex min-h-[48px] items-center rounded-[12px] border border-gris-300 bg-blanco">
            <input
              id={idContrasena}
              type={visible ? "text" : "password"}
              autoComplete="current-password"
              value={contrasena}
              onChange={(e) => setContrasena(e.target.value)}
              className="min-w-0 flex-1 bg-transparent px-4 text-[15px] text-gris-900 outline-none"
            />
            {/* El botón alterna y lo anuncia: quien usa lector de pantalla necesita saber
                si la contraseña está a la vista, que es justo lo que no puede comprobar. */}
            <button
              type="button"
              onClick={() => setVisible((v) => !v)}
              aria-label={visible ? "Ocultar la contraseña" : "Mostrar la contraseña"}
              aria-pressed={visible}
              className="px-4 text-gris-500 hover:text-gris-700"
            >
              {visible ? (
                <EyeOff aria-hidden="true" className="h-4 w-4" />
              ) : (
                <Eye aria-hidden="true" className="h-4 w-4" />
              )}
            </button>
          </div>
        </div>

        {/* A 14 px las dos piezas sumaban más que el ancho de la tarjeta y se partían en
            dos líneas, que no es lo que muestra el diseño. La solución es bajar a 13.5 px,
            con lo que caben de sobra, y NO forzar la fila con `whitespace-nowrap`: eso
            arreglaba el escritorio y desbordaba 30 px en un móvil de 390. Que envuelvan
            cuando de verdad no quepan es justamente lo que debe pasar. */}
        <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-3">
          <label className="flex items-center gap-2.5 text-[13.5px] text-gris-700">
            <input
              type="checkbox"
              checked={recordar}
              onChange={(e) => setRecordar(e.target.checked)}
              className="h-[18px] w-[18px] rounded border-gris-300 accent-azul-700"
            />
            Recordar este dispositivo
          </label>
          <Link
            href="/pendiente"
            className="text-[13.5px] font-semibold text-azul-600 hover:underline"
          >
            ¿Olvidaste tu contraseña?
          </Link>
        </div>

        <button
          type="submit"
          disabled={enviando || pausado}
          aria-busy={enviando}
          className="inline-flex min-h-[48px] w-full items-center justify-center rounded-full bg-azul-700 px-7 text-[15px] font-bold text-blanco hover:bg-azul-800 disabled:opacity-60"
        >
          {enviando ? "Un momento…" : "Ingresar"}
        </button>
      </form>

      <div className="my-6 flex items-center gap-4">
        <hr className="flex-1 border-gris-300" />
        <span className="text-[13px] text-gris-500">o</span>
        <hr className="flex-1 border-gris-300" />
      </div>

      <Link
        href="/pendiente"
        className="inline-flex min-h-[48px] w-full items-center justify-center gap-2.5 rounded-full border border-azul-700 px-7 text-[15px] font-semibold text-azul-700 hover:bg-azul-050"
      >
        <ScanFace aria-hidden="true" className="h-[18px] w-[18px]" />
        Entrar con la biometría del dispositivo
      </Link>

      <p className="mt-6 text-center text-[14px] text-gris-700">
        ¿Aún no tienes cuenta?{" "}
        <Link href="/registro" className="font-semibold text-azul-600 hover:underline">
          Ábrela en 5 minutos
        </Link>
      </p>
    </>
  );
}
