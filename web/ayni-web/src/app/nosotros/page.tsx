import type { Metadata } from "next";
import Link from "next/link";

import { PaginaPublica, Seccion } from "@/componentes/publico/PaginaPublica";

export const metadata: Metadata = {
  title: "Quiénes somos",
  description:
    "Ayni Bank nace del principio andino de reciprocidad: un banco sin comisiones donde el dinero rinde para quien lo ahorra.",
};

const PRINCIPIOS = [
  [
    "Sin letra chica",
    "Si una tasa o una comisión no está en el tarifario, no existe. La TREA que anunciamos es la que ganas.",
  ],
  [
    "Tu dinero, tu control",
    "Congelas tu tarjeta, fijas tus límites y confirmas cada operación tú. Nada se mueve sin ti.",
  ],
  [
    "Hecho para el celular",
    "Abrir la cuenta toma cinco minutos con tu DNI y una selfie. Sin agencias, sin filas, sin papeles.",
  ],
  [
    "Seguro por diseño",
    "Segundo factor en cada transferencia, datos cifrados y una pista de auditoría que nadie puede borrar.",
  ],
] as const;

const EQUIPO = [
  ["Joaquín Loa", "Scrum Master y desarrollo"],
  ["Kiara Santti", "Desarrollo"],
  ["Eduardo Vargas", "Desarrollo"],
  ["Frank Sotomayor", "Desarrollo"],
  ["Fabián García", "Desarrollo"],
  ["Gerardo Socualaya", "Desarrollo"],
] as const;

export default function QuienesSomos() {
  return (
    <PaginaPublica
      titulo="Hoy por ti, mañana por mí."
      bajada="Ayni es el principio andino de reciprocidad: la comunidad se ayuda y lo que das vuelve. Un banco sin comisiones, donde el dinero de la comunidad rinde para la comunidad, es eso mismo."
    >
      <Seccion titulo="Por qué existimos">
        <div className="grid gap-8 lg:grid-cols-2">
          <p className="max-w-[62ch] text-[17px] leading-[1.7] text-gris-700">
            En el Perú, abrir una cuenta todavía exige ir a una agencia en horario bancario, y las
            cuentas de ahorro rinden muy poco mientras la comisión de mantenimiento se come los
            saldos pequeños. Quien más necesita ahorrar es quien menos gana.
          </p>
          <p className="max-w-[62ch] text-[17px] leading-[1.7] text-gris-700">
            Ayni da la vuelta a eso: una cuenta que se abre en minutos desde el navegador, que paga
            intereses todos los días y que no cobra mantenimiento. Sin agencias que sostener, lo que
            ahorramos en costos vuelve a ti en tasa.
          </p>
        </div>
      </Seccion>

      <Seccion titulo="Lo que no negociamos" fondo="blanco">
        <dl className="grid gap-5 sm:grid-cols-2">
          {PRINCIPIOS.map(([titulo, detalle]) => (
            <div key={titulo} className="rounded-[20px] bg-azul-050 p-7">
              <dt className="text-h3 font-bold text-azul-900">{titulo}</dt>
              <dd className="mt-2 text-body leading-[1.6] text-gris-700">{detalle}</dd>
            </div>
          ))}
        </dl>
      </Seccion>

      <Seccion
        titulo="El equipo"
        bajada="Ayni Bank es el proyecto del curso Integrador II: Software de la Universidad Tecnológica del Perú, construido en sprints de dos semanas con Scrum."
      >
        <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {EQUIPO.map(([nombre, rol]) => (
            <li
              key={nombre}
              className="flex items-center gap-4 rounded-[20px] border border-azul-200 bg-blanco p-5"
            >
              <span
                aria-hidden="true"
                className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-azul-800 text-body font-bold text-oro"
              >
                {nombre
                  .split(" ")
                  .map((p) => p[0])
                  .join("")}
              </span>
              <span>
                <span className="block text-body font-semibold text-gris-900">{nombre}</span>
                <span className="block text-small text-gris-700">{rol}</span>
              </span>
            </li>
          ))}
        </ul>
        <p className="mt-6 text-body text-gris-700">
          Product Owner: Dr. Carlos Tovar, docente del curso. ¿Quieres ver cómo trabajamos?{" "}
          <Link
            href="/trabaja-con-nosotros"
            className="font-semibold text-azul-600 hover:underline"
          >
            Te lo contamos aquí
          </Link>
          .
        </p>
      </Seccion>
    </PaginaPublica>
  );
}
