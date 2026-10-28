"use client";

import { useState, type FormEvent } from "react";

import { Boton } from "@/componentes/Boton";
import { AFIRMACIONES_SUS, interpretarSus, puntuacionSus } from "@/lib/sus";

const ESCALA = [1, 2, 3, 4, 5] as const;

/**
 * Opinión del usuario (sesión 24) con el cuestionario SUS del anexo A del reporte de
 * usabilidad: diez afirmaciones de 1 a 5 y una puntuación de 0 a 100.
 *
 * shortcut: la puntuación se calcula y se muestra en el navegador, no se guarda en el
 * servidor; quien conduce la prueba con usuarios la anota. Si se quiere medir en producción,
 * añadir un endpoint y una tabla.
 */
export default function PaginaDeOpinion() {
  const [faltan, setFaltan] = useState<number[]>([]);
  const [puntuacion, setPuntuacion] = useState<number | null>(null);

  function calcular(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    const datos = new FormData(evento.currentTarget);
    const respuestas = AFIRMACIONES_SUS.map((_, i) => Number(datos.get(`a${i + 1}`)));
    const sinResponder = respuestas.flatMap((r, i) => (r ? [] : [i + 1]));
    setFaltan(sinResponder);
    setPuntuacion(sinResponder.length ? null : puntuacionSus(respuestas));
  }

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <header>
        <h1 className="text-h1 font-bold text-azul-800">Tu opinión</h1>
        <p className="text-body text-gris-700">
          Diez afirmaciones, un minuto. Marca de 1 (totalmente en desacuerdo) a 5 (totalmente de
          acuerdo).
        </p>
      </header>

      {puntuacion !== null ? (
        <section
          role="status"
          aria-labelledby="titulo-resultado-sus"
          className="rounded-[20px] border border-azul-200 bg-blanco p-7"
        >
          <h2 id="titulo-resultado-sus" className="text-h3 font-bold text-azul-900">
            Gracias por contarnos
          </h2>
          <p className="cifra mt-3 text-display text-azul-900">
            {puntuacion.toLocaleString("es-PE")} <span className="text-h3">de 100</span>
          </p>
          <p className="mt-2 text-body text-gris-800">{interpretarSus(puntuacion)}</p>
        </section>
      ) : (
        <form onSubmit={calcular} noValidate className="flex flex-col gap-4">
          {faltan.length > 0 && (
            <p
              role="alert"
              className="rounded-lg border border-error bg-blanco p-4 text-body text-error"
            >
              Falta responder {faltan.length === 1 ? "la afirmación" : "las afirmaciones"}{" "}
              {faltan.join(", ")}. Elige un número en cada una.
            </p>
          )}
          {AFIRMACIONES_SUS.map((afirmacion, i) => (
            <fieldset
              key={afirmacion}
              className={`rounded-[16px] border bg-blanco p-5 ${
                faltan.includes(i + 1) ? "border-error" : "border-azul-200"
              }`}
            >
              <legend className="sr-only">{`${i + 1}. ${afirmacion}`}</legend>
              <p aria-hidden="true" className="text-body font-semibold text-gris-900">
                {i + 1}. {afirmacion}
              </p>
              <div className="mt-3 flex flex-wrap gap-2">
                {ESCALA.map((valor) => (
                  <label
                    key={valor}
                    className="flex min-h-12 min-w-12 cursor-pointer items-center justify-center rounded-full border border-gris-300 text-body font-semibold text-gris-800 has-[:checked]:border-azul-700 has-[:checked]:bg-azul-700 has-[:checked]:text-blanco has-[:focus-visible]:outline has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-azul-600"
                  >
                    <input type="radio" name={`a${i + 1}`} value={valor} className="sr-only" />
                    {valor}
                  </label>
                ))}
              </div>
            </fieldset>
          ))}
          <Boton type="submit">Enviar mi opinión</Boton>
        </form>
      )}
    </div>
  );
}
