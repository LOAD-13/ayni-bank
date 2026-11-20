import type { Metadata } from "next";

import { NOTA_REFERENCIAL, PaginaDeProducto } from "@/componentes/publico/PaginaDeProducto";
import { SimuladorDeSeguros } from "@/componentes/publico/Simuladores";

export const metadata: Metadata = {
  title: "Seguros simples",
  description:
    "Protege tu tarjeta, tu celular y a tu familia con seguros que entiendes en un minuto.",
};

const PLANES = [
  {
    nombre: "Protección de tarjeta",
    prima: 4.9,
    cubre: "Compras no reconocidas y robo de tu tarjeta",
  },
  { nombre: "Celular protegido", prima: 9.9, cubre: "Robo o daño accidental de tu celular" },
  { nombre: "Vida y familia", prima: 14.9, cubre: "Un monto para tu familia si faltas" },
];

const BENEFICIOS = [
  [
    "En simple",
    "Cada seguro cubre una cosa y lo dice en una línea. Sin pólizas de veinte páginas.",
  ],
  ["Mes a mes", "Pagas una prima mensual fija y lo cancelas cuando quieras."],
  ["Con tu cuenta", "La prima se carga a tu Cuenta Ayni y la ves en tus movimientos."],
  ["Sin sorpresas", "La prima no sube mientras el seguro esté vigente."],
] as const;

const REQUISITOS = [
  "Tener una Cuenta Ayni Soles activa.",
  "Ser mayor de edad.",
  "Para «Celular protegido», el IMEI de tu equipo.",
];

const PREGUNTAS = [
  {
    pregunta: "¿Puedo cancelar un seguro?",
    respuesta: "Sí, cuando quieras. Dejas de pagar desde el mes siguiente.",
  },
  {
    pregunta: "¿Qué no cubre «Protección de tarjeta»?",
    respuesta:
      "Las operaciones que tú autorizaste con tu segundo factor. Por eso nunca debes compartir tus códigos.",
  },
  {
    pregunta: "¿Cómo presento un siniestro?",
    respuesta:
      "Desde los canales de atención. Te pediremos los datos de lo ocurrido y te daremos un código para seguirlo.",
  },
];

export default function Seguros() {
  return (
    <PaginaDeProducto
      titulo="Seguros simples"
      bajada="Protege tu tarjeta, tu celular y a tu familia. Elige lo que necesitas y mira cuánto pagarías al mes."
      simulador={<SimuladorDeSeguros planes={PLANES} />}
      beneficios={BENEFICIOS}
      requisitos={REQUISITOS}
      preguntas={PREGUNTAS}
      nota={NOTA_REFERENCIAL}
    />
  );
}
