import { Fingerprint, Lock, TrendingUp } from "lucide-react";
import Link from "next/link";

import { LogotipoAyni } from "@/componentes/LogotipoAyni";

const ARGUMENTOS = [
  { Icono: TrendingUp, texto: "4.50 % TREA en soles · 1.20 % en dólares" },
  { Icono: Lock, texto: "Cifrado en tránsito y en reposo" },
  { Icono: Fingerprint, texto: "Segundo factor en cada operación sensible" },
];

/**
 * Columna izquierda de las pantallas de ingreso, según el diseño aprobado en pen.dev.
 *
 * Es idéntica en las dos pantallas del login —credenciales y código— y por eso vive
 * aparte: en el prototipo son dos lienzos distintos, pero repetir el marcado en los dos
 * sitios garantiza que tarde o temprano se desincronicen.
 *
 * Por debajo de `lg` desaparece entera. No es contenido, es ambientación: en una pantalla
 * de móvil solo empujaría el formulario fuera de la vista, y el prototipo móvil (`M6a`)
 * tampoco la incluye.
 */
export function PanelDeMarca() {
  return (
    <aside
      className="relative hidden overflow-hidden bg-azul-900 bg-cover bg-center px-16 py-14 lg:flex lg:flex-col lg:justify-between"
      style={{ backgroundImage: "url('/pen/landing-0d7618605d36.webp')" }}
    >
      {/* Anillos concéntricos del fondo. Decorativos. */}
      <div aria-hidden="true" className="pointer-events-none absolute inset-0">
        <div className="absolute top-[22%] left-[38%] h-[560px] w-[560px] rounded-full border border-blanco/8" />
        <div className="absolute top-[34%] left-[52%] h-[380px] w-[380px] rounded-full border border-oro/12" />
      </div>

      <Link href="/" className="relative">
        <LogotipoAyni altura={17} />
      </Link>

      <div className="relative">
        <h1 className="max-w-[420px] text-[clamp(2.2rem,3.2vw,44px)] leading-[1.1] font-bold text-blanco">
          Tu banca, siempre contigo.
        </h1>
        <p className="mt-5 max-w-[400px] text-[16px] leading-[1.6] text-texto-bajada">
          Banca 100 % digital para personas naturales en Perú. Sin agencias, sin comisión de
          mantenimiento y con devengo diario.
        </p>

        <ul className="mt-9 flex flex-col gap-3.5">
          {ARGUMENTOS.map(({ Icono, texto }) => (
            <li key={texto} className="flex items-center gap-3 text-[14px] text-texto-tenue">
              <Icono aria-hidden="true" className="h-4 w-4 shrink-0 text-oro" />
              {texto}
            </li>
          ))}
        </ul>
      </div>

      <p className="relative text-[12.5px] text-texto-franja">
        Diseñado conforme al marco normativo de la SBS.
      </p>
    </aside>
  );
}
