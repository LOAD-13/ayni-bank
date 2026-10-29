export const PASOS_DEL_ONBOARDING = [
  "Tus datos",
  "Segundo factor",
  "DNI anverso",
  "DNI reverso",
  "Prueba de vida",
  "Listo",
] as const;

/** Los pasos de una transferencia (propia o interbancaria): la sesión 24 pide verlos. */
export const PASOS_DE_UNA_TRANSFERENCIA = ["Datos", "Revisión", "Código", "Comprobante"] as const;

/**
 * Indicador de pasos: los seis del onboarding por omisión, o los que se le pasen.
 *
 * En movil se reduce a «Paso N de 6» con una barra: seis circulos con etiqueta en 360 px
 * quedan ilegibles, y un indicador que no se lee no indica nada.
 *
 * `aria-label` describe el estado completo en una frase, porque recorrer seis circulos
 * con lector de pantalla para deducir en cual se esta es trabajo que la interfaz puede
 * ahorrar.
 */
export function IndicadorDeProgreso({
  pasoActual,
  pasos = PASOS_DEL_ONBOARDING,
}: {
  pasoActual: number;
  pasos?: readonly string[];
}) {
  const total = pasos.length;

  return (
    <div
      role="group"
      aria-label={`Paso ${pasoActual} de ${total}: ${pasos[pasoActual - 1]}`}
      className="w-full"
    >
      {/* Móvil */}
      <div className="sm:hidden">
        <p className="text-small font-semibold text-azul-700">
          Paso {pasoActual} de {total} · {pasos[pasoActual - 1]}
        </p>
        <div className="mt-2 h-1.5 w-full rounded-full bg-gris-300" aria-hidden="true">
          <div
            className="h-1.5 rounded-full bg-azul-700 transition-all"
            style={{ width: `${(pasoActual / total) * 100}%` }}
          />
        </div>
      </div>

      {/* Escritorio */}
      <ol className="hidden sm:flex sm:items-start" aria-hidden="true">
        {pasos.map((etiqueta, indice) => {
          const numero = indice + 1;
          const completado = numero < pasoActual;
          const actual = numero === pasoActual;

          return (
            <li key={etiqueta} className="flex flex-1 items-start last:flex-none">
              <div className="flex flex-1 flex-col items-center gap-2.5">
                <span
                  className={`flex h-9 w-9 items-center justify-center rounded-full text-small font-bold ${
                    completado || actual
                      ? "bg-azul-700 text-blanco"
                      : "border border-gris-300 bg-blanco text-gris-500"
                  }`}
                >
                  {completado ? "✓" : numero}
                </span>
                <span
                  className={`text-center text-caption font-semibold ${
                    actual ? "text-azul-700" : "text-gris-500"
                  }`}
                >
                  {etiqueta}
                </span>
              </div>

              {numero < total && (
                <div className="flex h-9 flex-1 items-center">
                  <span
                    className={`h-0.5 flex-1 rounded-full ${
                      completado ? "bg-azul-700" : "bg-gris-300"
                    }`}
                  />
                </div>
              )}
            </li>
          );
        })}
      </ol>
    </div>
  );
}
