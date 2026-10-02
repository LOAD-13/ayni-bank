import type { Metadata } from "next";
import type { ReactNode } from "react";

import { MarcoDeBanca } from "@/componentes/banca/MarcoDeBanca";
import { SesionDeBanca } from "@/componentes/banca/SesionDeBanca";

export const metadata: Metadata = {
  title: "Tu banca",
  // La banca de un cliente no tiene nada que hacer en un buscador.
  robots: { index: false, follow: false },
};

export default function LayoutDeBanca({ children }: { children: ReactNode }) {
  return (
    <SesionDeBanca>
      <MarcoDeBanca>{children}</MarcoDeBanca>
    </SesionDeBanca>
  );
}
