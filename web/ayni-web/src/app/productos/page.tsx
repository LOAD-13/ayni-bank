import type { Metadata } from "next";

import { ListaDeProductos, PRODUCTOS } from "@/componentes/landing/SeccionProductos";
import { PaginaPublica, Seccion } from "@/componentes/publico/PaginaPublica";

export const metadata: Metadata = {
  title: "Productos",
  description:
    "Cuenta de ahorro, depósito a plazo, tarjeta de crédito, préstamos, seguros, inversiones y negocios.",
};

const TODOS = [
  ...PRODUCTOS,
  {
    icono: "/pen/landing-c545a0328b79.png",
    titulo: "Inversiones",
    sub: "Fondos mutuos con aportes desde S/ 50 al mes, según tu perfil.",
    accion: "Proyectar",
    destino: "/inversiones",
  },
];

export default function Productos() {
  return (
    <PaginaPublica
      titulo="Todo lo que puedes hacer con Ayni"
      bajada="Empieza con la Cuenta Ayni, sin comisión de mantenimiento, y simula el resto antes de decidir."
    >
      <Seccion titulo="Nuestros productos">
        <ListaDeProductos productos={TODOS} />
      </Seccion>
    </PaginaPublica>
  );
}
