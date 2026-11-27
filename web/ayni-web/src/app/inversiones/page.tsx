import type { Metadata } from "next";

import { NOTA_REFERENCIAL, PaginaDeProducto } from "@/componentes/publico/PaginaDeProducto";
import { SimuladorDeInversion } from "@/componentes/publico/Simuladores";

export const metadata: Metadata = {
  title: "Inversiones",
  description:
    "Fondos mutuos con aportes desde S/ 50 al mes. Proyecta cuánto podrías tener según tu perfil.",
};

const BENEFICIOS = [
  ["Desde S/ 50 al mes", "Invierte de a poco, con aportes automáticos desde tu Cuenta Ayni."],
  ["Según tu perfil", "Conservador, moderado o agresivo: tú eliges cuánto riesgo aceptas."],
  ["Rescata cuando quieras", "Tu dinero no queda atado a un plazo fijo."],
  ["Todo a la vista", "El valor de tus cuotas y lo que llevas aportado, en tu banca."],
] as const;

const REQUISITOS = [
  "Tener una Cuenta Ayni Soles activa.",
  "Completar el test de perfil de inversionista.",
  "Un aporte inicial desde S/ 1 000.",
];

const PREGUNTAS = [
  {
    pregunta: "¿La rentabilidad está garantizada?",
    respuesta:
      "No. El valor de un fondo puede subir o bajar; las rentabilidades del simulador son supuestos para entender cómo funciona el ahorro a largo plazo, no promesas.",
  },
  {
    pregunta: "¿Qué perfil me conviene?",
    respuesta:
      "Depende de cuánto tiempo puedes dejar el dinero y de cuánto te afectaría que baje. Si lo necesitarás pronto, un perfil conservador o un depósito a plazo es más prudente.",
  },
  {
    pregunta: "¿Cuál es la diferencia con un depósito a plazo?",
    respuesta:
      "El depósito a plazo tiene una tasa fija conocida desde el inicio. Un fondo puede rendir más, pero su resultado no está asegurado.",
  },
];

const NOTA =
  "Las rentabilidades del simulador son supuestos anuales, no resultados pasados ni garantizados. " +
  NOTA_REFERENCIAL;

export default function Inversiones() {
  return (
    <PaginaDeProducto
      titulo="Haz crecer lo que ahorras."
      bajada="Fondos mutuos con aportes mensuales desde S/ 50. Proyecta cuánto podrías tener según tu perfil y tu horizonte."
      simulador={
        <SimuladorDeInversion
          perfiles={[
            { nombre: "Conservador", rentabilidad: 4 },
            { nombre: "Moderado", rentabilidad: 7 },
            { nombre: "Agresivo", rentabilidad: 10 },
          ]}
        />
      }
      beneficios={BENEFICIOS}
      requisitos={REQUISITOS}
      preguntas={PREGUNTAS}
      nota={NOTA}
    />
  );
}
