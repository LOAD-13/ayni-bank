import type { Metadata } from "next";

import { PasoDeSegundoFactorRegistro } from "./PasoDeSegundoFactorRegistro";

export const metadata: Metadata = {
  title: "Elige tu segundo factor",
  description: "Elige cómo proteger tu cuenta y verifica tu contacto para continuar el registro.",
  robots: { index: false, follow: false },
};

/** HU-22 (AYNI-124) · paso 2 de 6 del onboarding: segundo factor y verificación de contacto. */
export default async function SegundoFactorRegistroPage({
  searchParams,
}: {
  searchParams: Promise<{ solicitudId?: string }>;
}) {
  const { solicitudId } = await searchParams;

  return <PasoDeSegundoFactorRegistro solicitudId={solicitudId} />;
}
