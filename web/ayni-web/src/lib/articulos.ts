/** Artículos del blog: educación financiera escrita por el equipo, sin cifras de mercado inventadas. */
export interface Articulo {
  slug: string;
  titulo: string;
  resumen: string;
  fecha: string;
  lectura: string;
  secciones: { subtitulo?: string; parrafos: string[] }[];
}

export const ARTICULOS: Articulo[] = [
  {
    slug: "tea-o-trea-que-comparar",
    titulo: "TEA o TREA: qué número comparar antes de abrir una cuenta",
    resumen:
      "Dos cuentas pueden anunciar la misma tasa y pagarte cosas muy distintas. La diferencia está en las comisiones, y la TREA es el número que las incluye.",
    fecha: "19 de noviembre de 2026",
    lectura: "4 min de lectura",
    secciones: [
      {
        parrafos: [
          "Cuando un banco anuncia «4 % anual», casi siempre habla de la TEA, la tasa efectiva anual: lo que gana tu dinero en un año si los intereses se van sumando al saldo. Es un buen dato, pero no es lo que terminas ganando.",
          "La TREA, tasa de rendimiento efectivo anual, resta todo lo que la cuenta te cobra: mantenimiento, portes, comisiones por saldo bajo. Por eso la SBS obliga a publicarla. Es el único número que sirve para comparar dos cuentas.",
        ],
      },
      {
        subtitulo: "Cuándo la diferencia duele",
        parrafos: [
          "Una comisión fija pesa más cuanto menor es el saldo. Con S/ 10 000 una comisión mensual pequeña apenas mueve la TREA; con S/ 300, puede dejarla en cero o en negativo: la cuenta te cuesta dinero aunque anuncie intereses.",
          "Por eso, si estás empezando a ahorrar, la comisión de mantenimiento importa más que la tasa. Una cuenta sin comisiones y con una TEA modesta puede rendirte más que otra con una TEA alta y cobros mensuales.",
        ],
      },
      {
        subtitulo: "Cómo comparar en tres pasos",
        parrafos: [
          "Primero, busca la TREA, no la TEA, en el tarifario de cada banco. Segundo, revisa si la TREA publicada supone un saldo mínimo: algunas solo valen desde cierto monto. Tercero, mira cada cuánto se calculan los intereses: diario es mejor que mensual, porque cada día cuenta.",
          "En Ayni la TREA es igual a la TEA porque no cobramos mantenimiento ni gastos. Lo puedes comprobar en nuestro tarifario.",
        ],
      },
    ],
  },
  {
    slug: "como-reconocer-un-fraude",
    titulo: "Cinco señales de que un mensaje del «banco» es un fraude",
    resumen:
      "Los fraudes por correo, SMS y WhatsApp imitan al banco a la perfección. Lo que no pueden imitar es lo que un banco nunca te pide.",
    fecha: "19 de noviembre de 2026",
    lectura: "3 min de lectura",
    secciones: [
      {
        parrafos: [
          "El fraude más común no rompe ninguna contraseña: te la pide. Llega un mensaje con el logo del banco, un tono urgente y un enlace. Estas son las señales que lo delatan.",
        ],
      },
      {
        subtitulo: "Las señales",
        parrafos: [
          "Uno: te piden tu contraseña, tu código de verificación o los datos completos de tu tarjeta. Ningún banco lo hace, nunca. Ese código existe justamente para que solo tú puedas autorizar una operación.",
          "Dos: hay urgencia. «Tu cuenta será bloqueada en 24 horas», «confirma ahora o perderás tu dinero». La prisa está para que no pienses.",
          "Tres: el enlace no lleva a la web del banco. Antes de tocar, mira la dirección completa; una letra cambiada o un dominio extraño es suficiente para desconfiar.",
          "Cuatro: te ofrecen algo demasiado bueno, como un premio, un reembolso o un préstamo aprobado que no pediste.",
          "Cinco: el mensaje llega por un canal por el que tu banco no atiende. Si no sabes cuáles son, revisa la página de canales de atención de tu banco.",
        ],
      },
      {
        subtitulo: "Si ya hiciste clic",
        parrafos: [
          "Cambia tu contraseña de inmediato, congela tu tarjeta desde la banca y revisa tus últimos movimientos. Si ves algo que no reconoces, regístralo en el libro de reclamaciones para que quede constancia.",
        ],
      },
    ],
  },
  {
    slug: "que-es-el-devengo-diario",
    titulo: "Qué es el devengo diario y por qué tus intereses crecen cada día",
    resumen:
      "Que tu cuenta «devengue diario» significa que cada día que el dinero pasa en ella cuenta. Te explicamos cómo se calcula, con un ejemplo de S/ 1 000.",
    fecha: "19 de noviembre de 2026",
    lectura: "4 min de lectura",
    secciones: [
      {
        parrafos: [
          "Devengar es ganar un derecho aunque todavía no te lo paguen. Cuando una cuenta devenga intereses a diario, al cierre de cada día calcula lo que tu saldo ganó ese día y lo anota a tu favor. A fin de mes, suma todos esos días y te los abona.",
        ],
      },
      {
        subtitulo: "La fórmula",
        parrafos: [
          "El interés de un día es el saldo de cierre multiplicado por ((1 + TEA) elevado a 1/360, menos 1). Se usa un año de 360 días, como en todo el sistema financiero peruano.",
          "Con S/ 1 000.00 y una TEA de 4.50 %, un día rinde S/ 0.12227660. En 30 días con el mismo saldo son S/ 3.67.",
        ],
      },
      {
        subtitulo: "Por qué importa",
        parrafos: [
          "Con devengo mensual, el dinero que entra el día 20 puede no ganar nada ese mes. Con devengo diario, gana desde el primer día que pasa en la cuenta y deja de ganar solo cuando sale. Tus intereses siguen exactamente a tu saldo.",
          "En la banca de Ayni ves el detalle de cada día: con qué saldo y a qué tasa se calculó.",
        ],
      },
    ],
  },
];
