import type { Metadata } from "next";
import Link from "next/link";

import { PaginaPublica, Seccion } from "@/componentes/publico/PaginaPublica";

export const metadata: Metadata = {
  title: "Tarifario",
  description:
    "Tasas, comisiones y límites de la Cuenta Ayni, la tarjeta de débito y las transferencias.",
};

type Fila = [concepto: string, valor: string, nota?: string];

const TABLAS: { titulo: string; filas: Fila[] }[] = [
  {
    titulo: "Cuenta Ayni Soles",
    filas: [
      ["Tasa efectiva anual (TEA)", "4.50 %", "Vigente desde el 17 de agosto de 2026"],
      [
        "Tasa de rendimiento efectivo anual (TREA)",
        "4.50 %",
        "Igual a la TEA porque no hay comisiones",
      ],
      ["Comisión de mantenimiento", "S/ 0.00"],
      ["Monto mínimo de apertura", "S/ 0.00"],
      ["Devengo de intereses", "Diario", "Sobre el saldo de cierre; se abonan a fin de mes"],
    ],
  },
  {
    titulo: "Cuenta Ayni Dólares",
    filas: [
      ["Tasa efectiva anual (TEA)", "1.20 %", "Vigente desde el 17 de agosto de 2026"],
      ["Tasa de rendimiento efectivo anual (TREA)", "1.20 %"],
      ["Comisión de mantenimiento", "US$ 0.00"],
    ],
  },
  {
    titulo: "Transferencias",
    filas: [
      ["Entre cuentas Ayni", "S/ 0.00", "Al instante, de S/ 1.00 a S/ 5 000.00 por operación"],
      ["A otros bancos por CCI", "S/ 0.00", "De S/ 1.00 a S/ 5 000.00 por operación"],
    ],
  },
  {
    titulo: "Tarjeta de débito virtual",
    filas: [
      ["Emisión", "S/ 0.00", "Se emite al abrir la cuenta"],
      ["Membresía", "S/ 0.00"],
      ["Límite diario de consumo", "S/ 500.00", "Lo cambias tú desde la banca"],
      ["Congelar y descongelar", "S/ 0.00", "Inmediato, las veces que quieras"],
    ],
  },
  {
    titulo: "Cambio de moneda",
    filas: [
      [
        "Margen sobre el tipo de cambio de referencia",
        "0.50 %",
        "Mitad a la compra y mitad a la venta",
      ],
      ["Margen con saldo promedio desde S/ 5 000", "0.25 %"],
      ["Tipo de cambio de referencia", "SUNAT / BCRP", "Se guarda en cada operación con su hora"],
    ],
  },
];

export default function Tarifario() {
  return (
    <PaginaPublica
      titulo="Tarifario"
      bajada="Todo lo que cobramos y pagamos, con números exactos. Si no está en esta página, no te lo cobramos."
    >
      {TABLAS.map(({ titulo, filas }, i) => (
        <Seccion key={titulo} titulo={titulo} fondo={i % 2 ? "blanco" : "claro"}>
          <div className="overflow-hidden rounded-[20px] border border-azul-200 bg-blanco">
            <table className="w-full text-left">
              <caption className="sr-only">{titulo}: tasas, comisiones y límites</caption>
              <thead className="bg-azul-800 text-small text-blanco">
                <tr>
                  <th scope="col" className="px-5 py-3 font-semibold sm:px-6">
                    Concepto
                  </th>
                  <th scope="col" className="px-5 py-3 text-right font-semibold sm:px-6">
                    Valor
                  </th>
                </tr>
              </thead>
              <tbody>
                {filas.map(([concepto, valor, nota]) => (
                  <tr key={concepto} className="border-t border-azul-100">
                    <th scope="row" className="px-5 py-4 font-normal sm:px-6">
                      <span className="block text-body text-gris-900">{concepto}</span>
                      {nota && <span className="block text-small text-gris-700">{nota}</span>}
                    </th>
                    <td className="cifra px-5 py-4 text-right text-body whitespace-nowrap text-azul-900 sm:px-6">
                      {valor}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Seccion>
      ))}
      <Seccion titulo="Cómo se calculan los intereses" fondo="blanco">
        <p className="max-w-[70ch] text-body leading-[1.7] text-gris-700">
          Cada día calculamos el interés de tu saldo de cierre con la fórmula{" "}
          <span className="cifra text-gris-900">saldo × ((1 + TEA)^(1/360) − 1)</span>, sin
          redondear hasta el abono. A fin de mes sumamos los días y abonamos el total en tu cuenta.
          Lo ves día por día en tu banca, en «Cuentas». Más detalle en{" "}
          <Link href="/transparencia" className="font-semibold text-azul-600 hover:underline">
            Transparencia
          </Link>
          .
        </p>
      </Seccion>
    </PaginaPublica>
  );
}
