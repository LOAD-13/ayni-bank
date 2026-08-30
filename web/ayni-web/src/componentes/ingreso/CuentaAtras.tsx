"use client";

import { Timer } from "lucide-react";
import { useEffect, useState } from "react";

interface Props {
  segundos: number;
  onTerminar: () => void;
}

/**
 * La cuenta atrás del bloqueo, tal como aparece en el diseño aprobado.
 *
 * `aria-live="off"` a propósito: un contador que se anuncia cada segundo hace inservible
 * un lector de pantalla. El texto completo va en el `aria-label`, que se lee una sola vez
 * cuando el foco llega, y el número visible queda como información puramente visual.
 */
export function CuentaAtras({ segundos, onTerminar }: Props) {
  const [restantes, setRestantes] = useState(segundos);

  useEffect(() => {
    setRestantes(segundos);
  }, [segundos]);

  useEffect(() => {
    if (restantes <= 0) {
      onTerminar();
      return;
    }
    const temporizador = setTimeout(() => setRestantes((s) => s - 1), 1000);
    return () => clearTimeout(temporizador);
  }, [restantes, onTerminar]);

  const minutos = Math.floor(restantes / 60);
  const resto = restantes % 60;
  const formateado = `${String(minutos).padStart(2, "0")}:${String(resto).padStart(2, "0")}`;

  return (
    <p
      className="mt-4 flex items-center justify-between rounded-[10px] border border-gris-300 bg-blanco px-4 py-3"
      aria-label={`Podrás reintentar en ${minutos} minutos y ${resto} segundos`}
    >
      <span className="flex items-center gap-2.5 text-[13.5px] text-gris-700">
        <Timer aria-hidden="true" className="h-4 w-4 text-gris-500" />
        Podrás reintentar en
      </span>
      <span aria-hidden="true" className="cifra text-[20px] font-bold text-azul-700">
        {formateado}
      </span>
    </p>
  );
}
