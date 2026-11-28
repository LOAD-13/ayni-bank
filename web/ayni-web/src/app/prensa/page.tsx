import { Download } from "lucide-react";
import type { Metadata } from "next";
import Image from "next/image";
import Link from "next/link";

import { PaginaPublica, Seccion } from "@/componentes/publico/PaginaPublica";

export const metadata: Metadata = {
  title: "Sala de prensa",
  description: "Novedades de Ayni Bank por versión y el kit de marca para medios.",
};

const NOVEDADES = [
  {
    fecha: "19 de noviembre de 2026",
    titulo: "Movimientos con gráficas y un comprobante nuevo",
    texto:
      "La banca suma un historial con búsqueda, el detalle de cada movimiento y un resumen mensual de lo que entra, sale y se ahorra. El comprobante en PDF estrena diseño y logo.",
  },
  {
    fecha: "29 de octubre de 2026",
    titulo: "Versión 2.0: tarjeta de débito virtual",
    texto:
      "Cada cuenta nueva recibe al instante una tarjeta de débito virtual. Se congela con un toque, tiene límite diario configurable y su número completo solo se muestra tras confirmar con el segundo factor.",
  },
  {
    fecha: "2 de octubre de 2026",
    titulo: "Versión 1.0: la banca por internet abre al público",
    texto:
      "Apertura de cuenta con DNI y selfie, segundo factor a elección, transferencias entre cuentas Ayni sin comisión y el panel con el saldo que rinde cada día, desplegados en la nube.",
  },
  {
    fecha: "24 de agosto de 2026",
    titulo: "Nace Ayni Bank",
    texto:
      "Seis estudiantes de la Universidad Tecnológica del Perú empiezan a construir un banco 100 % digital, sin comisión de mantenimiento, sobre el principio andino de reciprocidad.",
  },
] as const;

const COLORES = [
  ["Azul Ayni", "#064475", "bg-azul-700"],
  ["Dorado Ayni", "#C59E41", "bg-dorado-500"],
  ["Azul noche", "#011526", "bg-noche"],
] as const;

export default function SalaDePrensa() {
  return (
    <PaginaPublica
      titulo="Sala de prensa"
      bajada="Lo que lanzamos, cuándo lo lanzamos y los recursos de marca para contarlo."
      cierre={false}
    >
      <Seccion titulo="Novedades">
        <ol className="flex flex-col gap-6 border-l-2 border-azul-200 pl-6 sm:pl-8">
          {NOVEDADES.map(({ fecha, titulo, texto }) => (
            <li key={titulo} className="relative">
              <span
                aria-hidden="true"
                className="absolute top-2 -left-[31px] h-3 w-3 rounded-full bg-dorado-500 sm:-left-[39px]"
              />
              <p className="text-small font-semibold text-gris-700">
                <time>{fecha}</time>
              </p>
              <h3 className="mt-1 text-h3 font-bold text-azul-900">{titulo}</h3>
              <p className="mt-2 max-w-[70ch] text-body leading-[1.6] text-gris-700">{texto}</p>
            </li>
          ))}
        </ol>
      </Seccion>

      <Seccion titulo="Kit de marca" fondo="blanco">
        <div className="grid gap-6 lg:grid-cols-2">
          <div className="flex flex-col items-center justify-center gap-5 rounded-[20px] bg-azul-050 p-8">
            <Image
              src="/marca/ayni-logo.png"
              alt="Logotipo de Ayni Bank"
              width={640}
              height={449}
              className="h-auto w-[240px]"
            />
            <a
              href="/marca/ayni-logo.png"
              download
              className="inline-flex min-h-[44px] items-center gap-2 rounded-full border border-azul-700 px-6 font-semibold text-azul-700 hover:bg-blanco"
            >
              <Download aria-hidden="true" className="h-4 w-4" /> Descargar logotipo (PNG)
            </a>
          </div>
          <ul className="grid gap-4">
            {COLORES.map(([nombre, hex, clase]) => (
              <li
                key={hex}
                className="flex items-center gap-4 rounded-[20px] border border-azul-200 p-4"
              >
                <span aria-hidden="true" className={`h-14 w-14 shrink-0 rounded-xl ${clase}`} />
                <span>
                  <span className="block text-body font-semibold text-gris-900">{nombre}</span>
                  <span className="cifra block text-small text-gris-700">{hex}</span>
                </span>
              </li>
            ))}
            <li className="rounded-[20px] border border-azul-200 p-4 text-small leading-[1.6] text-gris-700">
              El dorado no se usa para texto sobre fondo claro: no alcanza el contraste mínimo. Para
              texto dorado, usa #8A6D24.
            </li>
          </ul>
        </div>
        <p className="mt-8 text-body text-gris-700">
          ¿Escribes sobre nosotros? Lee primero{" "}
          <Link href="/nosotros" className="font-semibold text-azul-600 hover:underline">
            quiénes somos
          </Link>{" "}
          y el{" "}
          <Link href="/blog" className="font-semibold text-azul-600 hover:underline">
            blog
          </Link>
          .
        </p>
      </Seccion>
    </PaginaPublica>
  );
}
