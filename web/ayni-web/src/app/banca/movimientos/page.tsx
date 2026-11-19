"use client";

import { RefreshCw, WifiOff } from "lucide-react";
import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";

import { CampoDeTexto } from "@/componentes/CampoDeTexto";
import { ListaDeMovimientos } from "@/componentes/banca/ListaDeMovimientos";
import { ResumenDeMeses } from "@/componentes/banca/ResumenDeMeses";
import { useSesion } from "@/componentes/banca/SesionDeBanca";
import { buscarMovimientos, ErrorDeApi, type Movimiento } from "@/lib/api";
import {
  claveDeMes,
  conceptoLegible,
  categoriaDe,
  nombreDeMes,
  ultimosMeses,
} from "@/lib/movimientos";

const MESES = 6;
const POR_PAGINA = 50;
// shortcut: se leen como mucho 10 páginas (500 movimientos en seis meses); si una cuenta real
// pasa de eso, las gráficas deberían salir de un resumen mensual calculado en el servidor.
const PAGINAS_MAXIMAS = 10;

type Tipo = "todo" | "ABONO" | "CARGO";
type Estado =
  | { tipo: "cargando" }
  | { tipo: "lista"; movimientos: Movimiento[]; incompleta: boolean }
  | { tipo: "sin-cuenta" }
  | { tipo: "error" };

/**
 * Movimientos: el resumen de los últimos seis meses (entradas, salidas y en qué se fue el
 * dinero) y el historial completo con búsqueda y filtros. Elegir un mes en la gráfica filtra
 * también el historial. Cada movimiento lleva a su detalle y a su comprobante.
 */
