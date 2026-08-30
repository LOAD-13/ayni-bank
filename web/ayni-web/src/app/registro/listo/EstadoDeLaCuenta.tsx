"use client";

import {
  ArrowRight,
  CircleCheck,
  Download,
  Loader2,
  Mail,
  ShieldCheck,
  TrendingUp,
  TriangleAlert,
  Wallet,
} from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";

import {
  consultarCuenta,
  consultarTitular,
  ErrorDeApi,
  type CuentaAbierta,
  type Titular,
} from "@/lib/api";

import { TarjetaDeLaCuenta } from "./TarjetaDeLaCuenta";

/** Cada cuánto se vuelve a preguntar mientras la cuenta se está abriendo. */
const ESPERA_MS = 2000;

/** Cuántas veces antes de dejar de insistir y decir que algo va mal. */
const INTENTOS_MAXIMOS = 15;

/** Capital de referencia del ejemplo de rendimiento que muestra el diseño. */
const CAPITAL_DE_EJEMPLO = 10000;

const LO_QUE_SIGUE = [
  {
    Icono: Mail,
    texto: "Revisa tu correo: te enviamos la confirmación de apertura.",
  },
  {
    Icono: Wallet,
    texto: "Tu tarjeta de débito virtual estará disponible en tu banca.",
  },
  {
    Icono: ShieldCheck,
    texto: "Activa el segundo factor para operar con más seguridad.",
  },
];

/**
 * Paso 5a del onboarding · «Verificación aprobada», según el diseño aprobado en pen.dev.
 *
 * **Por qué esta pantalla sondea en lugar de mostrar el resultado de una vez.** La cuenta no
 * se abre en la misma petición que aprueba la verificación: se abre cuando `core-banking`
 * recibe el evento, y entre una cosa y otra pasan unos segundos. Esa demora es la
 * contrapartida de que los servicios estén desacoplados —la verificación no falla porque
 * `core-banking` esté caído— y lo honesto es enseñarla, no fingir que ya está y que el
 * usuario recargue para encontrarse un error.
 */
