/** Preguntas frecuentes con `<details>` nativo: se abren con teclado y el lector anuncia su estado. */
export function Preguntas({
  preguntas,
}: {
  preguntas: readonly { pregunta: string; respuesta: string }[];
}) {
  return (
    <div className="flex flex-col gap-3">
      {preguntas.map(({ pregunta, respuesta }) => (
        <details
          key={pregunta}
          className="group rounded-[16px] border border-azul-200 bg-blanco p-5 open:border-azul-400"
        >
          <summary className="cursor-pointer text-body font-semibold text-gris-900 marker:text-azul-700">
            {pregunta}
          </summary>
          <p className="mt-3 max-w-[70ch] text-body leading-[1.6] text-gris-700">{respuesta}</p>
        </details>
      ))}
    </div>
  );
}
