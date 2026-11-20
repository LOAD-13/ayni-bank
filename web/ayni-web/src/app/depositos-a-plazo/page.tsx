import type { Metadata } from "next";

import { NOTA_REFERENCIAL, PaginaDeProducto } from "@/componentes/publico/PaginaDeProducto";
import { SimuladorDeDeposito } from "@/componentes/publico/Simuladores";

export const metadata: Metadata = {
  title: "Depósito a plazo",
  description:
    "Elige el plazo y asegura tu tasa desde el primer día. Tu dinero gana más mientras no lo necesitas.",
};

const BENEFICIOS = [
  ["Tasa fija", "La tasa con la que abres el depósito es la que recibes hasta el final del plazo."],
  ["Desde S/ 500", "Empieza con poco y suma el depósito a lo que ya rinde tu Cuenta Ayni."],
  ["Sin comisiones", "No cobramos por abrir, mantener ni cobrar tu depósito al vencimiento."],
  ["Todo en línea", "Lo abres y lo sigues desde tu banca, sin ir a una agencia."],
] as const;

const REQUISITOS = [
  "Tener una Cuenta Ayni Soles activa.",
  "Un monto desde S/ 500.",
  "Elegir el plazo: 90, 180, 360 o 720 días.",
];

const PREGUNTAS = [
  {
    pregunta: "¿Puedo retirar antes del plazo?",
    respuesta:
      "Un depósito a plazo está pensado para no tocarse hasta el vencimiento. Si necesitas el dinero disponible todos los días, tu Cuenta Ayni ya rinde con devengo diario.",
  },
  {
    pregunta: "¿Qué pasa al vencer?",
    respuesta:
      "El capital y los intereses vuelven a tu Cuenta Ayni Soles, donde siguen rindiendo cada día.",
  },
  {
    pregunta: "¿Cómo se calcula lo que gano?",
    respuesta:
      "Con la tasa efectiva anual y el año comercial de 360 días: monto × ((1 + TEA)^(días/360) − 1).",
  },
];

export default function Pagina() {
  return (
    <PaginaDeProducto
      titulo="Depósito a plazo"
      bajada="Elige el plazo y asegura tu tasa desde el primer día. Tu dinero gana más mientras no lo necesitas."
      simulador={
        <SimuladorDeDeposito
          tasas={[
            { dias: 90, tea: 4.8 },
            { dias: 180, tea: 5.2 },
            { dias: 360, tea: 5.6 },
            { dias: 720, tea: 5.9 },
          ]}
        />
      }
      beneficios={BENEFICIOS}
      requisitos={REQUISITOS}
      preguntas={PREGUNTAS}
      nota={NOTA_REFERENCIAL}
    />
  );
}
