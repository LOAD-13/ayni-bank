import { BookOpenText, Headset, KeyRound } from "lucide-react";
import type { Metadata } from "next";
import Link from "next/link";

import { PaginaPublica, Seccion } from "@/componentes/publico/PaginaPublica";
import { Preguntas } from "@/componentes/publico/Preguntas";
import { PREGUNTAS_DE_APERTURA, PREGUNTAS_DE_LA_BANCA } from "@/lib/preguntas-frecuentes";

export const metadata: Metadata = {
  title: "Centro de ayuda",
  description:
    "Respuestas sobre la apertura de cuenta, transferencias, intereses y seguridad en Ayni Bank.",
};

const ATAJOS = [
  {
    Icono: KeyRound,
    titulo: "Recuperar mi acceso",
    detalle: "Restablece tu contraseña con tu correo",
    href: "/recuperar",
  },
  {
    Icono: Headset,
    titulo: "Canales de atención",
    detalle: "Dónde y cómo te atendemos",
    href: "/canales",
  },
  {
    Icono: BookOpenText,
    titulo: "Libro de reclamaciones",
    detalle: "Registra un reclamo o una queja",
    href: "/libro-de-reclamaciones",
  },
];

export default function CentroDeAyuda() {
  return (
    <PaginaPublica
      titulo="¿En qué te ayudamos?"
      bajada="Las preguntas que más nos hacen, antes de abrir la cuenta y mientras la usas. Si no encuentras la tuya, te decimos dónde escribirnos."
    >
      <div className="mx-auto grid max-w-[1200px] gap-4 px-6 pt-12 sm:grid-cols-3 lg:px-16">
        {ATAJOS.map(({ Icono, titulo, detalle, href }) => (
          <Link
            key={href}
            href={href}
            className="flex items-center gap-4 rounded-[20px] border border-azul-200 bg-blanco p-5 hover:border-azul-400"
          >
            <span
              aria-hidden="true"
              className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-azul-100 text-azul-700"
            >
              <Icono className="h-5 w-5" />
            </span>
            <span>
              <span className="block text-body font-semibold text-gris-900">{titulo}</span>
              <span className="block text-small text-gris-700">{detalle}</span>
            </span>
          </Link>
        ))}
      </div>
      <Seccion titulo="Antes de abrir tu cuenta">
        <Preguntas preguntas={PREGUNTAS_DE_APERTURA} />
      </Seccion>
      <Seccion titulo="Usando la banca por internet" fondo="blanco">
        <Preguntas preguntas={PREGUNTAS_DE_LA_BANCA} />
      </Seccion>
    </PaginaPublica>
  );
}
