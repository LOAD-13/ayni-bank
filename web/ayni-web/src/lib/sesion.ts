/* La sesión del cliente en el navegador.
 *
 * El token de acceso vive SOLO en memoria: ni localStorage ni sessionStorage, que
 * cualquier script inyectado podría leer. Si la página se recarga, el token se pierde y se
 * recupera con la cookie de renovación (HttpOnly, SameSite=Strict, invisible para
 * JavaScript), que rota en cada uso. Ver ADR-0010. */

import { renovarSesion, type Sesion } from "./api";

let actual: Sesion | null = null;

export function guardarSesion(sesion: Sesion): void {
  actual = sesion;
}

export function olvidarSesion(): void {
  actual = null;
}

/** El token si sigue vigente con al menos 30 s de margen; si no, `null`. */
export function tokenVigente(ahora: Date = new Date()): string | null {
  if (!actual) return null;
  const margen = 30_000;
  return new Date(actual.expiraEn).getTime() - margen > ahora.getTime()
    ? actual.tokenDeAcceso
    : null;
}

/**
 * Un token válido: el de memoria o uno nuevo pedido con la cookie de renovación.
 *
 * Devuelve `null` si no hay sesión que recuperar; quien llama decide adónde mandar al
 * usuario.
 */
export async function obtenerToken(): Promise<string | null> {
  const vigente = tokenVigente();
  if (vigente) return vigente;
  try {
    const renovada = await renovarSesion();
    actual = renovada;
    return renovada.tokenDeAcceso;
  } catch {
    actual = null;
    return null;
  }
}

/**
 * El identificador del usuario, leído del `sub` del token.
 *
 * Solo para presentación: la autorización la hace el gateway verificando la firma. Leerlo
 * aquí sin verificar es inocuo porque nada de lo que decide el servidor depende de ello.
 */
export function usuarioDelToken(token: string): string | null {
  try {
    const carga = token.split(".")[1];
    const json = atob(carga.replace(/-/g, "+").replace(/_/g, "/"));
    const sub = (JSON.parse(json) as { sub?: unknown }).sub;
    return typeof sub === "string" ? sub : null;
  } catch {
    return null;
  }
}
