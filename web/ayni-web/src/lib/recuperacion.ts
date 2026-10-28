/* HU-21 · El token del enlace de recuperación.
 *
 * Llega en el fragmento (`/recuperar/nueva#t=…`) y no en la query: el navegador nunca
 * envía el fragmento al servidor, así que el token no queda en los registros de Caddy ni
 * del gateway, ni sale en la cabecera `Referer`. Ver ADR-0030. */

/** Formato del token: Base64 URL sin relleno, como lo genera identity. */
const FORMATO = /^[A-Za-z0-9_-]{16,128}$/;

/** Extrae el token de un fragmento como `#t=abc`. Devuelve null si falta o no tiene forma de token. */
export function tokenDelFragmento(fragmento: string): string | null {
  const parametros = new URLSearchParams(fragmento.replace(/^#/, ""));
  const token = parametros.get("t");
  return token !== null && FORMATO.test(token) ? token : null;
}
