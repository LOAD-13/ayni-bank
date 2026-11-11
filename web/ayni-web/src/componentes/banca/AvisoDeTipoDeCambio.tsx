import { TriangleAlert } from "lucide-react";

import { formatearFecha } from "@/lib/formato";

interface Props {
  /**
   * `true` si SUNAT/BCRP no respondió y el tipo que se va a aplicar es el último conocido,
   * servido desde la caché por el circuit breaker (AYNI-267, AYNI-268).
   */
  enCache: boolean;
  /** Cuándo se obtuvo ese tipo de cambio de la fuente oficial, en ISO 8601. */
  obtenidoEn: string;
  /**
   * Para enlazar el aviso con `aria-describedby` desde el botón de confirmar: así el lector
   * de pantalla lo repite justo en el momento de decidir, no solo cuando aparece.
   */
  id?: string;
}

/**
 * HU-12, escenario 2 · Advertir al cliente cuando se usa un tipo de cambio en caché (AYNI-269).
 *
 * Si la fuente externa no responde, la conversión sigue funcionando con el último tipo
 * conocido; lo que no puede pasar es que el cliente confirme sin saberlo. Este aviso va en
 * la pantalla de conversión (AYNI-274), junto a la simulación y antes del botón de
 * confirmar, y dice la fecha y la hora exactas de ese tipo de cambio.
 *
 * Con el tipo del día (`enCache` en `false`) no pinta nada: avisar siempre enseñaría a no
 * leer el aviso.
 */
export function AvisoDeTipoDeCambio({ enCache, obtenidoEn, id }: Props) {
  if (!enCache) return null;

  const fecha = formatearFecha(obtenidoEn);

  return (
    <div
      id={id}
      role="status"
      className="flex items-start gap-3 rounded-[12px] border border-dorado-300 bg-dorado-100 p-4 text-small text-dorado-800"
    >
      <TriangleAlert aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0 text-aviso" />
      <p>
        <strong className="font-semibold">Tipo de cambio del {fecha}.</strong> La fuente oficial
        (SUNAT/BCRP) no respondió, así que usamos el último tipo de cambio conocido. Revísalo antes
        de confirmar: la conversión se hará con este valor.
      </p>
    </div>
  );
}
