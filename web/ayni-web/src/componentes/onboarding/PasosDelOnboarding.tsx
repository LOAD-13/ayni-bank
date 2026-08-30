import { Check } from "lucide-react";

const PASOS = ["Tus datos", "DNI anverso", "DNI reverso", "Prueba de vida", "Listo"] as const;

interface Props {
  /** Paso en curso, empezando en 1. Todos los anteriores se marcan como cumplidos. */
  actual: number;
  /** `true` cuando el proceso terminó bien: el último paso también sale en verde. */
  completado?: boolean;
}

/**
 * La barra de pasos del onboarding, según el diseño aprobado en pen.dev.
 *
 * El paso en curso se marca además con `aria-current`: sin eso, quien navega con lector de
 * pantalla oye cinco etiquetas seguidas sin saber en cuál está, que es justo la información
 * que la barra existe para dar.
 */
export function PasosDelOnboarding({ actual, completado = false }: Props) {
  return (
    <nav aria-label="Progreso del registro" className="overflow-x-auto">
      <ol className="mx-auto flex min-w-[560px] max-w-[720px] items-start">
        {PASOS.map((texto, indice) => {
          const numero = indice + 1;
          const cumplido = completado ? numero <= actual : numero < actual;
          const enCurso = !completado && numero === actual;

          return (
            <li
              key={texto}
              className="flex flex-1 flex-col items-center"
              aria-current={enCurso ? "step" : undefined}
            >
              <div className="flex w-full items-center">
                {/* Las líneas de unión son decorativas: el orden ya lo da la lista. */}
                <span
                  aria-hidden="true"
                  className={`h-px flex-1 ${indice === 0 ? "opacity-0" : ""} ${
                    cumplido || enCurso ? "bg-exito" : "bg-gris-300"
                  }`}
                />
                <span
                  aria-hidden="true"
                  className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-[12.5px] font-bold ${
                    cumplido
                      ? "bg-exito text-blanco"
                      : enCurso
                        ? "bg-dorado-500 text-azul-900"
                        : "border border-gris-300 bg-blanco text-gris-500"
                  }`}
                >
                  {cumplido ? <Check className="h-3.5 w-3.5" /> : numero}
                </span>
                <span
                  aria-hidden="true"
                  className={`h-px flex-1 ${
                    indice === PASOS.length - 1 ? "opacity-0" : ""
                  } ${cumplido ? "bg-exito" : "bg-gris-300"}`}
                />
              </div>
              <span
                className={`mt-2 text-center text-[12px] ${
                  cumplido || enCurso ? "font-semibold text-azul-800" : "text-gris-500"
                }`}
              >
                {texto}
              </span>
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
