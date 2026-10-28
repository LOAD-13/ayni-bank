"use client";

import { Award } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";

import { MARCAS, marcaVista, marcarVista } from "@/lib/marcas";

/**
 * Reconocimiento de la primera operación completada (sesión 24, compromiso del usuario).
 *
 * Sale una sola vez, junto al primer comprobante, y aprovecha el momento para pedir la
 * opinión: es cuando la persona acaba de usar la banca de principio a fin.
 */
export function LogroDePrimeraOperacion() {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    if (marcaVista(MARCAS.primeraOperacion)) return;
    marcarVista(MARCAS.primeraOperacion);
    setVisible(true);
  }, []);

  if (!visible) return null;

  return (
    <div
      role="status"
      className="flex gap-4 rounded-[16px] border border-dorado-500 bg-dorado-100 p-5"
    >
      <span
        aria-hidden="true"
        className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-dorado-500 text-blanco"
      >
        <Award className="h-6 w-6" />
      </span>
      <div>
        <p className="text-body font-bold text-dorado-800">¡Primera operación completada!</p>
        <p className="mt-1 text-small text-gris-800">
          Ya sabes mover tu dinero en Ayni.{" "}
          <Link href="/banca/opinion" className="font-semibold text-azul-700 hover:underline">
            Cuéntanos qué tal te fue
          </Link>{" "}
          en un minuto.
        </p>
      </div>
    </div>
  );
}
