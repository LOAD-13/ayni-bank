import type { Metadata } from "next";

import { FormularioDeContrasenaNueva } from "@/componentes/ingreso/FormularioDeContrasenaNueva";
import { MarcoDeIngreso } from "@/componentes/ingreso/MarcoDeIngreso";

export const metadata: Metadata = {
  title: "Crea tu contraseña nueva",
  description: "Fija una contraseña nueva con el enlace que te enviamos.",
  robots: { index: false, follow: false },
  // El token va en el fragmento y no viaja en el Referer, pero se corta igual por si
  // algún día el enlace cambia de forma.
  referrer: "no-referrer",
};

/** HU-21 · Fijar la contraseña nueva con el enlace del correo. */
export default function PaginaDeContrasenaNueva() {
  return (
    <MarcoDeIngreso>
      <FormularioDeContrasenaNueva />
    </MarcoDeIngreso>
  );
}
