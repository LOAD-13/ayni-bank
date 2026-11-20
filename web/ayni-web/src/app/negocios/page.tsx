import { FileText, QrCode, Store, TrendingUp } from "lucide-react";
import type { Metadata } from "next";

import { NOTA_REFERENCIAL, PaginaDeProducto } from "@/componentes/publico/PaginaDeProducto";
import { Seccion } from "@/componentes/publico/PaginaPublica";
import { SimuladorDeCuota, SimuladorDeNegocio } from "@/componentes/publico/Simuladores";

export const metadata: Metadata = {
  title: "Negocios",
  description:
    "Cuenta negocio, cobros con QR, facturación electrónica y préstamo pyme para emprendedores.",
};

const BENEFICIOS = [
  ["Sin mantenimiento", "Tu cuenta negocio tampoco cobra comisión de mantenimiento."],
  ["Cobra al instante", "Tus clientes pagan con QR desde cualquier banco o billetera."],
  ["Factura sin salir", "Emite facturas y boletas electrónicas con el IGV calculado."],
  ["Caja en orden", "Separa el dinero del negocio del personal y mira tus ventas por día."],
] as const;

const SERVICIOS = [
  {
    id: "cuenta-negocio",
    Icono: Store,
    titulo: "Cuenta negocio",
    texto:
      "Una cuenta para tu emprendimiento, separada de la personal, con las mismas reglas de Ayni: sin mantenimiento y con intereses diarios sobre el saldo.",
  },
  {
    id: "cobros-con-qr",
    Icono: QrCode,
    titulo: "Cobros con QR",
    texto:
      "Muestra tu código y cobra desde cualquier banco o billetera. El dinero llega a tu cuenta negocio y cada cobro aparece en tus movimientos.",
  },
  {
    id: "facturacion",
    Icono: FileText,
    titulo: "Facturación",
    texto:
      "Facturas y boletas electrónicas con el IGV de 18 % calculado y enviadas a tu cliente por correo, desde la misma banca.",
  },
  {
    id: "prestamo-pyme",
    Icono: TrendingUp,
    titulo: "Préstamo pyme",
    texto:
      "Capital de trabajo con cuota fija para comprar mercadería o equipos. Simula tu cuota aquí abajo.",
  },
];

const REQUISITOS = [
  "RUC activo y habido, de persona natural con negocio o de empresa.",
  "Tener una Cuenta Ayni Soles a nombre del titular o representante legal.",
  "Para el préstamo pyme, al menos seis meses de ventas.",
];

const PREGUNTAS = [
  {
    pregunta: "¿Puedo usar mi cuenta personal para el negocio?",
    respuesta:
      "Puedes, pero separar las cuentas te ayuda a saber cuánto gana de verdad tu negocio y a declarar tus impuestos sin mezclar gastos.",
  },
  {
    pregunta: "¿Cuánto cuesta cobrar con QR?",
    respuesta:
      "Una comisión por cada cobro, que el simulador de arriba te muestra sobre tus ventas del mes. No hay alquiler de equipos.",
  },
  {
    pregunta: "¿Cómo se calcula el IGV de una factura?",
    respuesta:
      "Al valor de venta se le suma el 18 %. En una venta de S/ 118.00, S/ 100.00 son el valor y S/ 18.00 el IGV.",
  },
];

export default function Negocios() {
  return (
    <PaginaDeProducto
      titulo="Tu negocio, en un solo lugar."
      bajada="Cobra con QR, factura y controla tu caja desde la misma banca. Sin agencias y sin comisión de mantenimiento."
      simulador={<SimuladorDeNegocio comisionQr={1.5} />}
      beneficios={BENEFICIOS}
      requisitos={REQUISITOS}
      preguntas={PREGUNTAS}
      nota={NOTA_REFERENCIAL}
    >
      <Seccion titulo="Lo que incluye" fondo="blanco">
        <ul className="grid gap-5 md:grid-cols-2">
          {SERVICIOS.map(({ id, Icono, titulo, texto }) => (
            <li key={id} id={id} className="scroll-mt-6 rounded-[20px] bg-azul-050 p-7">
              <span
                aria-hidden="true"
                className="flex h-12 w-12 items-center justify-center rounded-xl bg-azul-800 text-oro"
              >
                <Icono className="h-6 w-6" />
              </span>
              <h3 className="mt-5 text-h3 font-bold text-azul-900">{titulo}</h3>
              <p className="mt-2 text-body leading-[1.6] text-gris-700">{texto}</p>
            </li>
          ))}
        </ul>
        <div className="mt-10 flex justify-center lg:justify-start">
          <SimuladorDeCuota
            titulo="Cotiza tu préstamo pyme"
            tea={21.9}
            montoMin={5000}
            montoMax={150_000}
            montoInicial={30_000}
            plazos={[6, 12, 24, 36, 48]}
          />
        </div>
      </Seccion>
    </PaginaDeProducto>
  );
}
