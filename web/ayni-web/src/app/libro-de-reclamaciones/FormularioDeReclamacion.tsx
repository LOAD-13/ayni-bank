"use client";

import { CircleCheck, Printer } from "lucide-react";
import { useEffect, useId, useRef, useState, type FormEvent, type ReactNode } from "react";

import { Boton } from "@/componentes/Boton";
import { CampoDeTexto } from "@/componentes/CampoDeTexto";
import {
  ErrorDeApi,
  registrarReclamacion,
  type ConstanciaDeReclamacion,
  type HojaDeReclamacion,
} from "@/lib/api";
import { normalizarImporte } from "@/lib/formato";

const VACIA: HojaDeReclamacion = {
  tipo: "RECLAMO",
  nombre: "",
  tipoDocumento: "DNI",
  numeroDocumento: "",
  correo: "",
  telefono: "",
  bien: "SERVICIO",
  descripcionDelBien: "",
  montoReclamado: "",
  detalle: "",
  pedido: "",
};

type Errores = Partial<Record<keyof HojaDeReclamacion, string>>;

/** Las mismas reglas que el servidor, para avisar antes de enviar. El servidor decide igual. */
function validar(h: HojaDeReclamacion): Errores {
  const e: Errores = {};
  if (!h.nombre.trim()) e.nombre = "Escribe tu nombre completo.";
  const documento = h.tipoDocumento === "DNI" ? /^\d{8}$/ : /^\d{9,12}$/;
  if (!documento.test(h.numeroDocumento))
    e.numeroDocumento =
      h.tipoDocumento === "DNI" ? "El DNI tiene 8 dígitos." : "Escribe entre 9 y 12 dígitos.";
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(h.correo))
    e.correo = "Escribe un correo válido: ahí te llega la respuesta.";
  if (h.telefono && !/^\d{9}$/.test(h.telefono)) e.telefono = "El celular tiene 9 dígitos.";
  if (!h.descripcionDelBien.trim()) e.descripcionDelBien = "Indica el producto o servicio.";
  if (h.montoReclamado && !normalizarImporte(h.montoReclamado))
    e.montoReclamado = "Escribe un importe válido, con hasta dos decimales.";
  if (h.detalle.trim().length < 20) e.detalle = "Cuéntanos qué pasó en al menos 20 caracteres.";
  if (!h.pedido.trim()) e.pedido = "Dinos qué solución esperas.";
  return e;
}

const FECHA = new Intl.DateTimeFormat("es-PE", { dateStyle: "long", timeZone: "UTC" });
const FECHA_Y_HORA = new Intl.DateTimeFormat("es-PE", {
  dateStyle: "long",
  timeStyle: "short",
  timeZone: "America/Lima",
});

