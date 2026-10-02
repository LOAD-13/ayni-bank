import { Eye, Handshake, Sprout } from "lucide-react";
import Image from "next/image";

const PILARES = [
  { Icono: Handshake, titulo: "Reciprocidad", sub: "Ganamos si tú ganas" },
  { Icono: Eye, titulo: "Transparencia", sub: "Todo el costo, a la vista" },
  { Icono: Sprout, titulo: "Cercanía", sub: "Hecho para el Perú" },
];

/** Sección «Qué significa Ayni», con la fotografía del mercado de fondo. */
export function SeccionAyni() {
  return (
    <section id="ayni" className="relative overflow-hidden bg-noche">
      <Image
        src="/pen/landing-35866074d9c8.jpg"
        alt=""
        aria-hidden="true"
        fill
        sizes="100vw"
        className="object-cover"
      />
      {/* Velo lateral: el texto va sobre la fotografía y sin él pierde contraste, que es
          justo lo que exige WCAG 2.1 AA en el criterio 1.4.3. */}
      <div
        aria-hidden="true"
        className="absolute inset-0 bg-gradient-to-r from-noche via-noche/95 to-noche/25"
      />

      <div className="relative mx-auto max-w-[1440px] px-6 py-20 lg:px-16 lg:py-28">
        <div className="max-w-[620px]">
          <p className="flex items-center gap-2.5">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-oro" />
            <span className="text-[12.5px] font-bold tracking-[0.14em] text-dorado-400">
              QUÉ SIGNIFICA AYNI
            </span>
          </p>

          <h2 className="mt-4 text-[clamp(2rem,4.2vw,54px)] leading-[1.1] font-bold text-blanco">
            Hoy por ti, mañana por mí.
          </h2>

          <p className="mt-6 text-[16.5px] leading-[1.65] text-texto-bajada">
            «Ayni» es la palabra quechua para la reciprocidad andina: el trabajo que se devuelve.
            Así entendemos la banca. Si tu dinero trabaja para ti desde el primer día, nosotros
            ganamos contigo y no a tu costa.
          </p>

          <ul className="mt-10 grid gap-8 sm:grid-cols-3">
            {PILARES.map(({ Icono, titulo, sub }) => (
              <li key={titulo}>
                <Icono aria-hidden="true" className="h-5 w-5 text-oro" />
                <p className="mt-3 text-[16px] font-bold text-blanco">{titulo}</p>
                <p className="mt-1.5 text-[13.5px] font-medium text-azul-200">{sub}</p>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </section>
  );
}
