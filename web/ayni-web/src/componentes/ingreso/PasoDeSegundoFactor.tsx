"use client";

import { ArrowLeft, Info, RefreshCw, ShieldCheck, TriangleAlert } from "lucide-react";
import { useEffect, useRef, useState, type ClipboardEvent, type FormEvent } from "react";

import { type TipoDeSegundoFactor } from "@/lib/api";
import { InscripcionDelSegundoFactor } from "./InscripcionDelSegundoFactor";
import { SelectorMetodoSegundoFactor } from "./SelectorMetodoSegundoFactor";

const DIGITOS = 6;
const SEGUNDOS_POR_VENTANA = 30;

interface Props {
  uriDeAprovisionamiento: string | null;
  onEnviar: (codigo: string) => Promise<void>;
  onVolver: () => void;
  enviando: boolean;
  error: string | null;
  tipoMetodo?: TipoDeSegundoFactor;
  onSeleccionarMetodo?: (metodo: TipoDeSegundoFactor) => Promise<void>;
  onReenviarCodigo?: () => Promise<void>;
}

export function PasoDeSegundoFactor({
  uriDeAprovisionamiento,
  onEnviar,
  onVolver,
  enviando,
  error,
  tipoMetodo = "APP_AUTENTICADORA",
  onSeleccionarMetodo,
  onReenviarCodigo,
}: Props) {
  const [digitos, setDigitos] = useState<string[]>(Array(DIGITOS).fill(""));
  const casillas = useRef<(HTMLInputElement | null)[]>([]);
  const [restantes, setRestantes] = useState(segundosHastaElSiguienteCodigo());
  const [cambiandoMetodo, setCambiandoMetodo] = useState(false);
  const [metodoActual, setMetodoActual] = useState<TipoDeSegundoFactor>(tipoMetodo);
  const [reenviando, setReenviando] = useState(false);
  const [mensajeReenvio, setMensajeReenvio] = useState<string | null>(null);

  useEffect(() => {
    const temporizador = setInterval(() => setRestantes(segundosHastaElSiguienteCodigo()), 1000);
    return () => clearInterval(temporizador);
  }, []);

  const codigo = digitos.join("");

  function escribir(indice: number, valor: string) {
    const limpio = valor.replace(/\D/g, "").slice(-1);
    const siguientes = [...digitos];
    siguientes[indice] = limpio;
    setDigitos(siguientes);

    if (limpio && indice < DIGITOS - 1) {
      casillas.current[indice + 1]?.focus();
    }
  }

  function alPulsarTecla(indice: number, tecla: string) {
    if (tecla === "Backspace" && !digitos[indice] && indice > 0) {
      casillas.current[indice - 1]?.focus();
    }
  }

  function alPegar(evento: ClipboardEvent<HTMLInputElement>) {
    const pegado = evento.clipboardData.getData("text").replace(/\D/g, "");
    if (!pegado) return;

    evento.preventDefault();
    const siguientes = Array(DIGITOS).fill("");
    for (let i = 0; i < Math.min(pegado.length, DIGITOS); i++) {
      siguientes[i] = pegado[i];
    }
    setDigitos(siguientes);
    casillas.current[Math.min(pegado.length, DIGITOS - 1)]?.focus();
  }

  async function alEnviar(evento: FormEvent) {
    evento.preventDefault();
    if (codigo.length === DIGITOS) {
      await onEnviar(codigo);
    }
  }

  async function manejarCambioMetodo(nuevoMetodo: TipoDeSegundoFactor) {
    setMetodoActual(nuevoMetodo);
    setCambiandoMetodo(false);
    setDigitos(Array(DIGITOS).fill(""));
    if (onSeleccionarMetodo) {
      await onSeleccionarMetodo(nuevoMetodo);
    }
  }

  async function manejarReenvio() {
    if (!onReenviarCodigo || reenviando) return;
    try {
      setReenviando(true);
      setMensajeReenvio(null);
      await onReenviarCodigo();
      setMensajeReenvio("Nuevo código enviado correctamente.");
    } catch {
      setMensajeReenvio("No se pudo reenviar el código. Inténtalo de nuevo.");
    } finally {
      setReenviando(false);
    }
  }

  return (
    <>
      <button
        type="button"
        onClick={onVolver}
        className="flex w-fit items-center gap-2 text-[14px] font-semibold text-azul-600 hover:underline"
      >
        <ArrowLeft aria-hidden="true" className="h-4 w-4" />
        Volver
      </button>

      <span
        aria-hidden="true"
        className="mt-6 flex h-12 w-12 items-center justify-center rounded-[14px] bg-azul-100 text-azul-700"
      >
        <ShieldCheck className="h-5 w-5" />
      </span>

      <h2 className="mt-5 text-[28px] leading-tight font-bold text-azul-700">
        Confirma que eres tú
      </h2>

      {cambiandoMetodo ? (
        <SelectorMetodoSegundoFactor
          metodoSeleccionado={metodoActual}
          onSeleccionarMetodo={manejarCambioMetodo}
        />
      ) : (
        <>
          {uriDeAprovisionamiento && metodoActual === "APP_AUTENTICADORA" ? (
            <InscripcionDelSegundoFactor uri={uriDeAprovisionamiento} />
          ) : (
            <p className="mt-2 max-w-[400px] text-[14.5px] leading-[1.55] text-gris-700">
              {metodoActual === "APP_AUTENTICADORA" &&
                "Abre tu app de autenticación y escribe el código de 6 dígitos que aparece para Ayni Bank."}
              {metodoActual === "CORREO_ELECTRONICO" &&
                "Escribe el código de 6 dígitos de un solo uso que enviamos a tu correo electrónico."}
              {metodoActual === "SMS" &&
                "Escribe el código de 6 dígitos enviado por mensaje SMS a tu número celular."}
            </p>
          )}

          {error && (
            <p
              role="alert"
              className="mt-5 flex items-start gap-2.5 rounded-[12px] border border-error bg-blanco p-4 text-[13.5px] text-error"
            >
              <TriangleAlert aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0" />
              {error}
            </p>
          )}

          {mensajeReenvio && (
            <p className="mt-3 text-[13.5px] font-semibold text-verde-700">
              {mensajeReenvio}
            </p>
          )}

          <form onSubmit={alEnviar} noValidate className="mt-6">
            <fieldset>
              <legend className="text-[13.5px] font-semibold text-gris-700">
                Código de verificación
              </legend>

              <div className="mt-2.5 flex gap-2.5">
                {digitos.map((digito, indice) => (
                  <input
                    key={indice}
                    ref={(nodo) => {
                      casillas.current[indice] = nodo;
                    }}
                    type="text"
                    inputMode="numeric"
                    autoComplete={indice === 0 ? "one-time-code" : "off"}
                    maxLength={1}
                    aria-label={`Dígito ${indice + 1} de ${DIGITOS}`}
                    value={digito}
                    onChange={(e) => escribir(indice, e.target.value)}
                    onKeyDown={(e) => alPulsarTecla(indice, e.key)}
                    onPaste={alPegar}
                    className="campo h-[58px] w-full max-w-[58px] flex-1 rounded-[12px] border border-gris-300 bg-blanco text-center text-[24px] font-bold text-gris-900 outline-none"
                  />
                ))}
              </div>
            </fieldset>

            <div className="mt-5 rounded-[10px] border border-gris-300 bg-blanco px-4 py-3">
              <p className="flex items-center justify-between">
                <span className="text-[13.5px] text-gris-700">El código vence en</span>
                <span aria-hidden="true" className="cifra text-[18px] font-bold text-azul-700">
                  00:{String(restantes).padStart(2, "0")}
                </span>
              </p>
              <span
                aria-hidden="true"
                className="mt-2.5 block h-1.5 w-full overflow-hidden rounded-full bg-azul-100"
              >
                <span
                  className="block h-full rounded-full bg-azul-600 transition-[width] duration-1000 ease-linear"
                  style={{ width: `${(restantes / SEGUNDOS_POR_VENTANA) * 100}%` }}
                />
              </span>
            </div>

            {(metodoActual === "CORREO_ELECTRONICO" || metodoActual === "SMS") && onReenviarCodigo && (
              <div className="mt-3 flex justify-end">
                <button
                  type="button"
                  disabled={reenviando}
                  onClick={manejarReenvio}
                  className="flex items-center gap-1.5 text-[13.5px] font-semibold text-azul-600 hover:underline disabled:opacity-50"
                >
                  <RefreshCw className={`h-3.5 w-3.5 ${reenviando ? "animate-spin" : ""}`} />
                  {reenviando ? "Reenviando..." : "Reenviar código"}
                </button>
              </div>
            )}

            <button
              type="submit"
              disabled={enviando || codigo.length < DIGITOS}
              aria-busy={enviando}
              className="mt-6 inline-flex min-h-[48px] w-full items-center justify-center rounded-full bg-azul-700 px-7 text-[15px] font-bold text-blanco hover:bg-azul-800 disabled:opacity-60"
            >
              {enviando ? "Verificando…" : "Verificar y entrar"}
            </button>
          </form>

          {onSeleccionarMetodo && (
            <div className="mt-4 text-center">
              <button
                type="button"
                onClick={() => setCambiandoMetodo(true)}
                className="text-[13.5px] font-semibold text-azul-600 hover:underline"
              >
                Usar otro método de verificación
              </button>
            </div>
          )}
        </>
      )}

      <p className="mt-6 flex items-start gap-2.5 rounded-[12px] bg-gris-100 p-4 text-[13px] leading-[1.5] text-gris-700">
        <Info aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0 text-gris-500" />
        Nadie de Ayni Bank te pedirá este código por teléfono, correo ni mensaje. Si te lo piden, no
        lo compartas.
      </p>
    </>
  );
}

function segundosHastaElSiguienteCodigo(): number {
  return SEGUNDOS_POR_VENTANA - (Math.floor(Date.now() / 1000) % SEGUNDOS_POR_VENTANA);
}
