"use client";

import {
  ArrowLeft,
  BookmarkCheck,
  Info,
  RefreshCw,
  ShieldCheck,
  TriangleAlert,
} from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, type ClipboardEvent, type FormEvent } from "react";

import { IndicadorDeProgreso, PASOS_DEL_ONBOARDING } from "@/componentes/IndicadorDeProgreso";
import { InscripcionDelSegundoFactor } from "@/componentes/ingreso/InscripcionDelSegundoFactor";
import { SelectorMetodoSegundoFactor } from "@/componentes/ingreso/SelectorMetodoSegundoFactor";
import { LogotipoAyni } from "@/componentes/LogotipoAyni";
import {
  ErrorDeApi,
  reenviarCodigoRegistro,
  seleccionarMetodoSegundoFactor,
  verificarCodigoRegistro,
  type MetodoSegundoFactor,
  type TipoDeSegundoFactor,
} from "@/lib/api";

const DIGITOS = 6;
const SEGUNDOS_CORREO = 600;
const SEGUNDOS_TOTP = 30;

interface Props {
  solicitudId?: string;
}

/**
 * HU-22 (AYNI-124) · Paso 2 de 6 del onboarding: elección del segundo factor y
 * verificación del contacto antes de permitir la captura de documentos KYC.
 */
