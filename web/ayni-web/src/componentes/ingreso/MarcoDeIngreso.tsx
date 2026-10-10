import Link from "next/link";
import type { ReactNode } from "react";

import { LogotipoAyni } from "@/componentes/LogotipoAyni";
import { VersionDeLaAplicacion } from "@/componentes/VersionDeLaAplicacion";

import { PanelDeMarca } from "./PanelDeMarca";

/**
 * El marco de las pantallas de acceso: panel de marca a la izquierda y la tarjeta con el
 * formulario a la derecha, igual que «Entra a tu banca». La recuperación de la contraseña
 * (HU-21) vive en el mismo marco para que el cliente sepa que no ha salido del banco.
 */
export function MarcoDeIngreso({ children }: { children: ReactNode }) {
  return (
    <main className="grid min-h-screen grid-cols-1 bg-azul-050 lg:grid-cols-[minmax(420px,44%)_1fr]">
      <PanelDeMarca />

      <div className="flex flex-col items-center justify-center px-5 py-10 sm:px-8">
        <Link href="/" className="mb-8 lg:hidden">
          <LogotipoAyni altura={17} tono="oscuro" />
        </Link>

        <div className="w-full max-w-[468px] rounded-[20px] border border-azul-200 bg-blanco p-7 sm:p-9">
          {children}
        </div>

        <VersionDeLaAplicacion />
      </div>
    </main>
  );
}
