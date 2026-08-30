"use client";

import Link from "next/link";
import { useState } from "react";

import { LogotipoAyni } from "@/componentes/LogotipoAyni";

/**
 * Barra de navegación de la landing.
 *
 * Reproduce el diseño aprobado en pen.dev. Sobre el hero es transparente, porque el
 * degradado del hero hace de fondo.
 *
 * El prototipo no incluye una versión móvil de la barra —es un lienzo de 1440 px—, así
 * que por debajo de `lg` los enlaces se recogen en un menú desplegable. Apilarlos en
 * vertical, que sería la traducción literal, empujaría el titular fuera de la pantalla.
 */
const ENLACES = [
  { texto: "Cuentas", destino: "#cuenta" },
  { texto: "Tarjetas", destino: "#productos" },
  { texto: "Préstamos", destino: "/pendiente" },
  { texto: "Inversiones", destino: "/pendiente" },
  { texto: "Negocios", destino: "/pendiente" },
  { texto: "Ayuda", destino: "/pendiente" },
];

export function BarraDeNavegacion() {
  const [abierto, setAbierto] = useState(false);

  return (
    <header className="absolute inset-x-0 top-0 z-20">
      <nav
        aria-label="Principal"
        className="mx-auto flex h-[88px] max-w-[1440px] items-center justify-between gap-6 px-6 lg:px-16"
      >
        <Link href="/">
          <LogotipoAyni altura={17} />
        </Link>

        <ul className="hidden items-center gap-8 lg:flex">
          {ENLACES.map(({ texto, destino }) => (
            <li key={texto}>
              <Link
                href={destino}
                className="text-[14.5px] font-medium text-texto-nav hover:text-blanco"
              >
                {texto}
              </Link>
            </li>
          ))}
        </ul>

        <div className="flex shrink-0 items-center gap-3">
          <Link
            href="/ingresar"
            className="hidden items-center gap-2 rounded-full border border-blanco/25 px-[18px] py-2.5 text-[14px] font-semibold text-blanco sm:inline-flex hover:bg-blanco/10"
          >
            <CandadoIcono />
            Banca en línea
          </Link>

          <Link
            href="/registro"
            className="inline-flex items-center rounded-full bg-gradient-to-r from-oro to-dorado-500 px-[22px] py-[11px] text-[14px] font-bold text-azul-900"
          >
            Abrir cuenta
          </Link>

          <button
            type="button"
            onClick={() => setAbierto((valor) => !valor)}
            aria-expanded={abierto}
            aria-label={abierto ? "Cerrar menú" : "Abrir menú"}
            className="inline-flex h-11 w-11 items-center justify-center rounded-full border border-blanco/25 text-blanco lg:hidden"
          >
            <span aria-hidden="true" className="text-[18px] leading-none">
              {abierto ? "✕" : "☰"}
            </span>
          </button>
        </div>
      </nav>

      {abierto && (
        <ul className="mx-6 mb-4 flex flex-col gap-1 rounded-lg border border-blanco/15 bg-azul-900/95 p-3 backdrop-blur lg:hidden">
          {ENLACES.map(({ texto, destino }) => (
            <li key={texto}>
              <Link
                href={destino}
                onClick={() => setAbierto(false)}
                className="block rounded-md px-4 py-3 text-body font-medium text-texto-nav hover:bg-blanco/10"
              >
                {texto}
              </Link>
            </li>
          ))}
          <li className="sm:hidden">
            <Link
              href="/ingresar"
              onClick={() => setAbierto(false)}
              className="block rounded-md px-4 py-3 text-body font-semibold text-blanco hover:bg-blanco/10"
            >
              Banca en línea
            </Link>
          </li>
        </ul>
      )}
    </header>
  );
}

function CandadoIcono() {
  return (
    <svg
      viewBox="0 0 14 14"
      aria-hidden="true"
      className="h-3.5 w-3.5 fill-current"
      focusable="false"
    >
      <path d="M7 .6a3.4 3.4 0 0 0-3.4 3.4v1.6h-.3c-.9 0-1.6.7-1.6 1.6v4.2c0 .9.7 1.6 1.6 1.6h7.4c.9 0 1.6-.7 1.6-1.6V7.2c0-.9-.7-1.6-1.6-1.6h-.3V4A3.4 3.4 0 0 0 7 .6Zm0 1.2a2.2 2.2 0 0 1 2.2 2.2v1.6H4.8V4A2.2 2.2 0 0 1 7 1.8Z" />
    </svg>
  );
}