export function FormularioDeReclamacion() {
  const [hoja, setHoja] = useState<HojaDeReclamacion>(VACIA);
  const [errores, setErrores] = useState<Errores>({});
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [constancia, setConstancia] = useState<ConstanciaDeReclamacion | null>(null);

  const cambiar = (campo: keyof HojaDeReclamacion) => (valor: string) =>
    setHoja((h) => ({ ...h, [campo]: valor }));

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    const encontrados = validar(hoja);
    setErrores(encontrados);
    if (Object.keys(encontrados).length > 0) {
      document.querySelector<HTMLElement>("[aria-invalid=true]")?.focus();
      return;
    }
    setEnviando(true);
    setError(null);
    try {
      setConstancia(
        await registrarReclamacion({
          ...hoja,
          montoReclamado: hoja.montoReclamado ? (normalizarImporte(hoja.montoReclamado) ?? "") : "",
        }),
      );
    } catch (fallo) {
      if (fallo instanceof ErrorDeApi && fallo.estado === 400) {
        setErrores(fallo.porCampo() as Errores);
      }
      setError(
        fallo instanceof ErrorDeApi && fallo.estado === 429
          ? "Recibimos muchas hojas desde tu conexión. Inténtalo en unos minutos."
          : "No pudimos registrar tu hoja. Revisa los datos e inténtalo de nuevo; no se guardó nada.",
      );
    } finally {
      setEnviando(false);
    }
  }

  if (constancia) return <Constancia constancia={constancia} hoja={hoja} />;

  return (
    <form onSubmit={enviar} noValidate className="flex flex-col gap-8">
      <Grupo titulo="1. ¿Qué quieres registrar?">
        <Opciones
          nombre="tipo"
          valor={hoja.tipo}
          cambiar={cambiar("tipo")}
          opciones={[
            ["RECLAMO", "Reclamo", "No estás conforme con un producto o servicio."],
            ["QUEJA", "Queja", "No estás conforme con la atención que recibiste."],
          ]}
        />
      </Grupo>

      <Grupo titulo="2. Tus datos">
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="sm:col-span-2">
            <CampoDeTexto
              etiqueta="Nombre completo"
              autoComplete="name"
              value={hoja.nombre}
              onChange={(e) => cambiar("nombre")(e.target.value)}
              error={errores.nombre}
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <label htmlFor="tipo-documento" className="text-small font-medium text-gris-700">
              Documento
            </label>
            <select
              id="tipo-documento"
              value={hoja.tipoDocumento}
              onChange={(e) => cambiar("tipoDocumento")(e.target.value)}
              className="min-h-[44px] rounded-md border border-gris-300 bg-blanco px-3 text-body text-gris-900"
            >
              <option value="DNI">DNI</option>
              <option value="CE">Carné de extranjería</option>
            </select>
          </div>
          <CampoDeTexto
            etiqueta="Número de documento"
            inputMode="numeric"
            value={hoja.numeroDocumento}
            onChange={(e) => cambiar("numeroDocumento")(e.target.value.replace(/\D/g, ""))}
            error={errores.numeroDocumento}
          />
          <CampoDeTexto
            etiqueta="Correo electrónico"
            type="email"
            autoComplete="email"
            value={hoja.correo}
            onChange={(e) => cambiar("correo")(e.target.value)}
            error={errores.correo}
          />
          <CampoDeTexto
            etiqueta="Celular (opcional)"
            type="tel"
            inputMode="numeric"
            autoComplete="tel-national"
            value={hoja.telefono}
            onChange={(e) => cambiar("telefono")(e.target.value.replace(/\D/g, ""))}
            error={errores.telefono}
          />
        </div>
      </Grupo>

      <Grupo titulo="3. El producto o servicio">
        <Opciones
          nombre="bien"
          valor={hoja.bien}
          cambiar={cambiar("bien")}
          opciones={[
            ["SERVICIO", "Servicio", "Una transferencia, la banca por internet, la atención…"],
            ["PRODUCTO", "Producto", "Tu cuenta o tu tarjeta de débito."],
          ]}
        />
        <div className="mt-4 grid gap-4 sm:grid-cols-[minmax(0,1fr)_220px]">
          <CampoDeTexto
            etiqueta="¿Cuál?"
            placeholder="Por ejemplo: transferencia a otro banco del 18 de noviembre"
            value={hoja.descripcionDelBien}
            onChange={(e) => cambiar("descripcionDelBien")(e.target.value)}
            error={errores.descripcionDelBien}
            maxLength={140}
          />
          <CampoDeTexto
            etiqueta="Monto reclamado (opcional)"
            inputMode="decimal"
            placeholder="S/ 0.00"
            value={hoja.montoReclamado}
            onChange={(e) => cambiar("montoReclamado")(e.target.value)}
            error={errores.montoReclamado}
          />
        </div>
      </Grupo>

      <Grupo titulo="4. Lo que pasó">
        <div className="flex flex-col gap-4">
          <AreaDeTexto
            etiqueta="Detalle"
            ayuda="Qué pasó, cuándo y con qué operación. Mínimo 20 caracteres."
            valor={hoja.detalle}
            cambiar={cambiar("detalle")}
            error={errores.detalle}
            maximo={2000}
          />
          <AreaDeTexto
            etiqueta="Qué solución esperas"
            valor={hoja.pedido}
            cambiar={cambiar("pedido")}
            error={errores.pedido}
            maximo={1000}
          />
        </div>
      </Grupo>

      {error && (
        <p
          role="alert"
          aria-live="assertive"
          className="rounded-lg border border-error bg-blanco p-4 text-body text-error"
        >
          {error}
        </p>
      )}

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <Boton type="submit" cargando={enviando}>
          Registrar mi {hoja.tipo === "RECLAMO" ? "reclamo" : "queja"}
        </Boton>
        <p className="text-small text-gris-700">
          Te responderemos al correo que indicaste en un plazo máximo de 15 días hábiles.
        </p>
      </div>
    </form>
  );
}

