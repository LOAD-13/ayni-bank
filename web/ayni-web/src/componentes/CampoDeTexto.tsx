import type { InputHTMLAttributes } from "react";
import { useId } from "react";

interface Props extends Omit<InputHTMLAttributes<HTMLInputElement>, "id"> {
  etiqueta: string;
  ayuda?: string;
  error?: string;
}

/**
 * Campo de formulario accesible.
 *
 * El detalle que importa esta en el cableado ARIA, no en el aspecto:
 *
 * - `<label htmlFor>` con un id generado por `useId`. Un `placeholder` no es etiqueta:
 *   desaparece al escribir y los lectores de pantalla no lo anuncian de forma fiable.
 * - `aria-describedby` enlaza ayuda y error al campo, de modo que se leen al enfocarlo y
 *   no como texto suelto que aparece en otro punto de la pagina.
 * - `aria-invalid` marca el campo como erroneo para la tecnologia asistiva. Sin el, un
 *   borde rojo es informacion que solo existe para quien ve.
 * - `role="alert"` en el mensaje: se anuncia en cuanto aparece, sin esperar a que el
 *   usuario vuelva a recorrer el formulario.
 */
export function CampoDeTexto({ etiqueta, ayuda, error, className = "", ...resto }: Props) {
  const id = useId();
  const idAyuda = `${id}-ayuda`;
  const idError = `${id}-error`;

  const descritoPor = [ayuda ? idAyuda : null, error ? idError : null].filter(Boolean).join(" ");

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-small font-medium text-gris-700">
        {etiqueta}
      </label>

      <input
        {...resto}
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={descritoPor || undefined}
        className={[
          "min-h-[44px] rounded-md border bg-blanco px-4 text-body text-gris-900",
          "placeholder:text-gris-500",
          error ? "border-error" : "border-gris-300",
          className,
        ]
          .filter(Boolean)
          .join(" ")}
      />

      {ayuda && !error && (
        <p id={idAyuda} className="text-caption text-gris-500">
          {ayuda}
        </p>
      )}

      {error && (
        <p id={idError} role="alert" className="text-caption font-medium text-error">
          {error}
        </p>
      )}
    </div>
  );
}
