import type { ReactNode } from "react";

import { BarraDeNavegacion } from "@/componentes/landing/BarraDeNavegacion";
import { CierreFinal, PiePagina } from "@/componentes/landing/CierreYPie";

/**
 * Marco de las páginas públicas fuera de la portada (productos, ayuda, institucionales).
 *
 * Hereda el mundo de la landing: la barra transparente sobre la franja azul noche del hero,
 * el contenido sobre fondo claro y el mismo pie. La franja es más baja que el hero porque
 * aquí el protagonista es el contenido, no una promesa.
 */
export function PaginaPublica({
  titulo,
  bajada,
  children,
  extra,
  cierre = true,
}: {
  titulo: string;
  bajada: string;
  children: ReactNode;
  /** Algo que acompaña al título dentro de la franja: un simulador, cifras, un botón. */
  extra?: ReactNode;
  /** La llamada final dorada a abrir la cuenta. */
  cierre?: boolean;
}) {
  return (
    <>
      <div className="relative overflow-hidden bg-gradient-to-br from-noche via-azul-900 to-azul-700">
        <div
          aria-hidden="true"
          className="pointer-events-none absolute -top-40 right-[-10%] h-[520px] w-[520px] rounded-full bg-azul-500/25 blur-[90px]"
        />
        <BarraDeNavegacion />
        <div className="relative mx-auto grid max-w-[1440px] items-center gap-10 px-6 pt-[128px] pb-14 lg:grid-cols-[minmax(0,1fr)_auto] lg:px-16 lg:pt-[160px] lg:pb-20">
          <div className="max-w-[760px]">
            <h1 className="text-[clamp(2.25rem,5vw,64px)] leading-[1.08] font-bold text-balance text-blanco">
              {titulo}
            </h1>
            <p className="mt-5 max-w-[620px] text-[17px] leading-[1.6] text-texto-bajada">
              {bajada}
            </p>
          </div>
          {extra}
        </div>
      </div>
      <main className="bg-azul-050">{children}</main>
      {cierre && <CierreFinal />}
      <PiePagina />
    </>
  );
}

/** Una sección de contenido con su título, al ancho de lectura de la página. */
export function Seccion({
  titulo,
  bajada,
  children,
  id,
  fondo = "claro",
}: {
  titulo: string;
  bajada?: string;
  children: ReactNode;
  id?: string;
  fondo?: "claro" | "blanco";
}) {
  return (
    <section id={id} className={fondo === "blanco" ? "bg-blanco" : undefined}>
      <div className="mx-auto max-w-[1200px] px-6 py-14 lg:px-16 lg:py-20">
        <h2 className="max-w-[720px] text-[clamp(1.6rem,3vw,36px)] leading-[1.15] font-bold text-azul-900">
          {titulo}
        </h2>
        {bajada && (
          <p className="mt-3 max-w-[640px] text-[16px] leading-[1.6] text-gris-700">{bajada}</p>
        )}
        <div className="mt-9">{children}</div>
      </div>
    </section>
  );
}
