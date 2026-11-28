import type { Metadata } from "next";

import { NOTA_REFERENCIAL, PaginaDeProducto } from "@/componentes/publico/PaginaDeProducto";
import { SimuladorDeCuota } from "@/componentes/publico/Simuladores";

export const metadata: Metadata = {
  title: "Tarjeta de crédito",
  description:
    "Sin membresía el primer año y control total desde tu banca: congélala, fija tus límites y mira cada compra al instante.",
};

const BENEFICIOS = [
  ["Sin membresía", "El primer año no pagas membresía, uses o no la tarjeta."],
  [
    "Control total",
    "Congélala con un toque, decide si compra por internet y fija tu límite diario.",
  ],
  [
    "Cuotas claras",
    "Antes de comprar en cuotas ves cuánto pagarás cada mes y cuánto en intereses.",
  ],
  ["Segura", "El número completo solo se muestra tras confirmar con tu segundo factor."],
] as const;

const REQUISITOS = [
  "Tener una Cuenta Ayni Soles activa.",
  "Ser mayor de edad y residir en el Perú.",
  "Ingresos que respalden la línea de crédito.",
];

const PREGUNTAS = [
  {
    pregunta: "¿Qué es la TEA de compras?",
    respuesta:
      "Es la tasa con la que se calculan los intereses de lo que compras en cuotas. El simulador te muestra la cuota y el total de intereses antes de decidir.",
  },
  {
    pregunta: "¿Puedo congelarla si la pierdo?",
    respuesta: "Sí, desde tu banca y al instante, igual que tu tarjeta de débito.",
  },
  {
    pregunta: "¿Cuánto cuesta después del primer año?",
    respuesta:
      "Las condiciones vigentes estarán siempre en el tarifario antes de que se cobre nada.",
  },
];

export default function Pagina() {
  return (
    <PaginaDeProducto
      titulo="Tarjeta de crédito"
      bajada="Sin membresía el primer año y control total desde tu banca: congélala, fija tus límites y mira cada compra al instante."
      simulador={
        <SimuladorDeCuota
          titulo="Simula una compra en cuotas"
          tea={59.9}
          montoMin={100}
          montoMax={10_000}
          montoInicial={1500}
          plazos={[3, 6, 12, 18, 24, 36]}
          etiquetaMonto="Valor de la compra"
        />
      }
      beneficios={BENEFICIOS}
      requisitos={REQUISITOS}
      preguntas={PREGUNTAS}
      nota={NOTA_REFERENCIAL}
    />
  );
}
