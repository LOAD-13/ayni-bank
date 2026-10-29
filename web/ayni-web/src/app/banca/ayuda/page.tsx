import Link from "next/link";

const PREGUNTAS = [
  {
    pregunta: "¿Cuánto puedo transferir?",
    respuesta:
      "Desde S/ 1.00 hasta S/ 5 000.00 por transferencia, a otra cuenta Ayni o a otro banco con su CCI. No cobramos comisión.",
  },
  {
    pregunta: "¿Por qué me piden un código para transferir?",
    respuesta:
      "Es tu segundo factor: aunque alguien conozca tu contraseña, sin ese código no puede mover tu dinero. Lo eliges en Configuración.",
  },
  {
    pregunta: "Se cortó la conexión al confirmar. ¿Se cobró dos veces?",
    respuesta:
      "No. Cada operación viaja con una clave única: si vuelves a confirmar, recibes el comprobante de la primera en lugar de un segundo cargo.",
  },
  {
    pregunta: "Me equivoqué al escribir los datos. ¿Cómo los cambio?",
    respuesta:
      "Antes de mover el dinero te mostramos el resumen. Pulsa «Corregir» y vuelves al formulario con lo que ya escribiste.",
  },
  {
    pregunta: "¿Dónde veo mi CCI para recibir dinero de otro banco?",
    respuesta: "En el resumen, en el recuadro «Recibir dinero», junto a tu número de cuenta.",
  },
  {
    pregunta: "¿Cómo gano intereses?",
    respuesta:
      "Tu cuenta gana intereses todos los días según su TREA, que ves junto al saldo. Se abonan a fin de mes.",
  },
  {
    pregunta: "¿Qué es el depósito simulado?",
    respuesta:
      "Ayni Bank es un proyecto académico: el depósito añade saldo de prueba para que puedas probar las transferencias. No es dinero real.",
  },
  {
    pregunta: "¿Ayni Bank me pedirá mi contraseña por correo o teléfono?",
    respuesta:
      "Nunca. Ni tu contraseña ni tus códigos. Si alguien te los pide en nombre de Ayni, no los des.",
  },
] as const;

/**
 * Ayuda contextual de la banca (sesión 24): las dudas que aparecen al operar, respondidas en
 * la propia aplicación. `<details>` es nativo: se abre con teclado y el lector de pantalla
 * anuncia si está abierto o cerrado sin ARIA añadido.
 */
export default function PaginaDeAyuda() {
  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <header>
        <h1 className="text-h1 font-bold text-azul-800">Ayuda</h1>
        <p className="text-body text-gris-700">Las preguntas que más nos hacen al usar la banca.</p>
      </header>

      <div className="flex flex-col gap-3">
        {PREGUNTAS.map(({ pregunta, respuesta }) => (
          <details
            key={pregunta}
            className="group rounded-[16px] border border-azul-200 bg-blanco p-5 open:border-azul-400"
          >
            <summary className="cursor-pointer text-body font-semibold text-gris-900 marker:text-azul-700">
              {pregunta}
            </summary>
            <p className="mt-3 text-body text-gris-800">{respuesta}</p>
          </details>
        ))}
      </div>

      <p className="text-body text-gris-800">
        ¿Algo no fue claro?{" "}
        <Link href="/banca/opinion" className="font-semibold text-azul-700 hover:underline">
          Cuéntanos qué tal te va con la banca
        </Link>
        .
      </p>
    </div>
  );
}
