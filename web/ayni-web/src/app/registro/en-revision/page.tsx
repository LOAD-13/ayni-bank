import type { Metadata } from "next";
import Link from "next/link";

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

  return (
    <main className="mx-auto flex min-h-screen max-w-xl flex-col justify-center gap-5 px-6 py-16">
      <p className="text-caption font-bold tracking-widest text-dorado-700 uppercase">Ayni Bank</p>

      <h1 className="text-h1 font-bold text-azul-700">
        {diferida ? "Tu verificación continuará en breve" : "Un operador revisará tu caso"}
      </h1>

      <p className="text-body text-gris-700">
        {diferida
          ? "Recibimos tus fotos, pero no pudimos terminar de revisarlas en este momento. No tienes que hacer nada más: seguiremos con tu verificación y te avisaremos por correo."
          : "No pudimos verificar tu DNI automáticamente. Una persona de nuestro equipo revisará tus fotos y tus datos, y te escribirá por correo con el resultado."}
      </p>

      <div className="mt-2">
        <Link
          href="/"
          className="inline-flex min-h-[44px] items-center justify-center rounded-full border border-gris-300 px-6 text-body font-semibold text-gris-700 hover:bg-gris-100"
        >
          Volver al inicio
        </Link>
      </div>
    </main>
  );
}
