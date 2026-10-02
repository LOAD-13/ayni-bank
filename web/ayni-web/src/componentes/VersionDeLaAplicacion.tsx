/**
 * Versión desplegada, bajo el formulario de ingreso.
 *
 * Sale del fichero VERSION de la raíz del repositorio: el pipeline de despliegue la lee y
 * la incrusta al construir la imagen. Sirve para comprobar de un vistazo qué versión está
 * en producción, y es la que se sube al publicar un cambio (1.0.0 → 1.0.1).
 */
export function VersionDeLaAplicacion() {
  const version = process.env.NEXT_PUBLIC_APP_VERSION ?? "desarrollo";
  const etiqueta = /^\d/.test(version) ? `v${version}` : version;

  return (
    <p className="mt-6 text-[12.5px] text-gris-700" data-testid="version-de-la-aplicacion">
      Ayni Bank · versión {etiqueta}
    </p>
  );
}
