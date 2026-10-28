/**
 * Marcas de «esto ya se vio» guardadas en el navegador: el asistente de bienvenida y el logro
 * de la primera operación. No son datos sensibles; los tokens nunca pasan por aquí.
 *
 * `localStorage` puede no existir o lanzar (ventana privada, almacenamiento bloqueado): en ese
 * caso la marca se da por no vista y lo peor que pasa es que la bienvenida se repita.
 */
export const MARCAS = {
  bienvenida: "ayni.bienvenida-vista",
  primeraOperacion: "ayni.primera-operacion",
} as const;

export function marcaVista(clave: string): boolean {
  try {
    return localStorage.getItem(clave) !== null;
  } catch {
    return false;
  }
}

export function marcarVista(clave: string) {
  try {
    localStorage.setItem(clave, "1");
  } catch {
    // Sin almacenamiento no hay memoria: se acepta, no hay nada que proteger.
  }
}
