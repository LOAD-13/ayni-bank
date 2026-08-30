import { FileLock2, Lock, ScanFace, ShieldCheck, ShieldQuestion } from "lucide-react";
import Image from "next/image";

const GARANTIAS = [
  {
    Icono: Lock,
    titulo: "Cifrado de extremo a extremo",
    sub: "Tus datos y tus imágenes viajan y se guardan cifrados, con las claves fuera del código.",
  },
  {
    Icono: ShieldQuestion,
    titulo: "Monitoreo antifraude 24/7",
    sub: "Detectamos movimientos raros y te avisamos antes de que pase algo.",
  },
  {
    Icono: FileLock2,
    titulo: "Tus datos no se venden",
    sub: "Nunca compartimos tu información con terceros para publicidad.",
  },
];

const DISTINTIVOS = [
  { Icono: Lock, texto: "Cifrado AES-256", posicion: "top-[18%] left-[2%]" },
  { Icono: ShieldCheck, texto: "Tokenización de tarjeta", posicion: "top-[8%] left-[38%]" },
  { Icono: ScanFace, texto: "Reconocimiento facial", posicion: "bottom-[16%] left-[8%]" },
];

/** Sección «Seguridad», con el escudo 3D y sus distintivos flotantes. */
export function SeccionSeguridad() {
  return (
    <section
      id="seguridad"
      className="relative overflow-hidden bg-azul-900 bg-cover bg-center"
      style={{ backgroundImage: "url('/pen/landing-7ae19e5a05c5.webp')" }}
    >
      <div aria-hidden="true" className="pointer-events-none absolute inset-0">
        <div className="absolute top-[10%] left-[10%] h-[420px] w-[420px] rounded-full bg-azul-600/45 blur-[90px]" />
        <div className="absolute right-[12%] bottom-[6%] h-[320px] w-[320px] rounded-full bg-dorado-500/20 blur-[80px]" />
      </div>

      <div className="relative mx-auto grid max-w-[1440px] items-center gap-12 px-6 py-20 lg:grid-cols-2 lg:px-16 lg:py-28">
        <div className="relative order-2 min-h-[340px] lg:order-1 lg:min-h-[460px]">
          <div
            aria-hidden="true"
            className="absolute top-1/2 left-1/2 h-[360px] w-[360px] -translate-x-1/2 -translate-y-1/2 rounded-full border border-blanco/12"
          />
          <Image
            src="/pen/landing-713a36954b18.png"
            alt=""
            aria-hidden="true"
            width={280}
            height={280}
            className="absolute top-1/2 left-1/2 h-[200px] w-[200px] -translate-x-1/2 -translate-y-1/2 lg:h-[260px] lg:w-[260px]"
          />

          {DISTINTIVOS.map(({ Icono, texto, posicion }) => (
            <p
              key={texto}
              className={`absolute ${posicion} inline-flex items-center gap-2 rounded-full bg-azul-tarjeta/85 px-4 py-2 text-[13px] font-semibold text-blanco backdrop-blur-md`}
            >
              <Icono aria-hidden="true" className="h-3.5 w-3.5 text-oro" />
              {texto}
            </p>
          ))}
        </div>

        <div className="order-1 lg:order-2">
          <p className="flex items-center gap-2.5">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-oro" />
            <span className="text-[12.5px] font-bold tracking-[0.14em] text-dorado-400">
              SEGURIDAD
            </span>
          </p>

          <h2 className="mt-4 max-w-[520px] text-[clamp(1.9rem,3.8vw,48px)] leading-[1.12] font-bold text-blanco">
            Tu plata protegida como si fuera nuestra.
          </h2>

          <ul className="mt-10 flex flex-col">
            {GARANTIAS.map(({ Icono, titulo, sub }, indice) => (
              <li
                key={titulo}
                className={`flex gap-4 py-6 ${indice > 0 ? "border-t border-blanco/10" : ""}`}
              >
                <span
                  aria-hidden="true"
                  className="mt-0.5 inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-[12px] bg-blanco/8 text-oro"
                >
                  <Icono className="h-[18px] w-[18px]" />
                </span>
                <div>
                  <p className="text-[18px] font-bold text-blanco">{titulo}</p>
                  <p className="mt-1.5 text-[14.5px] leading-[1.55] text-texto-tenue">{sub}</p>
                </div>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </section>
  );
}
