import { CircleCheck, MessageCircle } from "lucide-react";
import Image from "next/image";
import Link from "next/link";

/**
 * Columnas del pie, según el diseño aprobado.
 *
 * Todo enlace apunta a un destino real: si la pantalla no existe todavía, lleva a la
 * página de pendientes. Un enlace que devuelve 404 le enseña al visitante que la web está
 * rota, y uno que no reacciona le hace creer que falla su navegador.
 */
const COLUMNAS = [
  {
    titulo: "Productos",
    enlaces: [
      { texto: "Cuenta Ayni", destino: "#cuenta" },
      { texto: "Depósito a plazo", destino: "/pendiente" },
      { texto: "Tarjeta de crédito", destino: "/pendiente" },
      { texto: "Préstamo personal", destino: "/pendiente" },
      { texto: "Seguros", destino: "/pendiente" },
    ],
  },
  {
    titulo: "Negocios",
    enlaces: [
      { texto: "Cuenta negocio", destino: "/pendiente" },
      { texto: "Cobros con QR", destino: "/pendiente" },
      { texto: "Facturación", destino: "/pendiente" },
      { texto: "Préstamo pyme", destino: "/pendiente" },
    ],
  },
  {
    titulo: "Ayni Bank",
    enlaces: [
      { texto: "Quiénes somos", destino: "/pendiente" },
      { texto: "Trabaja con nosotros", destino: "/pendiente" },
      { texto: "Sala de prensa", destino: "/pendiente" },
      { texto: "Blog Ayni", destino: "/pendiente" },
    ],
  },
  {
    titulo: "Ayuda",
    enlaces: [
      { texto: "Centro de ayuda", destino: "/pendiente" },
      { texto: "Canales de atención", destino: "/pendiente" },
      { texto: "Libro de reclamaciones", destino: "/pendiente" },
      { texto: "Tarifario", destino: "/pendiente" },
      { texto: "Transparencia", destino: "/pendiente" },
    ],
  },
];

const REDES = [
  { red: "instagram", nombre: "Instagram" },
  { red: "facebook", nombre: "Facebook" },
  { red: "linkedin", nombre: "LinkedIn" },
  { red: "youtube", nombre: "YouTube" },
] as const;

const SELLOS = [
  "Diseñado conforme a la normativa SBS",
  "Controles según ISO/IEC 27001",
  "Proyecto académico",
];

/** Llamada final a la acción, sobre el degradado dorado del diseño. */
export function CierreFinal() {
  return (
    <section className="relative overflow-hidden bg-gradient-to-br from-oro-claro to-dorado-500">
      <div
        aria-hidden="true"
        className="pointer-events-none absolute top-1/2 right-[8%] h-[420px] w-[420px] -translate-y-1/2 rounded-full bg-blanco/30 blur-[70px]"
      />

      <div className="relative mx-auto grid max-w-[1440px] items-center gap-10 px-6 py-20 lg:grid-cols-[1fr_360px] lg:px-16 lg:py-24">
        <div>
          <Image
            src="/pen/landing-c545a0328b79.png"
            alt=""
            aria-hidden="true"
            width={48}
            height={48}
            className="h-9 w-9"
          />

          <h2 className="mt-5 max-w-[640px] text-[clamp(1.9rem,4.2vw,52px)] leading-[1.1] font-bold text-azul-900">
            Abre tu cuenta hoy y mañana ya estará rindiendo.
          </h2>

          <p className="mt-5 max-w-[520px] text-[17px] leading-[1.6] text-dorado-800">
            Solo necesitas tu DNI y 5 minutos. Sin papeles, sin filas y sin monto mínimo.
          </p>

          <div className="mt-8 flex flex-col gap-4 sm:flex-row">
            <Link
              href="/registro"
              className="inline-flex min-h-[44px] items-center justify-center gap-2.5 rounded-full bg-azul-900 px-7 py-4 text-[16px] font-bold text-blanco hover:bg-azul-800"
            >
              Abrir mi cuenta gratis <span aria-hidden="true">→</span>
            </Link>
            <Link
              href="/pendiente"
              className="inline-flex min-h-[44px] items-center justify-center gap-2.5 rounded-full border border-azul-900/25 px-7 py-4 text-[16px] font-semibold text-azul-900 hover:bg-blanco/25"
            >
              <MessageCircle aria-hidden="true" className="h-4 w-4" />
              Hablar con un asesor
            </Link>
          </div>

          <p className="mt-6 text-[13px] font-medium text-dorado-800">
            Ayni Bank es un proyecto académico. Diseñado conforme al marco normativo de la SBS.
          </p>
        </div>

        <Image
          src="/pen/landing-887af3540f1f.png"
          alt=""
          aria-hidden="true"
          width={320}
          height={320}
          className="mx-auto h-[220px] w-[220px] lg:h-[300px] lg:w-[300px]"
        />
      </div>
    </section>
  );
}

