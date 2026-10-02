import {
  fortalezaDe,
  REQUISITOS,
  requisitosCumplidos,
  type Fortaleza,
} from "@/dominio/politicaDeContrasena";

const TEXTO_DE_FORTALEZA: Record<Fortaleza, string> = {
  vacia: "Escribe tu contraseña",
  debil: "Fortaleza: débil",
  media: "Fortaleza: media",
  fuerte: "Fortaleza: fuerte",
};

const COLOR_DE_FORTALEZA: Record<Fortaleza, string> = {
  vacia: "text-gris-500",
  debil: "text-error",
  media: "text-aviso",
  fuerte: "text-exito",
};

const TRAMOS_LLENOS: Record<Fortaleza, number> = {
  vacia: 0,
  debil: 1,
  media: 2,
  fuerte: 3,
};

/**
 * Lista de requisitos con su estado, y el medidor de fortaleza.
 *
 * La politica se muestra **antes de escribir**, con todos los requisitos visibles desde
 * el primer momento. Revelarlos segun se incumplen convierte el registro en un juego de
 * adivinanzas.
 *
 * Cada requisito lleva su marca textual —«Cumplido» / «Falta»— ademas del icono. El color
 * y la forma no bastan: quien no distingue verde de rojo necesita la palabra.
 */
export function RequisitosDeContrasena({ contrasena }: { contrasena: string }) {
  const cumplidos = requisitosCumplidos(contrasena);
  const fortaleza = fortalezaDe(contrasena);
  const llenos = TRAMOS_LLENOS[fortaleza];

  return (
    <div className="rounded-md border border-azul-200 bg-azul-050 p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-small font-semibold text-azul-800">Tu contraseña debe tener</p>
        <p className={`text-small font-semibold ${COLOR_DE_FORTALEZA[fortaleza]}`}>
          {TEXTO_DE_FORTALEZA[fortaleza]}
        </p>
      </div>

      {/* Decorativo: la misma informacion ya esta en el texto de arriba, que es lo que
          lee la tecnologia asistiva. */}
      <div className="mt-2 flex gap-1.5" aria-hidden="true">
        {[0, 1, 2].map((tramo) => (
          <span
            key={tramo}
            className={`h-1.5 flex-1 rounded-full ${
              tramo < llenos
                ? fortaleza === "fuerte"
                  ? "bg-exito"
                  : fortaleza === "media"
                    ? "bg-aviso"
                    : "bg-error"
                : "bg-gris-300"
            }`}
          />
        ))}
      </div>

      <ul className="mt-3 flex flex-col gap-1.5">
        {REQUISITOS.map(({ clave, texto }) => {
          const cumplido = cumplidos.has(clave);
          return (
            <li key={clave} className="flex items-center gap-2 text-small">
              <span
                aria-hidden="true"
                className={`inline-flex h-4 w-4 shrink-0 items-center justify-center rounded-full text-caption font-bold ${
                  cumplido ? "bg-exito text-blanco" : "bg-gris-300 text-gris-700"
                }`}
              >
                {cumplido ? "✓" : "·"}
              </span>
              <span className={cumplido ? "text-gris-700" : "text-gris-500"}>{texto}</span>
              <span className="sr-only">{cumplido ? "Cumplido" : "Falta"}</span>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
