const CIFRAS = [
  { valor: "S/ 0.00", etiqueta: "Comisión de mantenimiento", acento: false },
  { valor: "4.50%", etiqueta: "TREA en soles, devengo diario", acento: true },
  { valor: "5 min", etiqueta: "Abrir cuenta con tu DNI", acento: false },
  { valor: "24/7", etiqueta: "Soporte real, sin robots", acento: true },
  { valor: "100%", etiqueta: "Digital, sin ir a una agencia", acento: false },
];

/**
 * Franja de cifras bajo el hero, según el diseño aprobado.
 *
 * Es una lista de definiciones y no una fila de `div`: cada cifra tiene su etiqueta, y
 * esa relación es lo que permite a un lector de pantalla anunciar «4.50 %, TREA en soles»
 * en lugar de leer diez fragmentos sueltos.
 *
 * Los separadores verticales son decorativos y desaparecen al apilarse. El diseño los usa
 * para dividir cinco columnas; cuando hay dos, no dividen nada.
 */
export function FranjaCifras() {
  return (
    <section id="cifras" className="bg-noche">
      <ul className="mx-auto grid max-w-[1440px] grid-cols-2 gap-x-6 gap-y-8 px-6 py-[34px] sm:grid-cols-3 lg:grid-cols-5 lg:gap-0 lg:px-16">
        {CIFRAS.map(({ valor, etiqueta, acento }, indice) => (
          <li
            key={etiqueta}
            className={indice > 0 ? "lg:border-l lg:border-blanco/10 lg:pl-10" : undefined}
          >
            <p
              className={`cifra text-[34px] leading-none font-bold ${
                acento ? "text-oro" : "text-blanco"
              }`}
            >
              {valor}
            </p>
            <p className="mt-1.5 text-[13px] text-texto-franja">{etiqueta}</p>
          </li>
        ))}
      </ul>
    </section>
  );
}
