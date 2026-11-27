import type { Metadata } from "next";
import Link from "next/link";

import { PaginaPublica, Seccion } from "@/componentes/publico/PaginaPublica";

export const metadata: Metadata = {
  title: "Transparencia",
  description:
    "Qué significan la TEA y la TREA, cómo calculamos tus intereses y cuáles son tus derechos como usuario.",
};

const DERECHOS = [
  [
    "Información",
    "Conocer antes de contratar las tasas, comisiones y condiciones, y recibirlas por escrito cuando cambien.",
  ],
  [
    "Reclamo",
    "Presentar un reclamo o una queja en el Libro de reclamaciones y recibir respuesta en un máximo de 15 días hábiles.",
  ],
  [
    "Datos personales",
    "Acceder, rectificar, cancelar u oponerte al uso de tus datos (derechos ARCO de la Ley N.º 29733).",
  ],
  [
    "Seguridad",
    "Que tu dinero no se mueva sin tu confirmación: cada transferencia pide tu segundo factor.",
  ],
] as const;

export default function Transparencia() {
  return (
    <PaginaPublica
      titulo="Transparencia"
      bajada="Lo que anunciamos es lo que ganas. Aquí explicamos cada tasa, cómo calculamos tus intereses y qué derechos tienes."
    >
      <Seccion titulo="TEA y TREA, sin letra chica">
        <div className="grid gap-5 md:grid-cols-2">
          <div className="rounded-[20px] border border-azul-200 bg-blanco p-7">
            <h3 className="text-h3 font-bold text-azul-900">TEA · Tasa efectiva anual</h3>
            <p className="mt-2 text-body leading-[1.6] text-gris-700">
              Lo que tu dinero gana en un año, con los intereses capitalizados. En la Cuenta Ayni
              Soles es 4.50 %.
            </p>
          </div>
          <div className="rounded-[20px] border border-azul-200 bg-blanco p-7">
            <h3 className="text-h3 font-bold text-azul-900">
              TREA · Tasa de rendimiento efectivo anual
            </h3>
            <p className="mt-2 text-body leading-[1.6] text-gris-700">
              Lo que de verdad ganas después de comisiones y gastos. Como no cobramos ninguno, en
              Ayni la TREA es igual a la TEA: 4.50 %. Es el número que debes comparar entre bancos.
            </p>
          </div>
        </div>
      </Seccion>

      <Seccion
        titulo="Un ejemplo con números"
        bajada="Con S/ 1 000.00 durante 30 días en la Cuenta Ayni Soles, al 4.50 % TEA."
        fondo="blanco"
      >
        <ol className="grid gap-4 md:grid-cols-3">
          {[
            ["Interés de un día", "S/ 1 000.00 × ((1 + 0.045)^(1/360) − 1)", "S/ 0.12227660"],
            ["30 días con el mismo saldo", "S/ 0.12227660 × 30", "S/ 3.67"],
            ["Comisiones descontadas", "Mantenimiento y gastos", "S/ 0.00"],
          ].map(([paso, calculo, resultado]) => (
            <li key={paso} className="rounded-[20px] bg-azul-050 p-6">
              <p className="text-small font-semibold text-gris-700">{paso}</p>
              <p className="cifra mt-2 text-small break-words text-gris-700">{calculo}</p>
              <p className="cifra mt-3 text-h2 text-azul-900">{resultado}</p>
            </li>
          ))}
        </ol>
        <p className="mt-6 max-w-[70ch] text-small leading-[1.6] text-gris-700">
          Usamos el año comercial de 360 días, como el sistema financiero peruano. El interés de
          cada día se guarda con ocho decimales y se redondea una sola vez, al abonarlo a fin de
          mes. Todas las tasas y comisiones están en el{" "}
          <Link href="/tarifario" className="font-semibold text-azul-600 hover:underline">
            Tarifario
          </Link>
          .
        </p>
      </Seccion>

      <Seccion titulo="Tus derechos como usuario">
        <dl className="grid gap-5 md:grid-cols-2">
          {DERECHOS.map(([derecho, detalle]) => (
            <div key={derecho} className="rounded-[20px] border border-azul-200 bg-blanco p-6">
              <dt className="text-body font-bold text-azul-900">{derecho}</dt>
              <dd className="mt-2 text-body leading-[1.6] text-gris-700">{detalle}</dd>
            </div>
          ))}
        </dl>
      </Seccion>

      <Seccion titulo="Quién está detrás" fondo="blanco">
        <p className="max-w-[70ch] text-body leading-[1.7] text-gris-700">
          Ayni Bank es un proyecto académico del curso Integrador II de la Universidad Tecnológica
          del Perú, diseñado conforme al marco normativo de la SBS. No es una entidad financiera
          autorizada ni capta depósitos del público: el dinero que ves en la banca es de prueba.
          Conoce al equipo en{" "}
          <Link href="/nosotros" className="font-semibold text-azul-600 hover:underline">
            Quiénes somos
          </Link>
          .
        </p>
      </Seccion>
    </PaginaPublica>
  );
}
