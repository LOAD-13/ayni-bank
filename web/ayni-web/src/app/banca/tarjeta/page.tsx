"use client";

import { CreditCard, Gauge, Globe, Snowflake } from "lucide-react";
import { useEffect, useId, useState, type FormEvent, type ReactNode } from "react";

import { Boton } from "@/componentes/Boton";
import { CampoDeTexto } from "@/componentes/CampoDeTexto";
import { useSesion } from "@/componentes/banca/SesionDeBanca";
import {
  configurarTarjeta,
  congelarTarjeta,
  consultarControlesDeTarjeta,
  descongelarTarjeta,
  ErrorDeApi,
  type ControlesDeTarjeta,
} from "@/lib/api";
import { formatearImporte, normalizarImporte, porcentajeUsado } from "@/lib/formato";

type Accion = "congelamiento" | "internet" | "limite";

/**
 * HU-10 · Controles de la tarjeta de débito virtual (AYNI-252): congelarla con efecto
 * inmediato, habilitar o no las compras por internet y fijar un límite diario de consumo.
 *
 * Lo que se ve es siempre lo que respondió el servidor, no lo que se pidió: cada cambio
 * devuelve los controles vigentes y se pintan esos. Mientras la tarjeta no exista (se emite
 * al abrirse la cuenta, HU-09) la página lo dice en lugar de mostrar controles que no
 * controlan nada.
 */
