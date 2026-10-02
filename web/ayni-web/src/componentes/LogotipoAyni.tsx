import Image from "next/image";

/** Proporción del logotipo exportado de pen.dev: 240 × 34. */
const PROPORCION = 240 / 34;

interface Props {
  /** Altura del texto «AYNI Bank», en píxeles. El símbolo se escala en proporción. */
  altura?: number;
  /**
   * `claro` sobre fondos oscuros —el logotipo se exportó en blanco—, `oscuro` sobre
   * fondos claros.
   */
  tono?: "claro" | "oscuro";
  className?: string;
}

/**
 * El logotipo de Ayni Bank, del prototipo aprobado.
 *
 * **Por qué existe este componente.** El texto «AYNI Bank» se exportó de pen.dev en
 * blanco, porque en el prototipo siempre aparece sobre el azul del hero. Puesto tal cual
 * sobre la cabecera blanca del registro resultaba invisible: se veía el símbolo dorado y
 * nada más. No era un fallo de maquetación sino de material, y por eso no saltaba en
 * ninguna prueba.
 *
 * La versión oscura no se pinta con filtros de color, que sobre una imagen blanca dan
 * resultados impredecibles, sino con una máscara: el PNG aporta solo su canal alfa y el
 * color lo pone el fondo del elemento, que es un token de la marca. Así la letra queda
 * exactamente en `--azul-700` y no en un azul aproximado.
 */
export function LogotipoAyni({ altura = 17, tono = "claro", className = "" }: Props) {
  const anchoDelTexto = Math.round(altura * PROPORCION);
  const alturaDelSimbolo = Math.round(altura * 1.7);

  return (
    <span className={`flex shrink-0 items-center gap-2.5 ${className}`}>
      {/* El símbolo es dorado en el original y se ve igual sobre los dos fondos. */}
      <Image
        src="/pen/landing-cc4dc915faa1.png"
        alt=""
        aria-hidden="true"
        width={72}
        height={58}
        priority
        style={{ height: alturaDelSimbolo, width: "auto" }}
      />

      {tono === "claro" ? (
        <Image
          src="/pen/landing-2cc23c14b5e9.png"
          alt="Ayni Bank"
          width={240}
          height={34}
          priority
          style={{ height: altura, width: "auto" }}
        />
      ) : (
        <span
          role="img"
          aria-label="Ayni Bank"
          className="block bg-azul-700"
          style={{
            height: altura,
            width: anchoDelTexto,
            maskImage: "url(/pen/landing-2cc23c14b5e9.png)",
            maskSize: "contain",
            maskRepeat: "no-repeat",
            maskPosition: "center",
            WebkitMaskImage: "url(/pen/landing-2cc23c14b5e9.png)",
            WebkitMaskSize: "contain",
            WebkitMaskRepeat: "no-repeat",
            WebkitMaskPosition: "center",
          }}
        />
      )}
    </span>
  );
}
