"use client";

import { useEffect, useRef, useState, type KeyboardEvent } from "react";

import { Boton } from "@/componentes/Boton";
import { MARCAS, marcaVista, marcarVista } from "@/lib/marcas";

export const PASOS_DE_LA_BIENVENIDA = [
  {
    titulo: "Te damos la bienvenida a tu banca",
    texto:
      "En el resumen ves tu saldo, tu cuenta con su TREA y los últimos movimientos. El saldo sale de tus movimientos: nadie lo edita a mano.",
  },
  {
    titulo: "Transfiere en cuatro pasos",
    texto:
      "Datos, revisión, código y comprobante. Antes de mover el dinero te mostramos el resumen, y con «Corregir» vuelves sin perder lo escrito.",
  },
  {
    titulo: "Nada se mueve sin tu código",
    texto:
      "Cada transferencia pide tu segundo factor. Si la conexión falla y confirmas otra vez, la operación no se repite.",
  },
  {
    titulo: "Si tienes dudas",
    texto:
      "Pasa el ratón o el tabulador por un ícono para ver qué hace. Las preguntas frecuentes están en «Ayuda», en el menú.",
  },
] as const;

/**
 * Asistente del primer ingreso a la banca (sesión 24): cuatro pasos con barra de progreso.
 *
 * Solo aparece una vez por navegador; «Saltar» también cuenta como visto, porque obligar a
 * recorrerlo es justo la fricción que intenta quitar. Es un diálogo modal de verdad: el foco
 * entra al título, el tabulador no se escapa a la página de detrás y Escape lo cierra.
 */
export function AsistenteDeBienvenida() {
  const [abierto, setAbierto] = useState(false);
  const [paso, setPaso] = useState(0);
  const dialogo = useRef<HTMLDivElement>(null);
  const titulo = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    if (!marcaVista(MARCAS.bienvenida)) setAbierto(true);
  }, []);

  useEffect(() => {
    if (abierto) titulo.current?.focus();
  }, [abierto, paso]);

  if (!abierto) return null;

  const total = PASOS_DE_LA_BIENVENIDA.length;
  const ultimo = paso === total - 1;
  const { titulo: encabezado, texto } = PASOS_DE_LA_BIENVENIDA[paso];

  function cerrar() {
    marcarVista(MARCAS.bienvenida);
    setAbierto(false);
  }

  function alPulsar(evento: KeyboardEvent<HTMLDivElement>) {
    if (evento.key === "Escape") {
      cerrar();
      return;
    }
    if (evento.key !== "Tab" || !dialogo.current) return;
    const enfocables = dialogo.current.querySelectorAll<HTMLElement>("button:not([disabled])");
    const primero = enfocables[0];
    const ultimoEnfocable = enfocables[enfocables.length - 1];
    if (evento.shiftKey && document.activeElement === primero) {
      evento.preventDefault();
      ultimoEnfocable.focus();
    } else if (!evento.shiftKey && document.activeElement === ultimoEnfocable) {
      evento.preventDefault();
      primero.focus();
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-azul-900/60 p-4">
      <div
        ref={dialogo}
        role="dialog"
        aria-modal="true"
        aria-labelledby="titulo-bienvenida"
        aria-describedby="texto-bienvenida"
        onKeyDown={alPulsar}
        className="w-full max-w-md rounded-[20px] bg-blanco p-7 shadow-xl"
      >
        <p className="text-small font-semibold text-azul-700">
          Paso {paso + 1} de {total}
        </p>
        <div
          role="progressbar"
          aria-label="Avance de la bienvenida"
          aria-valuemin={1}
          aria-valuemax={total}
          aria-valuenow={paso + 1}
          className="mt-2 h-1.5 w-full rounded-full bg-gris-300"
        >
          <div
            className="h-1.5 rounded-full bg-azul-700 transition-all"
            style={{ width: `${((paso + 1) / total) * 100}%` }}
          />
        </div>

        <h2
          id="titulo-bienvenida"
          ref={titulo}
          tabIndex={-1}
          className="mt-5 text-h3 font-bold text-azul-900"
        >
          {encabezado}
        </h2>
        <p id="texto-bienvenida" className="mt-2 text-body text-gris-800">
          {texto}
        </p>

        <div className="mt-7 flex flex-wrap items-center justify-between gap-3">
          <button
            type="button"
            onClick={cerrar}
            className="text-small font-semibold text-gris-700 hover:underline"
          >
            Saltar
          </button>
          <div className="flex gap-3">
            {paso > 0 && (
              <Boton type="button" variante="contorno" onClick={() => setPaso(paso - 1)}>
                Anterior
              </Boton>
            )}
            <Boton type="button" onClick={ultimo ? cerrar : () => setPaso(paso + 1)}>
              {ultimo ? "Empezar" : "Siguiente"}
            </Boton>
          </div>
        </div>
      </div>
    </div>
  );
}