export function EstadoDeLaCuenta({ usuarioId }: { usuarioId: string }) {
  const [cuenta, setCuenta] = useState<CuentaAbierta | null>(null);
  const [titular, setTitular] = useState<Titular | null>(null);
  const [intentos, setIntentos] = useState(0);
  const [error, setError] = useState<string | null>(null);

  // El titular se pide una sola vez: existe desde el registro y no cambia mientras se
  // espera. Meterlo en el sondeo repetiría la consulta quince veces para nada.
  useEffect(() => {
    let vigente = true;
    consultarTitular(usuarioId)
      .then((datos) => vigente && setTitular(datos))
      .catch(() => {
        // Sin nombre se saluda igual, solo que sin nombre. No es motivo de error.
      });
    return () => {
      vigente = false;
    };
  }, [usuarioId]);

  useEffect(() => {
    if (cuenta || error || intentos >= INTENTOS_MAXIMOS) return;

    let vigente = true;
    const temporizador = setTimeout(
      async () => {
        try {
          const abierta = await consultarCuenta(usuarioId);
          if (vigente) setCuenta(abierta);
        } catch (fallo) {
          // El 404 es lo esperado mientras el evento viaja: se vuelve a preguntar.
          // Cualquier otro estado sí es un problema y se muestra.
          if (fallo instanceof ErrorDeApi && fallo.estado === 404) {
            if (vigente) setIntentos((n) => n + 1);
          } else if (vigente) {
            setError("No pudimos consultar tu cuenta. Inténtalo en unos minutos.");
          }
        }
      },
      intentos === 0 ? 0 : ESPERA_MS,
    );

    return () => {
      vigente = false;
      clearTimeout(temporizador);
    };
  }, [usuarioId, cuenta, error, intentos]);

  if (error || intentos >= INTENTOS_MAXIMOS) {
    return (
      <div
        role="alert"
        className="rounded-[16px] border border-error bg-blanco p-8 text-center"
      >
        <TriangleAlert aria-hidden="true" className="mx-auto h-9 w-9 text-error" />
        <h1 className="mt-4 text-[22px] font-bold text-azul-800">
          Tu cuenta está tardando más de lo normal
        </h1>
        <p className="mt-2 text-[14.5px] leading-[1.55] text-gris-700">
          {error ??
            "Tu verificación se completó, pero la apertura aún no termina. No hace falta que repitas nada: te avisaremos por correo en cuanto esté."}
        </p>
      </div>
    );
  }

  if (!cuenta) {
    return (
      <div
        role="status"
        aria-live="polite"
        className="rounded-[16px] border border-azul-200 bg-azul-050 p-10 text-center"
      >
        <Loader2 aria-hidden="true" className="mx-auto h-9 w-9 animate-spin text-azul-600" />
        <h1 className="mt-4 text-[22px] font-bold text-azul-800">Abriendo tu cuenta</h1>
        <p className="mt-2 text-[14.5px] text-gris-700">
          Estamos generando tu número de cuenta. Tarda unos segundos.
        </p>
      </div>
    );
  }

  const saludo = titular?.nombreDePila
    ? `Listo, ${titular.nombreDePila}. Tu cuenta ya está abierta.`
    : "Listo. Tu cuenta ya está abierta.";

  return (
    <div className="rounded-[20px] border border-azul-200 bg-blanco p-7 sm:p-9">
      <div className="text-center">
        <span
          aria-hidden="true"
          className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-exito/10"
        >
          <CircleCheck className="h-8 w-8 text-exito" />
        </span>
        <h1 className="mt-5 text-[26px] leading-tight font-bold text-azul-700">{saludo}</h1>
        <p className="mx-auto mt-3 max-w-[420px] text-[14.5px] leading-[1.55] text-gris-700">
          Confirmamos tu identidad y abrimos tu Cuenta Ayni en soles.
          {titular?.correo && <> Te enviamos el detalle a {titular.correo}.</>}
        </p>
      </div>

      <TarjetaDeLaCuenta cuenta={cuenta} />

      {cuenta.trea && (
        <p className="mt-4 flex items-start gap-2.5 rounded-[12px] border border-exito/25 bg-exito/5 p-4">
          <TrendingUp aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0 text-exito" />
          <span className="text-[13.5px] leading-[1.55] text-gris-700">
            <strong className="font-bold text-azul-800">
              Tu saldo empieza a rendir hoy mismo.
            </strong>{" "}
            {cuenta.trea} % TREA en soles con devengo diario. Sobre S/{" "}
            {CAPITAL_DE_EJEMPLO.toLocaleString("es-PE")}, en 12 meses tendrías{" "}
            <span className="cifra">S/ {proyeccionAUnAno(cuenta.trea)}</span>.
          </span>
        </p>
      )}

      <h2 className="mt-8 text-[15px] font-bold text-azul-800">Lo que sigue</h2>
      <ul className="mt-3 flex flex-col gap-3">
        {LO_QUE_SIGUE.map(({ Icono, texto }) => (
          <li key={texto} className="flex items-start gap-3 text-[13.5px] text-gris-700">
            <Icono aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0 text-azul-600" />
            {texto}
          </li>
        ))}
      </ul>

      {/* Proporción 3:2 como en el diseño. Con `flex-1` en los dos, el texto del botón
          principal se partía en tres líneas dentro de la píldora. */}
      <div className="mt-8 flex flex-col gap-3 sm:flex-row">
        <Link
          href="/pendiente"
          className="inline-flex min-h-[48px] items-center justify-center gap-2.5 rounded-full bg-azul-700 px-5 text-[14.5px] font-bold whitespace-nowrap text-blanco sm:flex-[3] hover:bg-azul-800"
        >
          Entrar a mi banca por internet
          <ArrowRight aria-hidden="true" className="h-4 w-4" />
        </Link>
        {/* La constancia en PDF no existe todavia: la genera HU-12. Enlazar a la pagina de
            pendientes es preferible a un boton que no hace nada. */}
        <Link
          href="/pendiente"
          className="inline-flex min-h-[48px] items-center justify-center gap-2.5 rounded-full border border-gris-300 px-5 text-[14.5px] font-semibold whitespace-nowrap text-azul-700 sm:flex-[2] hover:bg-azul-050"
        >
          <Download aria-hidden="true" className="h-4 w-4" />
          Descargar constancia
        </Link>
      </div>
    </div>
  );
}

/**
 * Lo que rendirían diez mil soles en un año con esa TREA.
 *
 * La TREA ya es una tasa **efectiva anual**: incorpora la capitalización, así que aplicarla
 * de nuevo día a día contaría el interés compuesto dos veces y prometería de más. Basta
 * multiplicar una vez, y eso es justamente lo que significa «efectiva».
 */
function proyeccionAUnAno(trea: string): string {
  const tasa = Number.parseFloat(trea) / 100;
  if (Number.isNaN(tasa)) return "—";

  return (CAPITAL_DE_EJEMPLO * (1 + tasa)).toLocaleString("es-PE", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}
