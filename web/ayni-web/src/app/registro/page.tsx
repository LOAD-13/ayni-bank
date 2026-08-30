import { BookmarkCheck } from "lucide-react";
import type { Metadata } from "next";
import Link from "next/link";

import { LogotipoAyni } from "@/componentes/LogotipoAyni";

import { IndicadorDeProgreso } from "@/componentes/IndicadorDeProgreso";

import { FormularioDeRegistro } from "./FormularioDeRegistro";

export const metadata: Metadata = {
  title: "Crea tu cuenta",
  description: "Abre tu cuenta Ayni en minutos, sin acudir a una agencia.",
};

/** HU-01 · paso 1 de 5 del onboarding, según el diseño aprobado en pen.dev. */
export default function Registro() {
  return (
    <div className="min-h-screen bg-azul-050">
      <header className="border-b border-gris-300 bg-blanco">
        <div className="mx-auto flex max-w-[1180px] items-center justify-between gap-4 px-6 py-3.5">
          <Link href="/">
            <LogotipoAyni altura={16} tono="oscuro" />
          </Link>

          <div className="flex items-center gap-4">
            <span className="hidden text-[13.5px] text-gris-500 sm:inline">
              Paso <strong className="font-bold text-azul-700">1</strong> de 5
            </span>
            {/* «Guardar y salir» del diseño. Hasta que exista el guardado parcial del
                expediente, solo sale: un botón que dice guardar y no guarda es peor que
                no tenerlo. */}
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

      <main className="mx-auto flex max-w-[1180px] flex-col items-center gap-9 px-6 py-9">
        <IndicadorDeProgreso pasoActual={1} />

        <div className="w-full max-w-[760px] rounded-[18px] border border-gris-300 bg-blanco p-6 shadow-sm sm:p-9">
          <h1 className="text-[28px] leading-tight font-bold text-azul-700">Crea tu cuenta Ayni</h1>
          <p className="mt-2 mb-7 text-[14.5px] text-gris-700">
            Te toma unos 5 minutos y solo necesitas tu DNI. Sin agencias y sin comisión de
            mantenimiento.
          </p>

          <FormularioDeRegistro />
        </div>

        <p className="max-w-[760px] text-center text-[12.5px] text-gris-500">
          Ayni Bank · Banca 100 % digital para personas naturales en Perú. Diseñado conforme al
          marco normativo de la SBS. Proyecto académico de la Universidad Tecnológica del Perú.
        </p>
      </main>
    </div>
  );
}
