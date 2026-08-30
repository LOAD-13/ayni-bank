import type { Metadata } from "next";

import { CierreFinal, PiePagina } from "@/componentes/landing/CierreYPie";
import { FranjaCifras } from "@/componentes/landing/FranjaCifras";
import { Hero } from "@/componentes/landing/Hero";
import { SeccionApp } from "@/componentes/landing/SeccionApp";
import { SeccionAyni } from "@/componentes/landing/SeccionAyni";
import { SeccionComoFunciona } from "@/componentes/landing/SeccionComoFunciona";
import { SeccionComparativa } from "@/componentes/landing/SeccionComparativa";
import { SeccionCuenta } from "@/componentes/landing/SeccionCuenta";
import { SeccionProductos } from "@/componentes/landing/SeccionProductos";
import { SeccionSeguridad } from "@/componentes/landing/SeccionSeguridad";

export const metadata: Metadata = {
  title: "Banca digital sin comisión de mantenimiento",
  description: "Cuenta de ahorro con devengo diario, sin comisión de mantenimiento y sin agencias.",
};

/**
 * Landing pública.
 *
 * Reconstruida sección por sección a partir del diseño aprobado en pen.dev. El export de
 * la herramienta es un lienzo de 1440 px con posiciones absolutas: sirve como
 * especificación —textos, colores, imágenes y proporciones—, pero no como código, porque
 * un lienzo de posiciones fijas no puede refluir. Las imágenes sí se reutilizan tal cual:
 * viven en `public/pen/`.
 */
export default function Landing() {
  return (
    <>
      <main>
        <Hero />
        <FranjaCifras />
        <SeccionCuenta />
        <SeccionAyni />
        <SeccionApp />
        <SeccionSeguridad />
        <SeccionProductos />
        <SeccionComoFunciona />
        <SeccionComparativa />
        <CierreFinal />
      </main>
      <PiePagina />
    </>
  );
}
