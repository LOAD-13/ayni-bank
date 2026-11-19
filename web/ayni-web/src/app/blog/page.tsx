import type { Metadata } from "next";
import Link from "next/link";

import { PaginaPublica, Seccion } from "@/componentes/publico/PaginaPublica";
import { ARTICULOS } from "@/lib/articulos";

export const metadata: Metadata = {
  title: "Blog Ayni",
  description:
    "Educación financiera sin rodeos: tasas, intereses y seguridad explicados con números.",
};

export default function Blog() {
  return (
    <PaginaPublica
      titulo="Blog Ayni"
      bajada="Educación financiera sin rodeos: tasas, intereses y seguridad, explicados con números y en simple."
    >
      <Seccion titulo="Últimos artículos">
        <ul className="grid gap-6 lg:grid-cols-3">
          {ARTICULOS.map(({ slug, titulo, resumen, fecha, lectura }) => (
            <li key={slug}>
              <Link
                href={`/blog/${slug}`}
                className="group flex h-full flex-col rounded-[20px] border border-azul-200 bg-blanco p-7 hover:border-azul-400"
              >
                <p className="text-small text-gris-700">
                  <time>{fecha}</time> · {lectura}
                </p>
                <h3 className="mt-3 text-h3 font-bold text-balance text-azul-900 group-hover:text-azul-700">
                  {titulo}
                </h3>
                <p className="mt-3 flex-1 text-body leading-[1.6] text-gris-700">{resumen}</p>
                <span className="mt-5 font-semibold text-azul-600">
                  Leer artículo <span aria-hidden="true">→</span>
                </span>
              </Link>
            </li>
          ))}
        </ul>
      </Seccion>
    </PaginaPublica>
  );
}
