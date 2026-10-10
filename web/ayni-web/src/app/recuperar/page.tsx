import type { Metadata } from "next";

import { FormularioDeRecuperacion } from "@/componentes/ingreso/FormularioDeRecuperacion";
import { MarcoDeIngreso } from "@/componentes/ingreso/MarcoDeIngreso";

export const metadata: Metadata = {
  title: "Recupera tu contraseña",
  description: "Pide un enlace para crear una contraseña nueva en Ayni Bank.",
  robots: { index: false, follow: false },
};

/** HU-21 · Pedir el enlace de recuperación. */
export default function PaginaDeRecuperacion() {
  return (
    <MarcoDeIngreso>
      <FormularioDeRecuperacion />
    </MarcoDeIngreso>
  );
}
