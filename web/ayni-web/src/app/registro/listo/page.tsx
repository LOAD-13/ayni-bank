import type { Metadata } from "next";
import Link from "next/link";

import { LogotipoAyni } from "@/componentes/LogotipoAyni";
import { PasosDelOnboarding } from "@/componentes/onboarding/PasosDelOnboarding";

import { EstadoDeLaCuenta } from "./EstadoDeLaCuenta";

export const metadata: Metadata = {
  title: "Tu cuenta está lista · Ayni Bank",
  description: "Tu cuenta de ahorro Ayni ya está abierta.",
  robots: { index: false, follow: false },
};

/**
 * Paso 5 de 5 del onboarding, según el diseño aprobado en pen.dev.
 *
 * **El titular llega por la URL y eso es provisional.** Lo correcto es sacarlo del token de
 * acceso, pero la validación del JWT vive en el gateway y llega con HU-07. Queda anotado
 * aquí, y el endpoint al que llama lo repite: hoy funciona porque el servicio no está
 * publicado fuera de la red interna del compose.
 */
export default async function PaginaDeCuentaLista({
  searchParams,
}: {
  searchParams: Promise<{ titular?: string }>;
}) {
  const { titular } = await searchParams;

  return (
    <div className="min-h-screen bg-azul-050">
      <header className="border-b border-gris-300 bg-blanco">
        <div className="mx-auto flex max-w-[1180px] items-center justify-between gap-4 px-6 py-3.5">
          <Link href="/">
            <LogotipoAyni altura={16} tono="oscuro" />
          </Link>
          <span className="text-[13.5px] text-gris-500">Registro completado</span>
        </div>
      </header>

      <div className="mx-auto w-full max-w-[760px] px-5 pt-9 sm:px-6">
        <PasosDelOnboarding actual={5} completado />
      </div>

      <main className="mx-auto w-full max-w-[680px] px-5 py-9 sm:px-6">
        {titular ? (
          <EstadoDeLaCuenta usuarioId={titular} />
        ) : (
          <div className="rounded-[16px] border border-azul-200 bg-blanco p-8 text-center">
            <h1 className="text-[22px] font-bold text-azul-800">No sabemos de qué cuenta hablas</h1>
            <p className="mt-2 text-[14.5px] text-gris-700">
              Vuelve a entrar desde tu correo de bienvenida o inicia sesión.
            </p>
            <Link
              href="/ingresar"
              className="mt-6 inline-flex min-h-[44px] items-center justify-center rounded-full bg-azul-700 px-7 text-[15px] font-bold text-blanco hover:bg-azul-800"
            >
              Iniciar sesión
            </Link>
          </div>
        )}
      </main>

      <footer className="px-5 pb-10 text-center text-[12px] text-gris-500">
        Ayni Bank · Banca 100 % digital para personas naturales en Perú. Diseñado conforme al marco
        normativo de la SBS.
      </footer>
    </div>
  );
}