export default function PaginaDeMovimientos() {
  const { token } = useSesion();
  const meses = useMemo(() => ultimosMeses(new Date(), MESES), []);
  const [estado, setEstado] = useState<Estado>({ tipo: "cargando" });
  const [mes, setMes] = useState(meses[meses.length - 1]);
  const [periodo, setPeriodo] = useState<string>("todo");
  const [tipo, setTipo] = useState<Tipo>("todo");
  const [texto, setTexto] = useState("");

  const cargar = useCallback(async () => {
    setEstado({ tipo: "cargando" });
    try {
      const vigente = await token();
      const todos: Movimiento[] = [];
      let pagina = 0;
      let ultima: Movimiento[];
      do {
        ultima = await buscarMovimientos(vigente, `${meses[0]}-01`, pagina, POR_PAGINA);
        todos.push(...ultima);
        pagina++;
      } while (ultima.length === POR_PAGINA && pagina < PAGINAS_MAXIMAS);
      todos.sort((a, b) => b.registradoEn.localeCompare(a.registradoEn));
      setEstado({
        tipo: "lista",
        movimientos: todos,
        incompleta: ultima.length === POR_PAGINA,
      });
    } catch (fallo) {
      setEstado(
        fallo instanceof ErrorDeApi && fallo.problema.codigo === "SIN_CUENTA"
          ? { tipo: "sin-cuenta" }
          : { tipo: "error" },
      );
    }
  }, [token, meses]);

  useEffect(() => {
    void cargar();
  }, [cargar]);

  const movimientos = estado.tipo === "lista" ? estado.movimientos : [];
  const buscado = texto.trim().toLocaleLowerCase("es");
  const filtrados = movimientos.filter(
    (m) =>
      (periodo === "todo" || claveDeMes(m.registradoEn) === periodo) &&
      (tipo === "todo" || m.tipo === tipo) &&
      (!buscado ||
        `${conceptoLegible(m.concepto)} ${categoriaDe(m.concepto)}`
          .toLocaleLowerCase("es")
          .includes(buscado)),
  );
  const hayFiltros = periodo !== "todo" || tipo !== "todo" || buscado !== "";

  function elegirMes(clave: string) {
    setMes(clave);
    setPeriodo(clave);
  }

  function limpiar() {
    setPeriodo("todo");
    setTipo("todo");
    setTexto("");
  }

  return (
    <div className="flex flex-col gap-6">
      <header>
        <h1 className="text-h1 font-bold text-azul-800">Movimientos</h1>
        <p className="text-body text-gris-700">
          Todo lo que entró y salió de tu Cuenta Ayni Soles en los últimos seis meses.
        </p>
      </header>

      {estado.tipo === "cargando" ? (
        <div role="status" className="flex flex-col gap-6">
          <div className="h-72 animate-pulse rounded-[20px] border border-azul-200 bg-blanco" />
          <div className="h-96 animate-pulse rounded-[20px] border border-azul-200 bg-blanco" />
          <span className="sr-only">Cargando tus movimientos…</span>
        </div>
      ) : estado.tipo === "sin-cuenta" ? (
        <section className="rounded-[20px] border border-azul-200 bg-blanco p-8">
          <h2 className="text-h2 font-bold text-azul-900">Tu cuenta aún no está abierta</h2>
          <p className="mt-2 text-body text-gris-700">
            Cuando termines la apertura verás aquí cada movimiento de tu cuenta.
          </p>
          <Link
            href="/banca"
            className="mt-6 inline-flex min-h-[44px] items-center rounded-full bg-azul-800 px-6 text-body font-semibold text-blanco hover:bg-azul-900"
          >
            Ir al resumen
          </Link>
        </section>
      ) : estado.tipo === "error" ? (
        <section
          role="alert"
          className="flex gap-4 rounded-[20px] border border-dorado-500 bg-dorado-100/60 p-6"
        >
          <span
            aria-hidden="true"
            className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full border border-dorado-500 text-dorado-700"
          >
            <WifiOff className="h-5 w-5" />
          </span>
          <div>
            <p className="text-body font-bold text-dorado-800">No pudimos cargar tus movimientos</p>
            <p className="mt-1 text-small text-gris-900">
              Tu dinero está seguro: lo único que falló es la consulta. Inténtalo de nuevo.
            </p>
            <button
              type="button"
              onClick={() => void cargar()}
              className="mt-4 flex min-h-[44px] items-center gap-2 rounded-full bg-azul-800 px-5 text-small font-semibold text-blanco hover:bg-azul-900"
            >
              <RefreshCw aria-hidden="true" className="h-4 w-4" /> Reintentar
            </button>
          </div>
        </section>
      ) : (
        <>
          <ResumenDeMeses
            movimientos={movimientos}
            meses={meses}
            elegido={mes}
            elegir={elegirMes}
          />

          <section
            aria-labelledby="titulo-historial"
            className="overflow-hidden rounded-[20px] border border-azul-200 bg-blanco"
          >
            <div className="flex flex-col gap-4 px-5 pt-5 pb-4 sm:px-6">
              <div className="flex flex-wrap items-baseline justify-between gap-2">
                <h2 id="titulo-historial" className="text-h3 font-bold text-azul-900">
                  Historial
                </h2>
                <p aria-live="polite" className="text-small text-gris-700">
                  {filtrados.length === 1 ? "1 movimiento" : `${filtrados.length} movimientos`}
                  {periodo !== "todo" && ` en ${nombreDeMes(periodo)}`}
                </p>
              </div>

              <div className="grid grid-cols-1 gap-3 md:grid-cols-[minmax(0,1fr)_200px]">
                <CampoDeTexto
                  etiqueta="Buscar"
                  type="search"
                  value={texto}
                  onChange={(e) => setTexto(e.target.value)}
                  placeholder="Nombre, concepto o categoría"
                  autoComplete="off"
                />
                <div className="flex flex-col gap-1.5">
                  <label htmlFor="periodo" className="text-small font-medium text-gris-700">
                    Periodo
                  </label>
                  <select
                    id="periodo"
                    value={periodo}
                    onChange={(e) => {
                      setPeriodo(e.target.value);
                      if (e.target.value !== "todo") setMes(e.target.value);
                    }}
                    className="min-h-[48px] rounded-xl border border-gris-300 bg-blanco px-3 text-body text-gris-900"
                  >
                    <option value="todo">Últimos seis meses</option>
                    {[...meses].reverse().map((clave) => (
                      <option key={clave} value={clave}>
                        {nombreDeMes(clave).replace(/^./, (l) => l.toUpperCase())}{" "}
                        {clave.slice(0, 4)}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="flex flex-wrap items-center gap-2">
                <div role="group" aria-label="Tipo de movimiento" className="flex gap-2">
                  {(
                    [
                      ["todo", "Todo"],
                      ["ABONO", "Entradas"],
                      ["CARGO", "Salidas"],
                    ] as const
                  ).map(([valor, rotulo]) => (
                    <button
                      key={valor}
                      type="button"
                      aria-pressed={tipo === valor}
                      onClick={() => setTipo(valor)}
                      className={[
                        "min-h-[44px] rounded-full border px-4 text-small font-semibold",
                        tipo === valor
                          ? "border-azul-700 bg-azul-700 text-blanco"
                          : "border-gris-300 bg-blanco text-gris-700 hover:border-azul-400",
                      ].join(" ")}
                    >
                      {rotulo}
                    </button>
                  ))}
                </div>
                {hayFiltros && (
                  <button
                    type="button"
                    onClick={limpiar}
                    className="min-h-[44px] px-2 text-small font-semibold text-azul-600 hover:underline"
                  >
                    Quitar filtros
                  </button>
                )}
              </div>
            </div>

            <ListaDeMovimientos
              movimientos={filtrados}
              vacio={
                movimientos.length === 0
                  ? "Todavía no hay movimientos. Haz un depósito o recibe una transferencia para empezar."
                  : "Ningún movimiento coincide con lo que buscas. Prueba con otra palabra o quita los filtros."
              }
            />
            {estado.incompleta && (
              <p className="border-t border-azul-100 px-6 py-4 text-small text-gris-700">
                Se muestran los {PAGINAS_MAXIMAS * POR_PAGINA} movimientos más recientes.
              </p>
            )}
          </section>
        </>
      )}
    </div>
  );
}