export function PasoDeSegundoFactorRegistro({ solicitudId }: Props) {
  const router = useRouter();

  const [metodoSeleccionado, setMetodoSeleccionado] =
    useState<TipoDeSegundoFactor>("APP_AUTENTICADORA");
  const [metodoInscrito, setMetodoInscrito] = useState<MetodoSegundoFactor | null>(null);
  const [digitos, setDigitos] = useState<string[]>(Array(DIGITOS).fill(""));
  const casillas = useRef<(HTMLInputElement | null)[]>([]);
  const [restantes, setRestantes] = useState<number>(SEGUNDOS_TOTP);

  const [cargandoMetodo, setCargandoMetodo] = useState(false);
  const [verificando, setVerificando] = useState(false);
  const [reenviando, setReenviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [mensajeReenvio, setMensajeReenvio] = useState<string | null>(null);

  useEffect(() => {
    if (!metodoInscrito) return;
    const temporizador = setInterval(() => {
      setRestantes((prev) => {
        if (metodoInscrito.tipo === "APP_AUTENTICADORA") {
          return SEGUNDOS_TOTP - (Math.floor(Date.now() / 1000) % SEGUNDOS_TOTP);
        }
        return Math.max(0, prev - 1);
      });
    }, 1000);
    return () => clearInterval(temporizador);
  }, [metodoInscrito]);

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

  async function confirmarSeleccion() {
    if (!solicitudId) return;
    setCargandoMetodo(true);
    setError(null);
    setMensajeReenvio(null);
    try {
      const respuesta = await seleccionarMetodoSegundoFactor(solicitudId, metodoSeleccionado);
      setMetodoInscrito(respuesta);
      setDigitos(Array(DIGITOS).fill(""));
      setRestantes(
        respuesta.tipo === "CORREO_ELECTRONICO"
          ? SEGUNDOS_CORREO
          : SEGUNDOS_TOTP - (Math.floor(Date.now() / 1000) % SEGUNDOS_TOTP),
      );
    } catch (fallo) {
      if (fallo instanceof ErrorDeApi) {
        setError(fallo.problema.detail ?? fallo.message);
      } else {
        setError("No pudimos registrar tu elección de segundo factor. Inténtalo de nuevo.");
      }
    } finally {
      setCargandoMetodo(false);
    }
  }

  async function manejarReenvio() {
    if (!solicitudId || !metodoInscrito || reenviando) return;
    setReenviando(true);
    setError(null);
    setMensajeReenvio(null);
    try {
      await reenviarCodigoRegistro(solicitudId, metodoInscrito.tipo);
      setRestantes(SEGUNDOS_CORREO);
      setMensajeReenvio("Enviamos un nuevo código de 6 dígitos a tu correo.");
    } catch (fallo) {
      if (fallo instanceof ErrorDeApi) {
        setError(fallo.problema.detail ?? fallo.message);
      } else {
        setError("No se pudo reenviar el código. Inténtalo de nuevo.");
      }
    } finally {
      setReenviando(false);
    }
  }

  async function alVerificar(evento: FormEvent) {
    evento.preventDefault();
    if (!solicitudId || !metodoInscrito || codigo.length < DIGITOS) return;

    setVerificando(true);
    setError(null);
    try {
      await verificarCodigoRegistro(solicitudId, metodoInscrito.tipo, codigo);
      router.push(`/registro/dni-anverso?solicitudId=${solicitudId}`);
    } catch (fallo) {
      if (fallo instanceof ErrorDeApi) {
        setError(fallo.problema.detail ?? fallo.message);
      } else {
        setError("No pudimos verificar el código. Inténtalo de nuevo.");
      }
    } finally {
      setVerificando(false);
    }
  }

  const uriQr =
    metodoInscrito?.uriDeAprovisionamiento ??
    (metodoInscrito?.secreto
      ? `otpauth://totp/Ayni%20Bank?secret=${metodoInscrito.secreto}&issuer=Ayni%20Bank&digits=6&period=30`
      : null);

  return (
    <div className="min-h-screen bg-azul-050">
      <header className="border-b border-gris-300 bg-blanco">
        <div className="mx-auto flex max-w-[1440px] items-center justify-between gap-4 px-6 py-5 lg:px-16">
          <Link href="/">
            <LogotipoAyni altura={16} tono="oscuro" />
          </Link>
          <div className="flex items-center gap-4">
            <span className="hidden text-[13.5px] text-gris-500 sm:inline">
              Paso <strong className="font-bold text-azul-700">2</strong> de{" "}
              {PASOS_DEL_ONBOARDING.length}
            </span>
            <Link
              href="/"
              className="inline-flex items-center gap-2 rounded-full border border-gris-300 px-4 py-2 text-[13.5px] font-medium text-gris-700 hover:bg-gris-100"
            >
              <BookmarkCheck aria-hidden="true" className="h-4 w-4" />
              Guardar y salir
            </Link>
          </div>
        </div>
      </header>

      <main className="mx-auto flex max-w-[1440px] flex-col items-center gap-6 px-3 py-6 sm:gap-9 sm:px-6 sm:py-9 lg:px-16">
        <IndicadorDeProgreso pasoActual={2} />

        <div className="w-full max-w-[760px] rounded-[20px] border border-gris-300 bg-blanco p-6 shadow-sm sm:p-10">
          {!solicitudId ? (
            <div className="text-center">
              <h1 className="text-[22px] font-bold text-azul-800">
                No sabemos de qué solicitud hablas
              </h1>
              <p className="mt-2 text-[14.5px] text-gris-700">
                Vuelve a entrar desde el enlace de tu correo, o empieza de nuevo tu registro.
              </p>
              <Link
                href="/registro"
                className="mt-6 inline-flex min-h-[44px] items-center justify-center rounded-full bg-azul-700 px-7 text-[15px] font-bold text-blanco hover:bg-azul-800"
              >
                Ir al registro
              </Link>
            </div>
          ) : !metodoInscrito ? (
            <div>
              <span
                aria-hidden="true"
                className="flex h-12 w-12 items-center justify-center rounded-[14px] bg-azul-100 text-azul-700"
              >
                <ShieldCheck className="h-6 w-6" />
              </span>

              <h1 className="mt-5 text-[28px] leading-tight font-bold text-azul-700">
                Protege tu cuenta con un segundo factor
              </h1>
              <p className="mt-2 text-[14.5px] leading-[1.55] text-gris-700">
                Antes de fotografiar tu DNI, elige cómo quieres confirmar tus ingresos y operaciones
                en Ayni Bank.
              </p>

              {error && (
                <p
                  role="alert"
                  className="mt-5 flex items-start gap-2.5 rounded-[12px] border border-error bg-blanco p-4 text-[13.5px] text-error"
                >
                  <TriangleAlert aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0" />
                  {error}
                </p>
              )}

              <SelectorMetodoSegundoFactor
                metodoSeleccionado={metodoSeleccionado}
                onSeleccionarMetodo={setMetodoSeleccionado}
                deshabilitado={cargandoMetodo}
              />

              <div className="mt-7 flex justify-end">
                <button
                  type="button"
                  disabled={cargandoMetodo}
                  aria-busy={cargandoMetodo}
                  onClick={confirmarSeleccion}
                  className="inline-flex min-h-[48px] w-full items-center justify-center gap-2 rounded-full bg-azul-700 px-7 text-[15px] font-bold text-blanco hover:bg-azul-800 disabled:opacity-60 sm:w-auto"
                >
                  {cargandoMetodo ? "Configurando…" : "Continuar con este método"}
                  {!cargandoMetodo && <span aria-hidden="true">→</span>}
                </button>
              </div>
            </div>
          ) : (
            <div>
              <button
                type="button"
                onClick={() => {
                  setMetodoInscrito(null);
                  setError(null);
                  setMensajeReenvio(null);
                }}
                className="flex w-fit items-center gap-2 text-[14px] font-semibold text-azul-600 hover:underline"
              >
                <ArrowLeft aria-hidden="true" className="h-4 w-4" />
                Elegir otro método
              </button>

              <h1 className="mt-5 text-[28px] leading-tight font-bold text-azul-700">
                {metodoInscrito.tipo === "APP_AUTENTICADORA"
                  ? "Vincula tu app autenticadora"
                  : "Verifica tu correo electrónico"}
              </h1>

              {metodoInscrito.tipo === "APP_AUTENTICADORA" && uriQr ? (
                <InscripcionDelSegundoFactor uri={uriQr} />
              ) : (
                <p className="mt-2 text-[14.5px] leading-[1.55] text-gris-700">
                  Enviamos un código de 6 dígitos a tu correo electrónico registrado. Escríbelo a
                  continuación para confirmar tu medio de contacto.
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
                <p role="status" className="mt-4 text-[13.5px] font-semibold text-verde-700">
                  {mensajeReenvio}
                </p>
              )}

              <form onSubmit={alVerificar} noValidate className="mt-6">
                <fieldset>
                  <legend className="text-[13.5px] font-semibold text-gris-700">
                    Código de verificación de 6 dígitos
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
                      {String(Math.floor(restantes / 60)).padStart(2, "0")}:
                      {String(restantes % 60).padStart(2, "0")}
                    </span>
                  </p>
                </div>

                {metodoInscrito.tipo === "CORREO_ELECTRONICO" && (
                  <div className="mt-3 flex justify-end">
                    <button
                      type="button"
                      disabled={reenviando}
                      onClick={manejarReenvio}
                      className="flex items-center gap-1.5 text-[13.5px] font-semibold text-azul-600 hover:underline disabled:opacity-50"
                    >
                      <RefreshCw className={`h-3.5 w-3.5 ${reenviando ? "animate-spin" : ""}`} />
                      {reenviando ? "Reenviando…" : "Reenviar código"}
                    </button>
                  </div>
                )}

                <button
                  type="submit"
                  disabled={verificando || codigo.length < DIGITOS}
                  aria-busy={verificando}
                  className="mt-6 inline-flex min-h-[48px] w-full items-center justify-center rounded-full bg-azul-700 px-7 text-[15px] font-bold text-blanco hover:bg-azul-800 disabled:opacity-60"
                >
                  {verificando ? "Verificando…" : "Verificar y continuar con mi DNI"}
                </button>
              </form>

              <p className="mt-6 flex items-start gap-2.5 rounded-[12px] bg-gris-100 p-4 text-[13px] leading-[1.5] text-gris-700">
                <Info aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0 text-gris-500" />
                Nadie de Ayni Bank te pedirá este código por teléfono, correo ni mensaje. Si te lo
                piden, no lo compartas.
              </p>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
