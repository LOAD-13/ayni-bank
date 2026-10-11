"use client";

import { ShieldCheck } from "lucide-react";
import { useId, useState, type FormEvent } from "react";

import { Boton } from "@/componentes/Boton";
import type { TipoDeSegundoFactor } from "@/lib/api";

interface Props {
  metodo: TipoDeSegundoFactor;
  onVerificar: (codigo: string) => Promise<void>;
  onCancelar: () => void;
  enviando: boolean;
  error: string | null;
}

const INDICACION: Record<TipoDeSegundoFactor, string> = {
  APP_AUTENTICADORA: "Escribe el código de 6 dígitos de tu app autenticadora.",
  CORREO_ELECTRONICO: "Te enviamos un código de 6 dígitos a tu correo. Vence en 10 minutos.",
  SMS: "Te enviamos un código de 6 dígitos por SMS.",
};

/**
 * HU-07 · Confirmar la transferencia con el segundo factor (ADR-0031).
 *
 * Un solo campo y no seis: el navegador y el gestor de contraseñas rellenan
 * `autocomplete="one-time-code"` en un único input, y el teclado numérico aparece solo.
 */
export function PasoDeConfirmacion({ metodo, onVerificar, onCancelar, enviando, error }: Props) {
  const idCodigo = useId();
  const [codigo, setCodigo] = useState("");
  const completo = /^\d{6}$/.test(codigo);

  async function alEnviar(evento: FormEvent) {
    evento.preventDefault();
    if (completo) {
      await onVerificar(codigo);
      setCodigo("");
    }
  }

  return (
    <section
      aria-labelledby="titulo-segundo-factor"
      className="mt-6 rounded-[20px] border border-azul-200 bg-blanco p-7"
    >
      <span className="inline-flex h-11 w-11 items-center justify-center rounded-full bg-azul-050 text-azul-700">
        <ShieldCheck aria-hidden="true" className="h-5 w-5" />
      </span>
      <h2 id="titulo-segundo-factor" className="mt-4 text-h3 font-bold text-azul-900">
        Confirma que eres tú
      </h2>
      <p className="mt-2 text-body text-gris-700">{INDICACION[metodo]}</p>

      {error && (
        <p
          role="alert"
          className="mt-4 rounded-lg border border-error bg-blanco p-4 text-body text-error"
        >
          {error}
        </p>
      )}

      <form onSubmit={alEnviar} noValidate className="mt-5 flex flex-col gap-4">
        <label htmlFor={idCodigo} className="text-small font-semibold text-gris-700">
          Código de verificación
        </label>
        <input
          id={idCodigo}
          inputMode="numeric"
          autoComplete="one-time-code"
          maxLength={6}
          value={codigo}
          onChange={(e) => setCodigo(e.target.value.replace(/\D/g, "").slice(0, 6))}
          className="min-h-[48px] rounded-[12px] border border-gris-300 px-4 text-center text-[22px] tracking-[0.4em] text-gris-900 outline-none focus:border-azul-600"
        />
        <div className="flex flex-col gap-3 sm:flex-row">
          <Boton type="submit" cargando={enviando} disabled={!completo}>
            Confirmar y transferir
          </Boton>
          <Boton type="button" variante="contorno" disabled={enviando} onClick={onCancelar}>
            Cancelar
          </Boton>
        </div>
      </form>
    </section>
  );
}
