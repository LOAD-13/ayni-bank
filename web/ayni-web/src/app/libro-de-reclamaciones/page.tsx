import type { Metadata } from "next";

import { PaginaPublica } from "@/componentes/publico/PaginaPublica";

import { FormularioDeReclamacion } from "./FormularioDeReclamacion";

export const metadata: Metadata = {
  title: "Libro de reclamaciones",
  description:
    "Registra un reclamo o una queja sobre Ayni Bank y recibe tu código y la fecha límite de respuesta.",
};

/**
 * Libro de reclamaciones virtual (Ley N.º 29571). Lo puede usar cualquier persona, sea o no
 * cliente: no pide sesión. La hoja se guarda en core-banking con su número correlativo.
 */
export default function LibroDeReclamaciones() {
  return (
    <PaginaPublica
      titulo="Libro de reclamaciones"
      bajada="Si algo no salió como debía, regístralo aquí. Te damos un código al instante y te respondemos por correo en un plazo máximo de 15 días hábiles."
      cierre={false}
    >
      <div className="mx-auto max-w-[880px] px-6 py-14 lg:py-16">
        <FormularioDeReclamacion />
        <p className="mt-8 max-w-[70ch] text-small leading-[1.6] text-gris-700">
          Reclamo: disconformidad con un producto o servicio. Queja: malestar con la atención, que
          no tiene que ver con el producto o servicio. Registrar una hoja no te impide acudir al
          INDECOPI. Tus datos se usan solo para atender esta hoja (Ley N.º 29733).
        </p>
      </div>
    </PaginaPublica>
  );
}