export default function PaginaDeLaTarjeta() {
  const { token } = useSesion();
  const [controles, setControles] = useState<ControlesDeTarjeta | null>(null);
  const [sinTarjeta, setSinTarjeta] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [enCurso, setEnCurso] = useState<Accion | null>(null);
  const [errorDeLimite, setErrorDeLimite] = useState<string | undefined>();

  useEffect(() => {
    let vigente = true;
    token()
      .then(consultarControlesDeTarjeta)
      .then((respuesta) => vigente && setControles(respuesta))
      .catch((fallo) => {
        if (!vigente) return;
        if (fallo instanceof ErrorDeApi && fallo.estado === 404) setSinTarjeta(true);
        else setError(mensajeDe(fallo));
      });
    return () => {
      vigente = false;
    };
  }, [token]);

  async function cambiar(accion: Accion, ejecutar: (t: string) => Promise<ControlesDeTarjeta>) {
    setEnCurso(accion);
    setError(null);
    setAviso(null);
    try {
      const nuevos = await ejecutar(await token());
      setControles(nuevos);
      setAviso(avisoDe(accion, nuevos));
    } catch (fallo) {
      setError(mensajeDe(fallo));
    } finally {
      setEnCurso(null);
    }
  }

  function guardarLimite(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    if (!controles) return;
    const importe = normalizarImporte(
      String(new FormData(evento.currentTarget).get("limite") ?? ""),
    );
    if (!importe || importe === "0.00") {
      setErrorDeLimite("Escribe un límite mayor que cero, con hasta dos decimales.");
      return;
    }
    setErrorDeLimite(undefined);
    void cambiar("limite", (t) =>
      configurarTarjeta(t, {
        limiteDiario: importe,
        comprasPorInternet: controles.comprasPorInternet,
      }),
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <header>
        <h1 className="text-h1 font-bold text-azul-800">Mi tarjeta</h1>
        <p className="text-body text-gris-700">
          Congélala y fija sus límites al instante, sin llamar a nadie.
        </p>
      </header>

      {/* Siempre en el DOM: una región viva que aparece junto con su texto no se anuncia. */}
      <p
        role="status"
        className={
          aviso
            ? "rounded-lg border border-exito/30 bg-exito/5 p-4 text-body text-gris-900"
            : "sr-only"
        }
      >
        {aviso}
      </p>
      {error && (
        <p
          role="alert"
          className="rounded-lg border border-error bg-blanco p-4 text-body text-error"
        >
          {error}
        </p>
      )}

      {sinTarjeta && (
        <Seccion Icono={CreditCard} titulo="Tu tarjeta aún no está lista">
          <p className="text-body text-gris-700">
            Tu tarjeta de débito virtual se emite al abrirse tu cuenta. Cuando esté lista, aquí
            podrás congelarla y fijar sus límites.
          </p>
        </Seccion>
      )}

      {!controles && !sinTarjeta && !error && (
        <p className="text-body text-gris-700">Cargando tu tarjeta…</p>
      )}

      {controles && (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
          <Seccion Icono={Snowflake} titulo="Estado de la tarjeta">
            <EstadoDeLaTarjeta controles={controles} />
            <div className="mt-4">
              {controles.estado === "CONGELADA" ? (
                <Boton
                  variante="contorno"
                  cargando={enCurso === "congelamiento"}
                  onClick={() => void cambiar("congelamiento", descongelarTarjeta)}
                >
                  Descongelar tarjeta
                </Boton>
              ) : (
                <Boton
                  cargando={enCurso === "congelamiento"}
                  onClick={() => void cambiar("congelamiento", congelarTarjeta)}
                >
                  Congelar tarjeta
                </Boton>
              )}
            </div>
          </Seccion>

          <Seccion Icono={Globe} titulo="Compras por internet">
            <Interruptor
              encendido={controles.comprasPorInternet}
              ocupado={enCurso === "internet"}
              texto="Permitir compras por internet"
              ayuda="Si las desactivas, se rechaza cualquier compra en una tienda en línea."
              onCambiar={(habilitadas) =>
                void cambiar("internet", (t) =>
                  configurarTarjeta(t, {
                    limiteDiario: controles.limiteDiario,
                    comprasPorInternet: habilitadas,
                  }),
                )
              }
            />
          </Seccion>

          <Seccion Icono={Gauge} titulo="Límite diario de consumo" anchoCompleto>
            <LimiteDiario controles={controles} />
            <form
              onSubmit={guardarLimite}
              noValidate
              className="mt-5 flex flex-col gap-4 sm:flex-row sm:items-end"
            >
              <div className="flex-1">
                <CampoDeTexto
                  etiqueta="Nuevo límite diario en soles"
                  name="limite"
                  inputMode="decimal"
                  autoComplete="off"
                  placeholder={controles.limiteDiario}
                  error={errorDeLimite}
                />
              </div>
              <Boton type="submit" cargando={enCurso === "limite"}>
                Guardar límite
              </Boton>
            </form>
          </Seccion>
        </div>
      )}
    </div>
  );
}

function mensajeDe(fallo: unknown): string {
  return fallo instanceof ErrorDeApi
    ? (fallo.problema.detail ?? fallo.message)
    : "No pudimos completar el cambio. Inténtalo de nuevo.";
}

function avisoDe(accion: Accion, controles: ControlesDeTarjeta): string {
  if (accion === "congelamiento") {
    return controles.estado === "CONGELADA"
      ? "Tu tarjeta quedó congelada. Ningún consumo pasará hasta que la descongeles."
      : "Tu tarjeta quedó activa de nuevo.";
  }
  if (accion === "internet") {
    return controles.comprasPorInternet
      ? "Las compras por internet quedaron habilitadas."
      : "Las compras por internet quedaron deshabilitadas.";
  }
  return `Tu límite diario ahora es ${formatearImporte(controles.limiteDiario, controles.moneda)}.`;
}

function EstadoDeLaTarjeta({ controles }: { controles: ControlesDeTarjeta }) {
  const congelada = controles.estado === "CONGELADA";
  return (
    <div className="flex items-center justify-between gap-4">
      <span className="text-body font-semibold text-gris-900">
        Tarjeta <span aria-hidden="true">•••• </span>
        <span className="sr-only">terminada en </span>
        {controles.ultimos4}
      </span>
      <span
        className={`rounded-full px-3 py-1 text-caption font-semibold ${
          congelada ? "bg-azul-100 text-azul-800" : "bg-exito/10 text-exito"
        }`}
      >
        {congelada ? "Congelada" : "Activa"}
      </span>
    </div>
  );
}

function LimiteDiario({ controles }: { controles: ControlesDeTarjeta }) {
  const usado = porcentajeUsado(controles.consumidoHoy, controles.limiteDiario);
  return (
    <div>
      <p className="text-body text-gris-900">
        Hoy llevas <strong>{formatearImporte(controles.consumidoHoy, controles.moneda)}</strong> de{" "}
        <strong>{formatearImporte(controles.limiteDiario, controles.moneda)}</strong>.
      </p>
      {/* La barra repite lo que dice el texto: es decorativa para un lector de pantalla. */}
      <div aria-hidden="true" className="mt-3 h-2 w-full overflow-hidden rounded-full bg-azul-100">
        <div className="h-full rounded-full bg-azul-700" style={{ width: `${usado}%` }} />
      </div>
    </div>
  );
}

/** Un interruptor de verdad: `role="switch"` con su estado, no un checkbox disfrazado. */
function Interruptor({
  encendido,
  ocupado,
  texto,
  ayuda,
  onCambiar,
}: {
  encendido: boolean;
  ocupado: boolean;
  texto: string;
  ayuda: string;
  onCambiar: (encendido: boolean) => void;
}) {
  const id = useId();
  return (
    <div className="flex items-start justify-between gap-4">
      <span>
        <span id={`${id}-texto`} className="block text-body font-semibold text-gris-900">
          {texto}
        </span>
        <span id={`${id}-ayuda`} className="block text-small text-gris-700">
          {ayuda}
        </span>
      </span>
      <button
        type="button"
        role="switch"
        aria-checked={encendido}
        aria-labelledby={`${id}-texto`}
        aria-describedby={`${id}-ayuda`}
        aria-busy={ocupado || undefined}
        disabled={ocupado}
        onClick={() => onCambiar(!encendido)}
        className={`relative inline-flex min-h-[44px] min-w-[64px] shrink-0 items-center rounded-full p-1 transition-colors disabled:opacity-60 ${
          encendido ? "bg-azul-700" : "bg-gris-300"
        }`}
      >
        <span
          aria-hidden="true"
          className={`h-8 w-8 rounded-full bg-blanco shadow transition-transform ${
            encendido ? "translate-x-6" : "translate-x-0"
          }`}
        />
      </button>
    </div>
  );
}

function Seccion({
  Icono,
  titulo,
  anchoCompleto = false,
  children,
}: {
  Icono: typeof CreditCard;
  titulo: string;
  anchoCompleto?: boolean;
  children: ReactNode;
}) {
  return (
    <section
      className={`rounded-[20px] border border-azul-200 bg-blanco p-6 ${anchoCompleto ? "lg:col-span-2" : ""}`}
    >
      <h2 className="mb-4 flex items-center gap-2.5 text-h3 font-bold text-azul-900">
        <Icono aria-hidden="true" className="h-5 w-5 text-azul-600" />
        {titulo}
      </h2>
      {children}
    </section>
  );
}
