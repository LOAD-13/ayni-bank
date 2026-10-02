import type { Metadata } from "next";

import { PasoDeConfirmacionDeDatos } from "@/componentes/kyc/PasoDeConfirmacionDeDatos";

export const metadata: Metadata = {
  title: "Confirma los datos de tu DNI",
  description: "Revisa y corrige los datos que leímos de tu DNI antes de continuar.",
  robots: { index: false, follow: false },
};

/** HU-02 · tras el reverso del DNI: el titular confirma o corrige lo que leyó el OCR. */
export default async function ConfirmarDatos({
  searchParams,
}: {
  searchParams: Promise<{ solicitudId?: string }>;
}) {
  const { solicitudId } = await searchParams;

  return <PasoDeConfirmacionDeDatos solicitudId={solicitudId} pasoActual={3} />;
}
