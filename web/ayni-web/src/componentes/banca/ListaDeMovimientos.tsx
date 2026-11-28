import { ArrowDownLeft, ArrowUpRight, ChevronRight } from "lucide-react";
import Link from "next/link";

import type { Movimiento } from "@/lib/api";
import { formatearImporte } from "@/lib/formato";
import { categoriaDe, claveDeDia, conceptoLegible, nombreDeDia } from "@/lib/movimientos";

const HORA = new Intl.DateTimeFormat("es-PE", {
  hour: "2-digit",
  minute: "2-digit",
  // «17:54» y no «05:54 p. m.»: cabe entera junto a la categoría en un celular.
  hourCycle: "h23",
  timeZone: "America/Lima",
});

/**
 * Movimientos agrupados por día, del más reciente al más antiguo. Cada fila lleva a su
 * detalle. Es una lista y no una tabla para que el importe se vea entero también en un
 * celular: la tabla de antes se desplazaba de lado y lo dejaba fuera de la pantalla.
 */
export function ListaDeMovimientos({
  movimientos,
  oculto = false,
  vacio = "Todavía no hay movimientos. Haz un depósito o recibe una transferencia para empezar.",
}: {
  movimientos: Movimiento[];
  oculto?: boolean;
  vacio?: string;
}) {
  if (movimientos.length === 0) {
    return <p className="px-6 py-10 text-center text-body text-gris-700">{vacio}</p>;
  }

  const hoy = new Date();
  const dias = new Map<string, Movimiento[]>();
  const ordenados = [...movimientos].sort((a, b) => b.registradoEn.localeCompare(a.registradoEn));
  for (const m of ordenados) {
    const dia = claveDeDia(m.registradoEn);
    dias.set(dia, [...(dias.get(dia) ?? []), m]);
  }

  return (
    <div>
      {[...dias].map(([dia, delDia]) => (
        <section key={dia} aria-labelledby={`dia-${dia}`}>
          <h3
            id={`dia-${dia}`}
            className="border-t border-azul-100 bg-azul-050 px-5 py-2 text-small font-semibold text-gris-700 sm:px-6"
          >
            {nombreDeDia(dia, hoy)}
          </h3>
          <ul>
            {delDia.map((m) => (
              <FilaDeMovimiento key={`${m.movimientoId}-${m.tipo}`} m={m} oculto={oculto} />
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}

function FilaDeMovimiento({ m, oculto }: { m: Movimiento; oculto: boolean }) {
  const abono = m.tipo === "ABONO";
  const Icono = abono ? ArrowDownLeft : ArrowUpRight;
  const importe = oculto ? "S/ ••••" : formatearImporte(m.importe, m.moneda);
  return (
    <li className="border-t border-azul-100 first:border-t-0">
      <Link
        href={`/banca/movimientos/${m.movimientoId}`}
        className="group flex min-h-[64px] items-center gap-3 px-5 py-3 hover:bg-azul-050 sm:gap-4 sm:px-6"
      >
        <span
          aria-hidden="true"
          className={[
            "flex h-10 w-10 shrink-0 items-center justify-center rounded-full",
            abono ? "bg-exito/10 text-exito" : "bg-azul-100 text-azul-700",
          ].join(" ")}
        >
          <Icono className="h-[18px] w-[18px]" />
        </span>
        <span className="min-w-0 flex-1">
          <span className="line-clamp-2 block text-body font-medium break-words text-gris-900">
            {conceptoLegible(m.concepto)}
          </span>
          <span className="block truncate text-small whitespace-nowrap text-gris-700">
            {categoriaDe(m.concepto)} · {HORA.format(new Date(m.registradoEn))}
          </span>
        </span>
        <span className="shrink-0 text-right whitespace-nowrap">
          <span className={`cifra block text-body ${abono ? "text-exito" : "text-gris-900"}`}>
            <span className="sr-only">{abono ? "Entrada de " : "Salida de "}</span>
            <span aria-hidden="true">{abono ? "+ " : "− "}</span>
            {importe}
          </span>
          {m.saldoPosterior && !oculto && (
            <span className="hidden text-caption text-gris-700 sm:block">
              Saldo {formatearImporte(m.saldoPosterior, m.moneda)}
            </span>
          )}
        </span>
        <ChevronRight
          aria-hidden="true"
          className="hidden h-5 w-5 shrink-0 text-gris-500 group-hover:text-azul-600 sm:block"
        />
      </Link>
    </li>
  );
}
