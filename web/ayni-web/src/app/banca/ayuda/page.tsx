import Link from "next/link";

import { PREGUNTAS_DE_LA_BANCA } from "@/lib/preguntas-frecuentes";

/**
 * Ayuda contextual de la banca (sesión 24): las dudas que aparecen al operar, respondidas en
 * la propia aplicación. `<details>` es nativo: se abre con teclado y el lector de pantalla
 * anuncia si está abierto o cerrado sin ARIA añadido.
 */
export default function PaginaDeAyuda() {
  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <header>
        <h1 className="text-h1 font-bold text-azul-800">Ayuda</h1>
        <p className="text-body text-gris-700">Las preguntas que más nos hacen al usar la banca.</p>
      </header>

      <div className="flex flex-col gap-3">
        {PREGUNTAS_DE_LA_BANCA.map(({ pregunta, respuesta }) => (
          <details
            key={pregunta}
            className="group rounded-[16px] border border-azul-200 bg-blanco p-5 open:border-azul-400"
          >
            <summary className="cursor-pointer text-body font-semibold text-gris-900 marker:text-azul-700">
              {pregunta}
            </summary>
            <p className="mt-3 text-body text-gris-800">{respuesta}</p>
          </details>
        ))}
      </div>

      <p className="text-body text-gris-800">
        ¿Algo no fue claro?{" "}
        <Link href="/banca/opinion" className="font-semibold text-azul-700 hover:underline">
          Cuéntanos qué tal te va con la banca
        </Link>
        .
      </p>
    </div>
  );
}
