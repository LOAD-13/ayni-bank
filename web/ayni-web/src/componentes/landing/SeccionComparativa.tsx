import { Check, Minus } from "lucide-react";
import Image from "next/image";

/**
 * Comparativa del diseño aprobado.
 *
 * **No compara con ningún banco.** Contrapone el dinero quieto frente al mismo dinero en
 * una Cuenta Ayni, con las cifras del propio producto. Nombrar a un competidor exigiría
 * poder sostener sus datos, y no podemos.
 */
const FILAS = [
  { concepto: "Intereses en 12 meses sobre S/ 10 000", quieto: "S/ 0", ayni: "S/ 459" },
  { concepto: "Cuándo se calcula el interés", quieto: "Nunca", ayni: "Todos los días" },
  { concepto: "Saldo al cabo de un año", quieto: "S/ 10 000", ayni: "S/ 10 459" },
];

export function SeccionComparativa() {
  return (
    <section
      id="comparativa"
      className="relative overflow-hidden bg-azul-900 bg-cover bg-center"
      style={{ backgroundImage: "url('/pen/landing-4466df076e3e.webp')" }}
    >
      <div className="relative mx-auto grid max-w-[1440px] items-center gap-12 px-6 py-20 lg:grid-cols-2 lg:px-16 lg:py-28">
        <div>
          <p className="flex items-center gap-2.5">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-oro" />
            <span className="text-[12.5px] font-bold tracking-[0.14em] text-dorado-400">
              LA DIFERENCIA
            </span>
          </p>

          <h2 className="mt-4 max-w-[420px] text-[clamp(1.9rem,3.6vw,46px)] leading-[1.12] font-bold text-blanco">
            Compara sin letra chica.
          </h2>

          <p className="mt-5 max-w-[420px] text-[16px] leading-[1.6] text-texto-tenue">
            Sin comparaciones con nadie: tu dinero quieto frente a tu dinero en una Cuenta Ayni, con
            las cifras del propio producto.
          </p>

          <Image
            src="/pen/landing-543cde9cf013.png"
            alt=""
            aria-hidden="true"
            width={120}
            height={120}
            className="mt-10 hidden h-[110px] w-[110px] lg:block"
          />
        </div>

        <div className="overflow-hidden rounded-[20px] bg-azul-tarjeta/35 backdrop-blur-sm">
          <table className="w-full border-collapse text-left">
            <caption className="sr-only">
              Comparación entre mantener el dinero sin remunerar y mantenerlo en una Cuenta Ayni
            </caption>
            <thead>
              <tr className="bg-blanco/5">
                <th scope="col" className="p-5 text-[13px] font-normal text-texto-franja">
                  <span className="sr-only">Concepto</span>
                </th>
                <th scope="col" className="p-5 text-right text-[13px] font-bold text-texto-franja">
                  Guardado sin remunerar
                </th>
                <th scope="col" className="p-3 text-right">
                  <span className="inline-flex rounded-full bg-oro px-3.5 py-1.5 text-[13px] font-bold text-azul-900">
                    En tu Cuenta Ayni
                  </span>
                </th>
              </tr>
            </thead>
            <tbody>
              {FILAS.map(({ concepto, quieto, ayni }) => (
                <tr key={concepto} className="border-t border-blanco/8">
                  <th scope="row" className="p-5 text-[14.5px] font-medium text-texto-bajada">
                    {concepto}
                  </th>
                  {/* El signo acompaña siempre al valor: distinguir «no rinde» de «rinde»
                      solo por el color no es accesible. */}
                  <td className="cifra px-4 py-4 text-right text-[14px] font-semibold whitespace-nowrap text-texto-franja">
                    <Minus aria-hidden="true" className="mr-1.5 inline h-3.5 w-3.5" />
                    {quieto}
                  </td>
                  <td className="cifra px-5 py-4 text-right text-[14.5px] font-bold whitespace-nowrap text-oro">
                    <Check aria-hidden="true" className="mr-1.5 inline h-4 w-4" />
                    {ayni}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  );
}