function Constancia({
  constancia,
  hoja,
}: {
  constancia: ConstanciaDeReclamacion;
  hoja: HojaDeReclamacion;
}) {
  const titulo = useRef<HTMLHeadingElement>(null);
  useEffect(() => titulo.current?.focus(), []);
  const filas: [string, string][] = [
    ["Código", constancia.codigo],
    ["Tipo", constancia.tipo === "RECLAMO" ? "Reclamo" : "Queja"],
    ["Registrado el", FECHA_Y_HORA.format(new Date(constancia.registradaEn))],
    [
      "Respuesta a más tardar el",
      FECHA.format(new Date(`${constancia.responderAntesDe}T12:00:00Z`)),
    ],
    ["A nombre de", hoja.nombre.trim()],
    ["Te responderemos a", hoja.correo.trim()],
  ];
  return (
    <section
      aria-labelledby="titulo-constancia"
      className="overflow-hidden rounded-[20px] border border-azul-200 bg-blanco"
    >
      <div className="flex flex-col items-center gap-3 border-b border-dashed border-azul-200 bg-azul-050 px-6 py-8 text-center">
        <CircleCheck aria-hidden="true" className="h-10 w-10 text-exito" />
        <h2
          id="titulo-constancia"
          ref={titulo}
          tabIndex={-1}
          className="text-h2 font-bold text-azul-900 outline-none"
        >
          Registramos tu {constancia.tipo === "RECLAMO" ? "reclamo" : "queja"}
        </h2>
        <p className="cifra text-h1 text-azul-800">{constancia.codigo}</p>
        <p className="max-w-[52ch] text-body text-gris-700">
          Guarda este código: identifica tu hoja del libro de reclamaciones. Imprime o guarda esta
          constancia en PDF.
        </p>
      </div>
      <dl className="divide-y divide-azul-100 px-6 sm:px-8">
        {filas.map(([clave, valor]) => (
          <div key={clave} className="flex justify-between gap-6 py-3">
            <dt className="shrink-0 text-small text-gris-700">{clave}</dt>
            <dd className="text-right text-small font-medium break-all text-gris-900">{valor}</dd>
          </div>
        ))}
      </dl>
      <div className="border-t border-azul-100 px-6 py-6 sm:px-8 print:hidden">
        <Boton type="button" variante="contorno" onClick={() => window.print()}>
          <Printer aria-hidden="true" className="h-4 w-4" /> Imprimir o guardar constancia
        </Boton>
      </div>
    </section>
  );
}

function Grupo({ titulo, children }: { titulo: string; children: ReactNode }) {
  return (
    <fieldset className="rounded-[20px] border border-azul-200 bg-blanco p-6 sm:p-7">
      <legend className="px-2 text-h3 font-bold text-azul-900">{titulo}</legend>
      <div className="mt-2">{children}</div>
    </fieldset>
  );
}

function Opciones({
  nombre,
  valor,
  cambiar,
  opciones,
}: {
  nombre: string;
  valor: string;
  cambiar: (v: string) => void;
  opciones: [string, string, string][];
}) {
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      {opciones.map(([v, rotulo, detalle]) => (
        <label
          key={v}
          className={[
            "flex cursor-pointer gap-3 rounded-xl border p-4",
            valor === v ? "border-azul-600 bg-azul-050" : "border-gris-300 hover:border-azul-400",
          ].join(" ")}
        >
          <input
            type="radio"
            name={nombre}
            value={v}
            checked={valor === v}
            onChange={() => cambiar(v)}
            className="mt-1 h-4 w-4 accent-azul-700"
          />
          <span>
            <span className="block text-body font-semibold text-gris-900">{rotulo}</span>
            <span className="block text-small text-gris-700">{detalle}</span>
          </span>
        </label>
      ))}
    </div>
  );
}

function AreaDeTexto({
  etiqueta,
  ayuda,
  valor,
  cambiar,
  error,
  maximo,
}: {
  etiqueta: string;
  ayuda?: string;
  valor: string;
  cambiar: (v: string) => void;
  error?: string;
  maximo: number;
}) {
  const id = useId();
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-small font-medium text-gris-700">
        {etiqueta}
      </label>
      <textarea
        id={id}
        value={valor}
        onChange={(e) => cambiar(e.target.value)}
        maxLength={maximo}
        rows={4}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : ayuda ? `${id}-ayuda` : undefined}
        className={`rounded-md border bg-blanco px-4 py-3 text-body text-gris-900 ${error ? "border-error" : "border-gris-300"}`}
      />
      {ayuda && !error && (
        <p id={`${id}-ayuda`} className="text-caption text-gris-700">
          {ayuda}
        </p>
      )}
      {error && (
        <p
          id={`${id}-error`}
          role="alert"
          aria-live="assertive"
          className="text-caption font-medium text-error"
        >
          {error}
        </p>
      )}
    </div>
  );
}
