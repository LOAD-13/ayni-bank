"use client";

import { ArrowLeft, Info, ShieldCheck, TriangleAlert } from "lucide-react";
import { useEffect, useRef, useState, type ClipboardEvent, type FormEvent } from "react";

import { InscripcionDelSegundoFactor } from "./InscripcionDelSegundoFactor";

const DIGITOS = 6;

/** Duración de una ventana TOTP. El código vive hasta el siguiente múltiplo de 30 s. */
const SEGUNDOS_POR_VENTANA = 30;

interface Props {
  uriDeAprovisionamiento: string | null;
  onEnviar: (codigo: string) => Promise<void>;
  onVolver: () => void;
  enviando: boolean;
  error: string | null;
}

/**
 * Pantalla «Confirma que eres tú», según el diseño aprobado en pen.dev.
 *
 * **Una diferencia deliberada con el prototipo.** El diseño incluye «Reenviar código ·
 * disponible en 00:23» y «Usar otro método de verificación», que vienen de pensar en un
 * OTP por SMS. Aquí el segundo factor es TOTP: el código lo calcula la aplicación del
 * usuario a partir de un secreto compartido y la hora, sin que viaje nada por la red. No
 * hay nada que reenviar, así que ese enlace se omite en lugar de ponerlo y que no haga
 * nada. La cuenta atrás sí se mantiene: marca cuándo caduca el código que está a la vista.
 */
export function PasoDeSegundoFactor({
  uriDeAprovisionamiento,
  onEnviar,
  onVolver,
  enviando,
  error,
}: Props) {
  const [digitos, setDigitos] = useState<string[]>(Array(DIGITOS).fill(""));
  const casillas = useRef<(HTMLInputElement | null)[]>([]);
  const [restantes, setRestantes] = useState(segundosHastaElSiguienteCodigo());

  useEffect(() => {
    const temporizador = setInterval(
      () => setRestantes(segundosHastaElSiguienteCodigo()),
      1000,
    );
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
    // Retroceso sobre una casilla vacía salta a la anterior: sin esto hay que pulsar dos
    // veces para borrar, y con seis casillas eso se nota enseguida.
    if (tecla === "Backspace" && !digitos[indice] && indice > 0) {
      casillas.current[indice - 1]?.focus();
    }
  }

  /** Pegar el código entero reparte los dígitos, que es lo que la gente hace. */
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

  return (
    <>
      {/* `flex` y no `inline-flex` en los dos: siendo ambos de nivel en línea, el escudo
          se colocaba a la derecha de «Volver» en lugar de debajo. */}
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

      {uriDeAprovisionamiento ? (
        <InscripcionDelSegundoFactor uri={uriDeAprovisionamiento} />
      ) : (
        <p className="mt-2 max-w-[400px] text-[14.5px] leading-[1.55] text-gris-700">
          Abre tu app de autenticación y escribe el código de 6 dígitos que aparece para Ayni
          Bank.
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

      <form onSubmit={alEnviar} noValidate className="mt-6">
        <fieldset>
          <legend className="text-[13.5px] font-semibold text-gris-700">
            Código de verificación
          </legend>

          <div className="mt-2.5 flex gap-2.5">
            {digitos.map((digito, indice) => (
              <input
                // El índice es la identidad real de la casilla: son seis posiciones fijas
                // que nunca se reordenan ni cambian de número.
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

        <button
          type="submit"
          disabled={enviando || codigo.length < DIGITOS}
          aria-busy={enviando}
          className="mt-6 inline-flex min-h-[48px] w-full items-center justify-center rounded-full bg-azul-700 px-7 text-[15px] font-bold text-blanco hover:bg-azul-800 disabled:opacity-60"
        >
          {enviando ? "Verificando…" : "Verificar y entrar"}
        </button>
      </form>

      <p className="mt-6 flex items-start gap-2.5 rounded-[12px] bg-gris-100 p-4 text-[13px] leading-[1.5] text-gris-700">
        <Info aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0 text-gris-500" />
        Nadie de Ayni Bank te pedirá este código por teléfono, correo ni mensaje. Si te lo
        piden, no lo compartas.
      </p>
    </>
  );
}

/**
 * Lo que le queda de vida al código visible.
 *
 * Las ventanas TOTP no cuentan desde que abres la pantalla: van ancladas al reloj, en
 * múltiplos de treinta segundos desde 1970. Por eso se calcula contra la hora y no con un
 * contador propio, que se desincronizaría con la aplicación del usuario.
 */
function segundosHastaElSiguienteCodigo(): number {
  return SEGUNDOS_POR_VENTANA - (Math.floor(Date.now() / 1000) % SEGUNDOS_POR_VENTANA);
}
