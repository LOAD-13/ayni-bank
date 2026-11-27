"use client";

import { useId, useState, type ReactNode } from "react";

import { formatearImporte } from "@/lib/formato";
import {
  aTexto,
  cobrosConQr,
  cuotaMensual,
  factura,
  interesDeDeposito,
  proyeccionDeInversion,
} from "@/lib/simulacion";

const soles = (valor: number) => formatearImporte(aTexto(valor));
const tasa = (valor: number) => `${valor.toFixed(2)} %`;

/**
 * Los simuladores de los productos de la landing. Son referenciales: calculan con tasas de
 * ejemplo para que la persona entienda el producto, y así lo dicen en cada uno.
 */
function Panel({
  titulo,
  children,
  resultado,
}: {
  titulo: string;
  children: ReactNode;
  resultado: ReactNode;
}) {
  return (
    <section
      aria-label={titulo}
      className="w-full rounded-[24px] bg-blanco p-6 shadow-[0_24px_60px_-24px_rgb(1_21_38/0.6)] sm:p-7 lg:w-[420px]"
    >
      <h2 className="text-h3 font-bold text-azul-900">{titulo}</h2>
      <div className="mt-5 flex flex-col gap-5">{children}</div>
      <div aria-live="polite" className="mt-6 rounded-[16px] bg-azul-050 p-5">
        {resultado}
      </div>
      <p className="mt-4 text-caption leading-[1.5] text-gris-700">
        Cálculo con tasas referenciales, solo para simular. No constituye una oferta.
      </p>
    </section>
  );
}

function Deslizador({
  etiqueta,
  valor,
  min,
  max,
  paso,
  mostrar,
  cambiar,
}: {
  etiqueta: string;
  valor: number;
  min: number;
  max: number;
  paso: number;
  mostrar: (v: number) => string;
  cambiar: (v: number) => void;
}) {
  const id = useId();
  return (
    <div>
      <div className="flex items-baseline justify-between gap-3">
        <label htmlFor={id} className="text-small font-medium text-gris-700">
          {etiqueta}
        </label>
        <span className="cifra text-body text-azul-900">{mostrar(valor)}</span>
      </div>
      <input
        id={id}
        type="range"
        min={min}
        max={max}
        step={paso}
        value={valor}
        aria-valuetext={mostrar(valor)}
        onChange={(e) => cambiar(Number(e.target.value))}
        className="mt-2 h-11 w-full cursor-pointer accent-azul-700"
      />
    </div>
  );
}

function Elegir<T extends string | number>({
  etiqueta,
  valor,
  opciones,
  cambiar,
}: {
  etiqueta: string;
  valor: T;
  opciones: [T, string][];
  cambiar: (v: T) => void;
}) {
  return (
    <div role="group" aria-label={etiqueta}>
      <p className="text-small font-medium text-gris-700">{etiqueta}</p>
      <div className="mt-2 flex flex-wrap gap-2">
        {opciones.map(([v, rotulo]) => (
          <button
            key={String(v)}
            type="button"
            aria-pressed={valor === v}
            onClick={() => cambiar(v)}
            className={[
              "min-h-[44px] rounded-full border px-4 text-small font-semibold",
              valor === v
                ? "border-azul-700 bg-azul-700 text-blanco"
                : "border-gris-300 text-gris-700 hover:border-azul-400",
            ].join(" ")}
          >
            {rotulo}
          </button>
        ))}
      </div>
    </div>
  );
}

function Cifra({
  etiqueta,
  valor,
  grande = false,
}: {
  etiqueta: string;
  valor: string;
  grande?: boolean;
}) {
  return (
    <div className="flex items-baseline justify-between gap-4">
      <span className="text-small text-gris-700">{etiqueta}</span>
      <span className={`cifra text-azul-900 ${grande ? "text-h2" : "text-body"}`}>{valor}</span>
    </div>
  );
}

export function SimuladorDeCuota({
  titulo,
  tea,
  montoMin,
  montoMax,
  montoInicial,
  plazos,
  etiquetaMonto = "Monto",
}: {
  titulo: string;
  tea: number;
  montoMin: number;
  montoMax: number;
  montoInicial: number;
  plazos: number[];
  etiquetaMonto?: string;
}) {
  const [monto, setMonto] = useState(montoInicial);
  const [meses, setMeses] = useState(plazos[Math.floor(plazos.length / 2)]);
  const r = cuotaMensual(monto, tea, meses);
  return (
    <Panel
      titulo={titulo}
      resultado={
        <div className="flex flex-col gap-2">
          <Cifra etiqueta={`${meses} cuotas de`} valor={soles(r.cuota)} grande />
          <Cifra etiqueta="Intereses en total" valor={soles(r.intereses)} />
          <Cifra etiqueta="TEA referencial" valor={tasa(tea)} />
        </div>
      }
    >
      <Deslizador
        etiqueta={etiquetaMonto}
        valor={monto}
        min={montoMin}
        max={montoMax}
        paso={montoMin >= 1000 ? 500 : 100}
        mostrar={soles}
        cambiar={setMonto}
      />
      <Elegir
        etiqueta="Plazo en meses"
        valor={meses}
        opciones={plazos.map((p) => [p, String(p)])}
        cambiar={setMeses}
      />
    </Panel>
  );
}

