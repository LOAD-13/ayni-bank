"use client";

import { cloneElement, useId, useState, type ReactElement } from "react";

/** El límite de la sesión 24: una pista más larga ya es un párrafo y nadie la lee entera. */
export const LARGO_MAXIMO_DE_PISTA = 80;

/**
 * Pista breve de un ícono o acción principal.
 *
 * Aparece al pasar el ratón **y al enfocar con el tabulador**, porque una ayuda que solo ve
 * el ratón no existe para quien usa teclado. Va enlazada con `aria-describedby`, así que el
 * lector de pantalla la lee al llegar al control, y Escape la cierra sin mover el foco
 * (WCAG 1.4.13, contenido al pasar el puntero o al enfocar).
 */
export function Pista({
  texto,
  children,
  className = "relative flex",
}: {
  texto: string;
  children: ReactElement<{ "aria-describedby"?: string }>;
  className?: string;
}) {
  const id = useId();
  const [cerrada, setCerrada] = useState(false);

  return (
    <span
      className={`group ${className}`}
      onKeyDown={(evento) => {
        if (evento.key === "Escape") setCerrada(true);
      }}
      onMouseLeave={() => setCerrada(false)}
      onBlur={() => setCerrada(false)}
    >
      {cloneElement(children, { "aria-describedby": id })}
      <span
        role="tooltip"
        id={id}
        className={`pointer-events-none absolute bottom-full left-1/2 z-20 mb-2 w-max max-w-64 -translate-x-1/2 rounded-lg bg-azul-900 px-3 py-1.5 text-caption text-blanco shadow-lg ${
          cerrada ? "hidden" : "hidden group-focus-within:block group-hover:block"
        }`}
      >
        {texto}
      </span>
    </span>
  );
}
