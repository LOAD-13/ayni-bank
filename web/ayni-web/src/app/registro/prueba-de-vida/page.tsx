import type { Metadata } from "next";

import { PasoDeCapturaDeSelfie } from "@/componentes/kyc/PasoDeCapturaDeSelfie";

export const metadata: Metadata = {
  title: "Prueba de vida",
  description: "Verifica tu identidad con una selfie para completar tu registro.",
  robots: { index: false, follow: false },
};

/**
 * HU-03 · paso 4 de 5 del onboarding: consentimiento biométrico y selfie (AYNI-14).
 *
 * Al terminar va a `/pendiente`: enlazar la cuenta abierta requiere el `usuarioId`, que
 * hoy no viaja hasta aquí (solo el `solicitudId`) y traerlo es AYNI-13/HU-07, no esta
 * historia. Mismo criterio que en `dni-reverso/page.tsx`: una pantalla que dice la verdad
 * en vez de una ruta que no existe.
 */
export default async function PruebaDeVida({
  searchParams,
}: {
  searchParams: Promise<{ solicitudId?: string }>;
}) {
  const { solicitudId } = await searchParams;

  return (
    <PasoDeCapturaDeSelfie solicitudId={solicitudId} pasoActual={4} siguienteHref="/pendiente" />
  );
}