export function PiePagina() {
  return (
    <footer className="bg-noche">
      <div className="mx-auto max-w-[1440px] px-6 py-16 lg:px-16">
        <div className="grid gap-12 lg:grid-cols-[320px_repeat(4,1fr)]">
          <div>
            <Image
              src="/pen/landing-09a1f7612368.png"
              alt="Ayni Bank"
              width={220}
              height={90}
              className="h-[86px] w-auto"
            />
            <p className="mt-5 max-w-[260px] text-[14px] leading-[1.6] text-texto-franja">
              Banca 100% digital para personas y negocios en el Perú. Hoy por ti, mañana por mí.
            </p>
            <ul className="mt-6 flex gap-3">
              {REDES.map(({ red, nombre }) => (
                <li key={nombre}>
                  <Link
                    href="/pendiente"
                    aria-label={nombre}
                    className="inline-flex h-10 w-10 items-center justify-center rounded-full bg-blanco/6 text-texto-franja hover:bg-blanco/12 hover:text-blanco"
                  >
                    <IconoRed red={red} />
                  </Link>
                </li>
              ))}
            </ul>
          </div>

          {COLUMNAS.map(({ titulo, enlaces }) => (
            <nav key={titulo} aria-label={titulo}>
              <h2 className="text-[14.5px] font-bold text-blanco">{titulo}</h2>
              <ul className="mt-4 flex flex-col gap-3">
                {enlaces.map(({ texto, destino }) => (
                  <li key={texto}>
                    <Link
                      href={destino}
                      className="text-[13.5px] text-azul-300/80 hover:text-blanco"
                    >
                      {texto}
                    </Link>
                  </li>
                ))}
              </ul>
            </nav>
          ))}
        </div>

        <div className="mt-14 flex flex-col gap-5 border-t border-blanco/10 pt-7 lg:flex-row lg:items-center lg:justify-between">
          <p className="max-w-[560px] text-[12.5px] leading-[1.6] text-texto-franja">
            © 2026 Ayni Bank · Proyecto académico del curso Integrador II. No es una entidad
            financiera autorizada.
          </p>
          <ul className="flex flex-wrap gap-x-7 gap-y-2 text-[12.5px] font-semibold text-azul-300/80">
            {SELLOS.map((sello) => (
              <li key={sello} className="inline-flex items-center gap-2">
                <CircleCheck aria-hidden="true" className="h-3.5 w-3.5 text-dorado-500" />
                {sello}
              </li>
            ))}
          </ul>
        </div>
      </div>
    </footer>
  );
}

/**
 * Iconos de redes sociales.
 *
 * Van en SVG dentro del componente porque Lucide retiró los iconos de marca en sus
 * versiones recientes, y una letra suelta dentro de un círculo —que es lo que había
 * antes— no se reconoce como la red que representa.
 */
function IconoRed({ red }: { red: "instagram" | "facebook" | "linkedin" | "youtube" }) {
  const trazos: Record<typeof red, string> = {
    instagram:
      "M7 2h10a5 5 0 0 1 5 5v10a5 5 0 0 1-5 5H7a5 5 0 0 1-5-5V7a5 5 0 0 1 5-5Zm0 2a3 3 0 0 0-3 3v10a3 3 0 0 0 3 3h10a3 3 0 0 0 3-3V7a3 3 0 0 0-3-3H7Zm5 3.5a4.5 4.5 0 1 1 0 9 4.5 4.5 0 0 1 0-9Zm0 2a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5ZM17.5 6a1 1 0 1 1 0 2 1 1 0 0 1 0-2Z",
    facebook:
      "M13.5 22v-8h2.7l.4-3.1h-3.1V8.9c0-.9.25-1.5 1.55-1.5h1.65V4.63A22 22 0 0 0 14.3 4.5c-2.4 0-4.05 1.47-4.05 4.16v2.24H7.5V14h2.75v8h3.25Z",
    linkedin:
      "M4.98 3.5a2.5 2.5 0 1 1 0 5 2.5 2.5 0 0 1 0-5ZM3 9h4v12H3V9Zm6 0h3.8v1.65h.05A4.17 4.17 0 0 1 16.6 8.7c4 0 4.75 2.6 4.75 5.98V21h-4v-5.5c0-1.31-.03-3-1.85-3-1.85 0-2.13 1.44-2.13 2.9V21H9V9Z",
    youtube:
      "M21.6 7.2a2.5 2.5 0 0 0-1.76-1.77C18.25 5 12 5 12 5s-6.25 0-7.84.43A2.5 2.5 0 0 0 2.4 7.2C2 8.8 2 12 2 12s0 3.2.4 4.8a2.5 2.5 0 0 0 1.76 1.77C5.75 19 12 19 12 19s6.25 0 7.84-.43a2.5 2.5 0 0 0 1.76-1.77C22 15.2 22 12 22 12s0-3.2-.4-4.8ZM10 15V9l5.2 3-5.2 3Z",
  };

  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false" className="h-4 w-4 fill-current">
      <path d={trazos[red]} />
    </svg>
  );
}
