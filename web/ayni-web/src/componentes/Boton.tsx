import type { ButtonHTMLAttributes, ReactNode } from "react";

type Variante = "primario" | "secundario" | "contorno";

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  variante?: Variante;
  anchoCompleto?: boolean;
  cargando?: boolean;
  children: ReactNode;
}

const ESTILOS: Record<Variante, string> = {
  primario: "bg-azul-700 text-blanco hover:bg-azul-800 active:bg-azul-900",
  secundario: "bg-dorado-500 text-azul-900 hover:bg-dorado-600",
  contorno: "border border-gris-300 bg-blanco text-gris-700 hover:bg-gris-100",
};

/**
 * Boton del sistema de diseno.
 *
 * Tres decisiones que no son estilo:
 *
 * - `min-h-[44px]`: es el area tactil minima que exige WCAG 2.5.5. Por debajo, en un
 *   movil se falla el objetivo y se pulsa lo de al lado.
 * - `disabled:opacity-60` en lugar de ocultar el boton: un boton que desaparece deja al
 *   usuario sin saber que le falta por hacer.
 * - `aria-busy` mientras carga, para que un lector de pantalla anuncie que la operacion
 *   esta en curso en vez de quedarse en silencio.
 */
export function Boton({
  variante = "primario",
  anchoCompleto = false,
  cargando = false,
  disabled,
  children,
  className = "",
  ...resto
}: Props) {
  return (
    <button
      {...resto}
      disabled={disabled || cargando}
      aria-busy={cargando}
      className={[
        "inline-flex min-h-[44px] items-center justify-center gap-2 rounded-full px-6",
        "text-body font-semibold transition-colors",
        "disabled:cursor-not-allowed disabled:opacity-60",
        ESTILOS[variante],
        anchoCompleto ? "w-full" : "",
        className,
      ]
        .filter(Boolean)
        .join(" ")}
    >
      {cargando ? "Un momento…" : children}
    </button>
  );
}
