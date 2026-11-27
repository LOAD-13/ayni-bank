import type { Metadata } from "next";

import { NOTA_REFERENCIAL, PaginaDeProducto } from "@/componentes/publico/PaginaDeProducto";
import { SimuladorDeCuota } from "@/componentes/publico/Simuladores";

export const metadata: Metadata = {
  title: "Préstamo personal",
  description:
    "Una cuota fija que conoces desde el principio. Calcula cuánto pagarías antes de decidir.",
};

const BENEFICIOS = [
  ["Cuota fija", "Pagas lo mismo cada mes: sabes desde el primer día cuánto y hasta cuándo."],
  ["Sin letra chica", "La cuota, los intereses y la tasa se ven completos antes de firmar."],
  ["Prepaga sin penalidad", "Adelanta cuotas o cancela antes y pagas menos intereses."],
  ["100 % digital", "Lo cotizas y lo sigues desde tu banca, sin papeles."],
] as const;

const REQUISITOS = [
  "Tener una Cuenta Ayni Soles activa.",
  "Ser mayor de edad con DNI vigente.",
  "Ingresos que respalden la cuota que elijas.",
];

const PREGUNTAS = [
  {
    pregunta: "¿Cómo se calcula la cuota?",
    respuesta:
      "Con el método francés: una cuota fija que incluye capital e intereses, calculados con la tasa efectiva mensual que sale de la TEA.",
  },
  {
    pregunta: "¿Qué es la TCEA?",
    respuesta:
      "La tasa de costo efectivo anual suma a la TEA los seguros y gastos del préstamo. Es la que conviene comparar entre bancos.",
  },
  {
    pregunta: "¿Puedo pagar antes?",
    respuesta: "Sí. Al adelantar pagos se reducen los intereses que quedan por pagar.",
  },
];

export default function Pagina() {
  return (
    <PaginaDeProducto
      titulo="Préstamo personal"
      bajada="Una cuota fija que conoces desde el principio. Calcula cuánto pagarías antes de decidir."
      simulador={
        <SimuladorDeCuota
          titulo="Cotiza tu préstamo"
          tea={24.9}
          montoMin={1000}
          montoMax={50_000}
          montoInicial={10_000}
          plazos={[6, 12, 24, 36, 48, 60]}
        />
      }
      beneficios={BENEFICIOS}
      requisitos={REQUISITOS}
      preguntas={PREGUNTAS}
      nota={NOTA_REFERENCIAL}
    />
  );
}
