import { Landmark, Play, ScanFace, ShieldCheck } from "lucide-react";
import Image from "next/image";
import Link from "next/link";

import { BarraDeNavegacion } from "./BarraDeNavegacion";

/**
 * Hero de la landing, según el diseño aprobado en pen.dev.
 *
 * **Cómo se traduce el diseño.** El prototipo coloca cada pieza con coordenadas absolutas
 * sobre un lienzo de 1440 px. Aquí la composición se expresa como una rejilla de dos
 * columnas: texto a la izquierda, fotografía con sus distintivos a la derecha. A partir
 * de `lg` el resultado es el mismo que el diseño; por debajo, las dos columnas se apilan
 * en lugar de superponerse.
 *
 * Los distintivos flotantes —Cero comisiones, la tarjeta de saldo, el chip de la TREA—
 * sí van en absoluto, pero **relativos a la fotografía**, no al lienzo. Así se mantienen
 * en su sitio sea cual sea el tamaño de la imagen.
 */
export function Hero() {
  return (
    // El fondo es la imagen del propio prototipo, no un degradado recreado a mano:
    // reproducirlo con `linear-gradient` nunca daría exactamente el mismo tono.
    <section
      className="relative overflow-hidden bg-azul-700 bg-cover bg-center"
      style={{ backgroundImage: "url('/pen/landing-0d7618605d36.webp')" }}
    >
      {/* Resplandores y anillos del fondo. Decorativos: no se anuncian y se ocultan en
          pantallas pequeñas, donde solo empujarían el contenido. */}
      <div aria-hidden="true" className="pointer-events-none absolute inset-0 hidden lg:block">
        <div className="absolute top-[-260px] left-[57%] h-[640px] w-[760px] rounded-full bg-dorado-500/35 blur-[80px]" />
        <div className="absolute top-[420px] left-[-18%] h-[660px] w-[760px] rounded-full bg-azul-500/25 blur-[85px]" />
        <div className="absolute top-[120px] left-[48.6%] h-[700px] w-[700px] rounded-full border-[1.5px] border-azul-300/20" />
        <div className="absolute top-[220px] left-[55.5%] h-[500px] w-[500px] rounded-full border-[1.5px] border-oro/18" />
      </div>

      <BarraDeNavegacion />

      <div className="relative mx-auto grid max-w-[1440px] grid-cols-1 items-center gap-12 px-6 pt-[136px] pb-16 lg:grid-cols-[660px_1fr] lg:gap-8 lg:px-16 lg:pt-[186px] lg:pb-[120px]">
        <div className="flex flex-col gap-[26px]">
          <p className="inline-flex w-fit items-center gap-2.5 rounded-full bg-blanco/10 p-2 pr-5">
            <span className="rounded-full bg-oro px-2.5 py-1 text-[11px] font-bold text-azul-900">
              NUEVO
            </span>
            <span className="text-[13.5px] font-medium text-texto-pildora">
              Cuenta Ayni · 4.50% TREA con devengo diario
            </span>
          </p>

          <h1 className="text-[clamp(2.75rem,6vw,78px)] leading-[1.06] font-bold text-blanco">
            Tu dinero crece
            <span className="block text-oro">todos los días.</span>
          </h1>

          <p className="max-w-[520px] text-[17.5px] leading-[1.6] text-texto-bajada">
            Banca 100% digital para el Perú. Sin comisión de mantenimiento, sin letras chicas y con
            intereses que se abonan cada día. Abre tu cuenta en 5 minutos con tu DNI.
          </p>

          <div className="flex flex-col gap-4 sm:flex-row">
            <Link
              href="/registro"
              className="inline-flex min-h-[44px] items-center justify-center gap-2.5 rounded-full bg-gradient-to-r from-oro-claro to-dorado-500 px-7 py-4 text-[16px] font-bold text-azul-900"
            >
              Abrir mi cuenta gratis
              <span aria-hidden="true">→</span>
            </Link>
            <Link
              href="/pendiente"
              className="inline-flex min-h-[44px] items-center justify-center gap-2.5 rounded-full bg-blanco/10 px-7 py-4 text-[16px] font-semibold text-blanco hover:bg-blanco/15"
            >
              <Play aria-hidden="true" className="h-4 w-4" />
              Ver cómo funciona
            </Link>
          </div>

          <hr className="max-w-[520px] border-blanco/12" />

          <ul className="flex flex-wrap gap-x-7 gap-y-2.5 text-[13.5px] font-medium text-texto-tenue">
            {[
              { Icono: ShieldCheck, texto: "Conforme a la normativa SBS" },
              { Icono: Landmark, texto: "Cifrado extremo a extremo" },
              { Icono: ScanFace, texto: "Ingreso biométrico" },
            ].map(({ Icono, texto }) => (
              <li key={texto} className="inline-flex items-center gap-2">
                <Icono aria-hidden="true" className="h-4 w-4" />
                {texto}
              </li>
            ))}
          </ul>
        </div>

        <div className="relative mx-auto w-full max-w-[560px] lg:mx-0 lg:max-w-none">
          {/* Flecha 3D del diseño. Decorativa: se oculta en pantallas estrechas, donde
              se solaparía con la fotografía en lugar de acompañarla. */}
          <Image
            src="/pen/landing-543cde9cf013.png"
            alt=""
            aria-hidden="true"
            width={160}
            height={160}
            className="absolute top-[-70px] right-[-40px] z-10 hidden h-[160px] w-[160px] rotate-[8deg] lg:block"
          />

          <div className="relative aspect-[500/620] w-full overflow-hidden rounded-t-[250px] rounded-b-[24px] lg:ml-auto lg:w-[500px]">
            <Image
              src="/pen/landing-db13439b18db.jpg"
              alt="Una clienta de Ayni Bank consultando su cuenta desde el celular"
              fill
              sizes="(max-width: 1024px) 90vw, 500px"
              priority
              className="object-cover"
            />
            {/* Velo inferior: sin él, los distintivos oscuros pierden contraste sobre las
                zonas claras de la fotografía. */}
            <div
              aria-hidden="true"
              className="absolute inset-0 bg-gradient-to-t from-azul-900/90 to-transparent"
            />
          </div>

          <DistintivoFlotante
            imagen="/pen/landing-82c2a7daf1ae.png"
            titulo="Cero comisiones"
            subtitulo="Mantenimiento S/ 0.00"
            className="top-2 left-0 lg:top-4 lg:left-[-6%]"
          />

          <DistintivoFlotante
            imagen="/pen/landing-3406b3a6ea01.png"
            titulo="Metas de ahorro"
            subtitulo="Aparta y automatiza"
            className="bottom-[22%] left-[-4%] rotate-[-4deg] lg:bottom-[18%] lg:left-[-10%]"
          />

          <div className="absolute bottom-[38%] left-[8%] w-[290px] max-w-[80%] rounded-[18px] bg-azul-tarjeta/85 p-5 backdrop-blur-md">
            <p className="text-[12px] font-semibold text-azul-300">Cuenta Ayni Soles</p>
            <p className="cifra mt-1 text-[30px] leading-none font-bold text-blanco">
              S/ 12,480.65
            </p>
            <p className="mt-2 text-[13px] font-semibold text-verde-rendimiento">
              + S/ 1.58 hoy · devengo diario
            </p>
          </div>

          <div className="absolute right-0 bottom-[6%] rounded-[16px] bg-oro px-5 py-3.5 lg:right-[-4%]">
            <p className="cifra text-[28px] leading-none font-bold text-azul-900">4.50%</p>
            <p className="mt-1 text-[12px] font-semibold text-dorado-800">TREA en soles</p>
          </div>
        </div>
      </div>
    </section>
  );
}

function DistintivoFlotante({
  imagen,
  titulo,
  subtitulo,
  className,
}: {
  imagen: string;
  titulo: string;
  subtitulo: string;
  className: string;
}) {
  return (
    <div
      className={`absolute flex items-center gap-3 rounded-full bg-azul-tarjeta/80 py-2.5 pr-5 pl-2.5 backdrop-blur-md ${className}`}
    >
      <Image src={imagen} alt="" aria-hidden="true" width={40} height={40} className="h-9 w-9" />
      <div>
        <p className="text-[14px] font-bold text-blanco">{titulo}</p>
        <p className="text-[11.5px] font-medium text-azul-300">{subtitulo}</p>
      </div>
    </div>
  );
}
