import type { Metadata } from "next";
import type { ReactNode } from "react";

import { MarcoDeBackoffice } from "@/componentes/backoffice/MarcoDeBackoffice";
import { SesionDeBanca } from "@/componentes/banca/SesionDeBanca";

export const metadata: Metadata = {
  title: "Back-office",
  // El back-office del banco no tiene nada que hacer en un buscador.
  robots: { index: false, follow: false },
};

export default function LayoutDeBackoffice({ children }: { children: ReactNode }) {
  return (
    <SesionDeBanca>
      <MarcoDeBackoffice>{children}</MarcoDeBackoffice>
    </SesionDeBanca>
  );
}
