"use client";

import {
  ArrowRight,
  CircleCheck,
  Download,
  KeyRound,
  Mail,
  ShieldCheck,
  Wallet,
} from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";

import { consultarTitular, type Titular } from "@/lib/api";

const LO_QUE_SIGUE = [
  {
    Icono: Mail,
    texto: "Revisa tu correo: te enviamos la confirmación de apertura.",
  },
  {
    Icono: KeyRound,
    texto: "Inicia sesión para ver tu número de cuenta, tu CCI y tu saldo.",
  },
  {
    Icono: Wallet,
    texto: "Tu tarjeta de débito virtual estará disponible en tu banca.",
  },
  {
    Icono: ShieldCheck,
    texto: "Cada operación se confirma con el segundo factor que elegiste.",
  },
];

/**
 * Paso final del onboarding · «Verificación aprobada».
 *
 * **No muestra el número de cuenta ni el CCI** (ADR-0038): esos datos solo se consultan con la
 * sesión abierta, porque una consulta sin sesión los devolvería a cualquiera que tuviera el
 * identificador. Aquí se confirma la verificación y se lleva a iniciar sesión, donde la banca
 * enseña la cuenta. La cuenta termina de abrirse en core-banking unos segundos después de
 * aprobarse la verificación; si la persona entra antes, la banca se lo dice.
 */
export function EstadoDeLaCuenta({ usuarioId }: { usuarioId: string }) {
  const [titular, setTitular] = useState<Titular | null>(null);

  useEffect(() => {
    let vigente = true;
    consultarTitular(usuarioId)
      .then((datos) => vigente && setTitular(datos))
      // Sin el nombre la pantalla sigue siendo correcta: solo pierde el saludo.
      .catch(() => {});
    return () => {
      vigente = false;
    };
  }, [usuarioId]);

  const saludo = titular?.nombreDePila
    ? `Listo, ${titular.nombreDePila}. Verificamos tu identidad.`
    : "Listo. Verificamos tu identidad.";

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
        <p className="mx-auto mt-3 max-w-[440px] text-[14.5px] leading-[1.55] text-gris-700">
          Estamos abriendo tu Cuenta Ayni en soles, sin comisión de mantenimiento.
          {titular?.correo && <> Te enviaremos el detalle a {titular.correo}.</>} Por seguridad, tu
          número de cuenta y tu CCI los ves al iniciar sesión.
        </p>
      </div>

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
          href="/ingresar"
          className="inline-flex min-h-[48px] items-center justify-center gap-2.5 rounded-full bg-azul-700 px-5 text-[14.5px] font-bold whitespace-nowrap text-blanco sm:flex-[3] hover:bg-azul-800"
        >
          Iniciar sesión para ver mi cuenta
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
