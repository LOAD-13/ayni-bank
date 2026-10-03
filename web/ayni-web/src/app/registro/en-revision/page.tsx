import type { Metadata } from "next";
import { Clock, Mail, SearchCheck, ShieldCheck, UserCheck } from "lucide-react";
import Link from "next/link";

import { LogotipoAyni } from "@/componentes/LogotipoAyni";
import { PasosDelOnboarding } from "@/componentes/onboarding/PasosDelOnboarding";

export const metadata: Metadata = {
  title: "Estamos revisando tu identidad",
  description: "Tu verificación de identidad continúa; te avisaremos por correo.",
  robots: { index: false, follow: false },
};

/**
 * HU-02 · escenarios 4 y 5: la solicitud quedó en revisión manual.
 *
 * Dos textos para dos situaciones que la persona debe entender distinto: si se agotaron los
 * intentos o los datos no cuadran, un operador revisará el caso; si kyc-service no respondió,
 * no hizo nada mal y la verificación sigue sola. En ningún caso ve un error técnico.
 */
export default async function EnRevision({
  searchParams,
}: {
  searchParams: Promise<{ motivo?: string }>;
}) {
  const { motivo } = await searchParams;
  const diferida = motivo === "diferida";
  const cotejo = motivo === "cotejo";

  return (
    <div className="min-h-screen bg-azul-050">
      <header className="border-b border-gris-300 bg-blanco">
        <div className="mx-auto flex max-w-[1440px] items-center justify-between gap-4 px-6 py-5 lg:px-16">
          <Link href="/">
            <LogotipoAyni altura={16} tono="oscuro" />
          </Link>
          <span className="text-[13.5px] text-gris-500">Verificación en curso</span>
        </div>
      </header>

      <div className="mx-auto w-full max-w-[1440px] px-6 pt-9 lg:px-16">
        <PasosDelOnboarding actual={5} />
      </div>

      <main className="mx-auto w-full max-w-[920px] px-5 py-9 sm:px-6">
        <div className="flex flex-col gap-7 rounded-[20px] border border-gris-300 bg-blanco p-6 shadow-sm sm:p-10">
          <div className="flex flex-col items-center gap-4 text-center">
            <span
              aria-hidden="true"
              className="flex h-20 w-20 items-center justify-center rounded-full border-2 border-dorado-500 bg-dorado-100 text-dorado-700"
            >
              <SearchCheck className="h-9 w-9" />
            </span>
            <h1 className="text-[26px] font-bold leading-tight text-azul-800 sm:text-[32px]">
              {cotejo
                ? "Recibimos tu selfie"
                : diferida
                  ? "Tu verificación continuará en breve"
                  : "Tu verificación pasó a revisión de una persona"}
            </h1>
            <p className="max-w-[640px] text-[15px] leading-relaxed text-gris-700">
              {cotejo
                ? "Tu DNI ya está verificado. Un analista de Ayni compara ahora tu selfie con la foto de tu DNI y, si todo cuadra, abre tu cuenta. Te avisamos por correo."
                : diferida
                  ? "Recibimos tus fotos, pero no pudimos terminar de revisarlas en este momento. No tienes que hacer nada más: seguiremos con tu verificación y te avisaremos por correo."
                  : "La comprobación automática no fue concluyente esta vez. Suele pasar por reflejos, poca luz o porque el documento tiene unos años. No es un rechazo y no hiciste nada mal: un analista de Ayni revisará tu solicitud."}
            </p>
          </div>

          <div className="flex items-start gap-4 rounded-[16px] border border-azul-200 bg-azul-050 p-5">
            <Clock aria-hidden="true" className="mt-0.5 h-5 w-5 shrink-0 text-azul-600" />
            <div>
              <p className="text-[15px] font-semibold text-azul-800">
                Tiempo estimado de respuesta
              </p>
              <p className="text-[14px] text-gris-700">
                Te avisamos por correo en cuanto haya resultado. No necesitas volver a empezar.
              </p>
            </div>
          </div>

          <section>
            <h2 className="mb-4 text-[18px] font-bold text-azul-800">Qué pasa ahora</h2>
            <ol className="flex flex-col gap-4">
              {PASOS_SIGUIENTES.map(({ Icono, titulo, texto }, i) => (
                <li key={titulo} className="flex items-start gap-4">
                  <span
                    aria-hidden="true"
                    className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-azul-100 text-azul-700"
                  >
                    <Icono className="h-5 w-5" />
                  </span>
                  <span>
                    <span className="block text-[15px] font-semibold text-gris-900">
                      {i + 1}. {titulo}
                    </span>
                    <span className="block text-[14px] text-gris-700">{texto}</span>
                  </span>
                </li>
              ))}
            </ol>
          </section>

          <div className="flex flex-col items-center gap-3">
            <Link
              href="/"
              className="inline-flex min-h-[44px] items-center justify-center rounded-full bg-azul-700 px-7 text-[15px] font-bold text-blanco hover:bg-azul-800"
            >
              Volver al inicio
            </Link>
            <p className="flex items-center gap-2 text-[13px] text-gris-500">
              <ShieldCheck aria-hidden="true" className="h-4 w-4" />
              Puedes cerrar esta página: tu progreso queda guardado y el enlace del correo te trae
              de vuelta.
            </p>
          </div>
        </div>
      </main>

      <footer className="px-5 pb-10 text-center text-[12px] text-gris-500">
        Ayni Bank · Banca 100 % digital para personas naturales en Perú. Diseñado conforme al marco
        normativo de la SBS.
      </footer>
    </div>
  );
}

const PASOS_SIGUIENTES = [
  {
    Icono: UserCheck,
    titulo: "Un analista revisa tu solicitud",
    texto: "Compara tu documento y tu prueba de vida a mano. Nadie más ve tus imágenes.",
  },
  {
    Icono: Mail,
    titulo: "Te escribimos con el resultado",
    texto: "Al correo que registraste. Si hace falta algo más, te lo pedimos ahí.",
  },
  {
    Icono: ShieldCheck,
    titulo: "Si todo cuadra, abrimos tu cuenta",
    texto: "Recibirás el número de cuenta y el CCI, y podrás entrar a tu banca.",
  },
];