export function SimuladorDeDeposito({ tasas }: { tasas: { dias: number; tea: number }[] }) {
  const [monto, setMonto] = useState(10_000);
  const [dias, setDias] = useState(tasas[2]?.dias ?? tasas[0].dias);
  const tea = tasas.find((t) => t.dias === dias)?.tea ?? tasas[0].tea;
  const r = interesDeDeposito(monto, tea, dias);
  return (
    <Panel
      titulo="Simula tu depósito"
      resultado={
        <div className="flex flex-col gap-2">
          <Cifra etiqueta="Ganarías" valor={soles(r.interes)} grande />
          <Cifra etiqueta="Recibes al vencer" valor={soles(r.total)} />
          <Cifra etiqueta="TEA referencial" valor={tasa(tea)} />
        </div>
      }
    >
      <Deslizador
        etiqueta="Monto"
        valor={monto}
        min={500}
        max={100_000}
        paso={500}
        mostrar={soles}
        cambiar={setMonto}
      />
      <Elegir
        etiqueta="Plazo"
        valor={dias}
        opciones={tasas.map((t) => [t.dias, `${t.dias} días`])}
        cambiar={setDias}
      />
    </Panel>
  );
}

export function SimuladorDeSeguros({
  planes,
}: {
  planes: { nombre: string; prima: number; cubre: string }[];
}) {
  const [elegidos, setElegidos] = useState<string[]>([planes[0].nombre]);
  const total = planes.filter((p) => elegidos.includes(p.nombre)).reduce((s, p) => s + p.prima, 0);
  return (
    <Panel
      titulo="Arma tu protección"
      resultado={
        <div className="flex flex-col gap-2">
          <Cifra etiqueta="Pagarías al mes" valor={soles(total)} grande />
          <Cifra etiqueta="Seguros elegidos" valor={String(elegidos.length)} />
        </div>
      }
    >
      <fieldset className="flex flex-col gap-3">
        <legend className="text-small font-medium text-gris-700">
          Elige lo que quieres proteger
        </legend>
        {planes.map((p) => (
          <label
            key={p.nombre}
            className="flex min-h-[44px] cursor-pointer items-center gap-3 rounded-xl border border-gris-300 p-3 has-[:checked]:border-azul-600 has-[:checked]:bg-azul-050"
          >
            <input
              type="checkbox"
              checked={elegidos.includes(p.nombre)}
              onChange={(e) =>
                setElegidos((lista) =>
                  e.target.checked ? [...lista, p.nombre] : lista.filter((n) => n !== p.nombre),
                )
              }
              className="h-4 w-4 accent-azul-700"
            />
            <span className="flex-1">
              <span className="block text-body font-semibold text-gris-900">{p.nombre}</span>
              <span className="block text-caption text-gris-700">{p.cubre}</span>
            </span>
            <span className="cifra text-small text-azul-900">{soles(p.prima)}</span>
          </label>
        ))}
      </fieldset>
    </Panel>
  );
}

export function SimuladorDeInversion({
  perfiles,
}: {
  perfiles: { nombre: string; rentabilidad: number }[];
}) {
  const [mensual, setMensual] = useState(200);
  const [anios, setAnios] = useState(5);
  const [perfil, setPerfil] = useState(perfiles[1]?.nombre ?? perfiles[0].nombre);
  const rentabilidad = perfiles.find((p) => p.nombre === perfil)?.rentabilidad ?? 0;
  const filas = proyeccionDeInversion(1000, mensual, anios, rentabilidad);
  const final = filas[filas.length - 1];
  const maximo = final.valor;
  return (
    <Panel
      titulo="Proyecta tu inversión"
      resultado={
        <div className="flex flex-col gap-3">
          <Cifra etiqueta={`En ${anios} años tendrías`} valor={soles(final.valor)} grande />
          <Cifra etiqueta="Habrás aportado" valor={soles(final.aportado)} />
          <div aria-hidden="true" className="flex h-16 items-end gap-1">
            {filas.map((f) => (
              <span key={f.anio} className="flex flex-1 items-end gap-0.5">
                <span
                  className="w-full rounded-t bg-azul-300"
                  style={{ height: `${(f.aportado / maximo) * 64}px` }}
                />
                <span
                  className="w-full rounded-t bg-azul-700"
                  style={{ height: `${(f.valor / maximo) * 64}px` }}
                />
              </span>
            ))}
          </div>
        </div>
      }
    >
      <Deslizador
        etiqueta="Aporte mensual (con S/ 1 000 iniciales)"
        valor={mensual}
        min={50}
        max={2000}
        paso={50}
        mostrar={soles}
        cambiar={setMensual}
      />
      <Deslizador
        etiqueta="Años"
        valor={anios}
        min={1}
        max={15}
        paso={1}
        mostrar={(v) => `${v} años`}
        cambiar={setAnios}
      />
      <Elegir
        etiqueta="Perfil"
        valor={perfil}
        opciones={perfiles.map((p) => [p.nombre, `${p.nombre} · ${p.rentabilidad} %`])}
        cambiar={setPerfil}
      />
    </Panel>
  );
}

export function SimuladorDeNegocio({ comisionQr }: { comisionQr: number }) {
  const [ventas, setVentas] = useState(8000);
  const qr = cobrosConQr(ventas, comisionQr);
  const f = factura(ventas / 1.18);
  return (
    <Panel
      titulo="Simula un mes de tu negocio"
      resultado={
        <div className="flex flex-col gap-2">
          <Cifra etiqueta="Te queda después de cobrar" valor={soles(qr.neto)} grande />
          <Cifra etiqueta={`Comisión QR (${tasa(comisionQr)})`} valor={soles(qr.comision)} />
          <Cifra etiqueta="IGV incluido en tus ventas" valor={soles(f.igv)} />
        </div>
      }
    >
      <Deslizador
        etiqueta="Ventas del mes cobradas con QR"
        valor={ventas}
        min={500}
        max={50_000}
        paso={500}
        mostrar={soles}
        cambiar={setVentas}
      />
    </Panel>
  );
}
