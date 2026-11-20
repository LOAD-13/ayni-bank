import { CircleCheck } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";

import { PaginaPublica, Seccion } from "@/componentes/publico/PaginaPublica";
import { Preguntas } from "@/componentes/publico/Preguntas";

/**
 * Página de un producto de la landing: el simulador en la franja de arriba, porque es lo que la
 * tarjeta de la portada promete («Simular», «Cotizar»), y debajo beneficios, requisitos y dudas.
 *
 * El botón lleva a abrir la Cuenta Ayni: es la puerta a todo lo demás y lo único que hoy se
 * contrata en línea. Las tasas son referenciales y la nota del pie lo dice sin rodeos.
 */
export function PaginaDeProducto({
  titulo,
  bajada,
  simulador,
  beneficios,
  requisitos,
  preguntas,
  nota,
  children,
}: {
  titulo: string;
  bajada: string;
  simulador: ReactNode;
  beneficios: readonly (readonly [string, string])[];
  requisitos: readonly string[];
  preguntas: readonly { pregunta: string; respuesta: string }[];
  nota: string;
  children?: ReactNode;
}) {
  return (
    <PaginaPublica titulo={titulo} bajada={bajada} extra={simulador}>
      <Seccion titulo="Por qué con Ayni">
        <dl className="grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
          {beneficios.map(([nombre, detalle]) => (
            <div key={nombre} className="rounded-[20px] border border-azul-200 bg-blanco p-6">
              <dt className="text-body font-bold text-azul-900">{nombre}</dt>
              <dd className="mt-2 text-small leading-[1.6] text-gris-700">{detalle}</dd>
            </div>
          ))}
        </dl>
      </Seccion>

      {children}

      <Seccion titulo="Qué necesitas" fondo="blanco">
        <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_360px]">
          <ul className="flex flex-col gap-3">
            {requisitos.map((r) => (
              <li key={r} className="flex gap-3 text-body leading-[1.6] text-gris-900">
                <CircleCheck aria-hidden="true" className="mt-1 h-5 w-5 shrink-0 text-exito" />
                {r}
              </li>
            ))}
          </ul>
          <div className="rounded-[20px] bg-azul-800 p-7 text-blanco">
            <p className="text-h3 font-bold">Todo empieza con tu Cuenta Ayni</p>
            <p className="mt-2 text-small leading-[1.6] text-azul-100">
              Ábrela en cinco minutos con tu DNI. Sin monto mínimo y sin comisión de mantenimiento.
            </p>
            <Link
              href="/registro"
              className="mt-5 inline-flex min-h-[44px] items-center rounded-full bg-gradient-to-r from-oro-claro to-dorado-500 px-6 font-bold text-azul-900"
            >
              Abrir mi cuenta gratis <span aria-hidden="true">&nbsp;→</span>
            </Link>
          </div>
        </div>
      </Seccion>

      <Seccion titulo="Preguntas frecuentes">
        <Preguntas preguntas={preguntas} />
        <p className="mt-8 max-w-[80ch] text-caption leading-[1.6] text-gris-700">{nota}</p>
      </Seccion>
    </PaginaPublica>
  );
}

export const NOTA_REFERENCIAL =
  "Las tasas, primas y montos de esta página son referenciales y sirven solo para simular; no constituyen una oferta ni una aprobación. Ayni Bank es un proyecto académico de la Universidad Tecnológica del Perú y no es una entidad financiera autorizada.";
