import type { Metadata } from "next";
import Link from "next/link";

import { LogotipoAyni } from "@/componentes/LogotipoAyni";
import { PanelDeMarca } from "@/componentes/ingreso/PanelDeMarca";

import { FormularioDeIngreso } from "./FormularioDeIngreso";

export const metadata: Metadata = {
  title: "Entra a tu banca · Ayni Bank",
  description: "Inicia sesión en Ayni Bank con tu correo y tu segundo factor.",
  // Una pantalla de ingreso no tiene por qué estar en ningún buscador, y estarlo solo
  // sirve para que aparezca en listados de superficies de ataque.
  robots: { index: false, follow: false },
};

export default function PaginaDeIngreso() {
  return (
    <main className="grid min-h-screen grid-cols-1 bg-azul-050 lg:grid-cols-[minmax(420px,44%)_1fr]">
      <PanelDeMarca />

      <div className="flex flex-col items-center justify-center px-5 py-10 sm:px-8">
        {/* El logotipo solo aparece cuando el panel lateral no está: por encima de `lg`
            estaría dos veces en la misma pantalla. */}
        <Link href="/" className="mb-8 lg:hidden">
          <LogotipoAyni altura={17} tono="oscuro" />
        </Link>

        <div className="w-full max-w-[468px] rounded-[20px] border border-azul-200 bg-blanco p-7 sm:p-9">
          <FormularioDeIngreso />
        </div>
      </div>
    </main>
  );
}
