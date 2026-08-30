import { CircleCheck, Zap } from "lucide-react";
import Image from "next/image";
import Link from "next/link";

import { TarjetaAyni } from "./TarjetaAyni";

/** Altura relativa de cada barra del gráfico de rendimiento, en porcentaje. */
const MESES = [
  { inicial: "E", altura: 34 },
  { inicial: "F", altura: 39 },
  { inicial: "M", altura: 44 },
  { inicial: "A", altura: 49 },
  { inicial: "M", altura: 55 },
  { inicial: "J", altura: 60 },
  { inicial: "J", altura: 66 },
  { inicial: "A", altura: 72 },
  { inicial: "S", altura: 78 },
  { inicial: "O", altura: 85, acento: true },
  { inicial: "N", altura: 92, acento: true },
  { inicial: "D", altura: 100, acento: true },
];

const SIN_COMISIONES = [
  "Mantenimiento S/ 0.00",
  "Retiros en cajeros aliados",
  "Envío de tarjeta física",
];

/** Sección «La cuenta Ayni»: encabezado y las cinco tarjetas del diseño aprobado. */
export function SeccionCuenta() {
  return (
    <section id="cuenta" className="bg-azul-050">
      <div className="mx-auto max-w-[1440px] px-6 py-16 lg:px-16 lg:py-24">
        <header className="grid gap-8 lg:grid-cols-[1fr_420px] lg:gap-16">
          <div>
            <p className="flex items-center gap-2.5">
              <span aria-hidden="true" className="h-2 w-2 rounded-full bg-dorado-500" />
              <span className="text-[12.5px] font-bold tracking-[0.14em] text-azul-600">
                LA CUENTA AYNI
              </span>
            </p>
            <h2 className="mt-4 max-w-[620px] text-[clamp(2rem,4vw,52px)] leading-[1.1] font-bold text-azul-900">
              Una cuenta que trabaja mientras tú vives.
            </h2>
          </div>

          <div className="lg:pt-12">
            <p className="text-[16px] leading-[1.6] text-gris-700">
              Sin monto mínimo, sin comisión de mantenimiento y con intereses que se calculan todos
              los días —no a fin de mes—. Tu plata siempre disponible.
            </p>
            <Link
              href="/pendiente"
              className="mt-4 inline-flex items-center gap-1.5 text-[15px] font-bold text-azul-600 hover:underline"
            >
              Ver tarifario completo <span aria-hidden="true">↗</span>
            </Link>
          </div>
        </header>

        {/* Primera fila del bento: rendimiento y tarjeta. */}
        <div className="mt-12 grid gap-6 lg:grid-cols-2">
          <article className="relative overflow-hidden rounded-[24px] bg-gradient-to-br from-azul-900 to-azul-700 p-8">
            <h3 className="text-[28px] leading-tight font-bold text-blanco">
              Tu saldo rinde cada día
            </h3>
            <p className="mt-2 max-w-[400px] text-[15px] text-texto-tenue">
              Devengo diario con capitalización mensual. Mira cómo crecen S/ 10 000 en un año.
            </p>

            {/* Gráfico decorativo: la cifra que importa va escrita debajo, de modo que
                quien no ve las barras recibe igualmente el dato. */}
            <div
              aria-hidden="true"
              className="mt-10 flex h-[150px] items-end justify-between gap-1.5"
            >
              {MESES.map(({ inicial, altura, acento }, indice) => (
                // `h-full` en la columna: la barra usa altura en porcentaje, y un
                // porcentaje solo resuelve contra un padre de altura definida. Sin esto
                // la barra mide cero y el gráfico aparece vacío.
                <div
                  key={`${inicial}-${indice}`}
                  className="flex h-full flex-1 flex-col items-center justify-end gap-2"
                >
                  <div
                    style={{ height: `${altura}%` }}
                    className={`w-full rounded-t-[4px] bg-gradient-to-b ${
                      acento ? "from-oro to-dorado-500" : "from-azul-400 to-azul-600"
                    }`}
                  />
                  <span className="text-[11px] font-semibold text-texto-franja">{inicial}</span>
                </div>
              ))}
            </div>

            <p className="cifra mt-8 text-[32px] leading-none font-bold text-oro">S/ 10 459</p>
            <p className="mt-1.5 text-[13px] text-texto-franja">Saldo proyectado a 12 meses</p>

            <Image
              src="/pen/landing-543cde9cf013.png"
              alt=""
              aria-hidden="true"
              width={110}
              height={110}
              className="absolute right-5 bottom-5 hidden h-[110px] w-[110px] sm:block"
            />
          </article>

          <article className="relative overflow-hidden rounded-[24px] bg-dorado-100 p-8">
            <h3 className="max-w-[320px] text-[26px] leading-tight font-bold text-azul-900">
              Tarjeta de débito digital al instante
            </h3>
            <p className="mt-3 max-w-[360px] text-[14.5px] leading-[1.55] text-gris-700">
              Úsala apenas abres la cuenta: paga online, agrégala a tu wallet y pide la física sin
              costo.
            </p>

            <div className="relative mt-10 flex justify-center pb-4">
              <div
                aria-hidden="true"
                className="absolute inset-x-8 top-6 bottom-0 rounded-[28px] bg-dorado-500/20 blur-2xl"
              />
              {/* Tarjeta dorada asomando por detrás, como en el diseño. */}
              <div
                aria-hidden="true"
                className="absolute top-[-14px] right-[14%] h-[172px] w-[272px] rotate-[6deg] rounded-[16px] bg-gradient-to-br from-oro to-dorado-600 p-4"
              >
                <span className="text-[14px] font-bold text-dorado-800">Ayni Gold</span>
              </div>
              <TarjetaAyni />
            </div>
          </article>
        </div>

        {/* Segunda fila del bento: transferencias, costos y metas. */}
        <div className="mt-6 grid gap-6 lg:grid-cols-3">
          <article className="relative overflow-hidden rounded-[24px] bg-gradient-to-br from-azul-100 to-azul-200 p-8">
            <Image
              src="/pen/landing-a29a72e1cbfb.png"
              alt=""
              aria-hidden="true"
              width={120}
              height={120}
              className="h-[92px] w-[92px]"
            />
            <h3 className="mt-6 text-[24px] leading-tight font-bold text-azul-900">
              Transferencias inmediatas 24/7
            </h3>
            <p className="mt-2 text-[14.5px] leading-[1.55] text-gris-700">
              A cualquier banco o billetera del Perú. Sin costo y sin horarios.
            </p>
            <p className="mt-6 inline-flex items-center gap-2 rounded-full bg-blanco px-4 py-2 text-[12.5px] font-bold text-azul-800">
              <Zap aria-hidden="true" className="h-3.5 w-3.5 text-dorado-600" />
              Llega en menos de 10 segundos
            </p>
          </article>

          <article className="rounded-[24px] bg-blanco p-8">
            <div className="inline-flex rounded-[18px] bg-gradient-to-br from-azul-900 to-azul-700 p-4">
              <Image
                src="/pen/landing-ba234392b891.png"
                alt=""
                aria-hidden="true"
                width={80}
                height={80}
                className="h-[52px] w-[52px]"
              />
            </div>
            <h3 className="mt-6 text-[24px] leading-tight font-bold text-azul-900">
              Cero comisiones ocultas
            </h3>
            <ul className="mt-4 flex flex-col gap-2.5">
              {SIN_COMISIONES.map((concepto) => (
                <li
                  key={concepto}
                  className="flex items-center gap-2.5 text-[14.5px] font-medium text-gris-700"
                >
                  <CircleCheck aria-hidden="true" className="h-4 w-4 shrink-0 text-exito" />
                  {concepto}
                </li>
              ))}
            </ul>
          </article>

          <article className="relative min-h-[320px] overflow-hidden rounded-[24px] bg-azul-900">
            <Image
              src="/pen/landing-5c8c3d7ae82c.png"
              alt=""
              aria-hidden="true"
              fill
              sizes="(max-width: 1024px) 90vw, 420px"
              className="object-cover"
            />
            <div
              aria-hidden="true"
              className="absolute inset-0 bg-gradient-to-t from-azul-900 to-azul-900/15"
            />
            <div className="absolute inset-x-0 bottom-0 p-8">
              <h3 className="text-[24px] leading-tight font-bold text-blanco">
                Metas de ahorro automáticas
              </h3>
              <p className="mt-2 text-[14.5px] leading-[1.55] text-azul-200">
                Aparta plata cada semana sin pensarlo y mírala crecer con su propio interés.
              </p>
            </div>
          </article>
        </div>
      </div>
    </section>
  );
}
