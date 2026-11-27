import { ArrowLeft } from "lucide-react";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";

import { PaginaPublica } from "@/componentes/publico/PaginaPublica";
import { ARTICULOS } from "@/lib/articulos";

export function generateStaticParams() {
  return ARTICULOS.map(({ slug }) => ({ slug }));
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ slug: string }>;
}): Promise<Metadata> {
  const { slug } = await params;
  const articulo = ARTICULOS.find((a) => a.slug === slug);
  return articulo ? { title: articulo.titulo, description: articulo.resumen } : {};
}

export default async function ArticuloDelBlog({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const articulo = ARTICULOS.find((a) => a.slug === slug);
  if (!articulo) notFound();

  return (
    <PaginaPublica titulo={articulo.titulo} bajada={articulo.resumen}>
      <article className="mx-auto max-w-[760px] px-6 py-14 lg:py-20">
        <p className="text-small text-gris-700">
          Equipo Ayni · <time>{articulo.fecha}</time> · {articulo.lectura}
        </p>
        {articulo.secciones.map(({ subtitulo, parrafos }, i) => (
          <section key={subtitulo ?? i} className="mt-8">
            {subtitulo && <h2 className="mb-3 text-h2 font-bold text-azul-900">{subtitulo}</h2>}
            {parrafos.map((p) => (
              <p key={p.slice(0, 32)} className="mt-4 text-[17px] leading-[1.75] text-gris-900">
                {p}
              </p>
            ))}
          </section>
        ))}
        <Link
          href="/blog"
          className="mt-12 inline-flex min-h-[44px] items-center gap-2 font-semibold text-azul-600 hover:underline"
        >
          <ArrowLeft aria-hidden="true" className="h-4 w-4" /> Todos los artículos
        </Link>
      </article>
    </PaginaPublica>
  );
}
