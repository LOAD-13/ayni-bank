/* Politica de contrasenas, en el cliente.
 *
 * Es un reflejo de `PoliticaDeContrasena` del identity-service, no la fuente de verdad.
 * Existe para que la persona vea que le falta MIENTRAS escribe, en lugar de descubrirlo
 * al enviar el formulario y esperar una respuesta del servidor.
 *
 * La validacion que manda sigue siendo la del servidor. Esta se puede desactivar desde
 * las herramientas del navegador en dos clics; la del dominio no.
 *
 * Si la politica cambia, cambia en los dos sitios. Es duplicacion consciente: la
 * alternativa —una llamada al servidor por cada tecla— es peor por latencia y por
 * privacidad, ya que enviaria contrasenas incompletas por la red. */

export type Requisito = "LONGITUD_MINIMA" | "MAYUSCULA" | "MINUSCULA" | "DIGITO" | "SIMBOLO";

export const LONGITUD_MINIMA = 12;
export const LONGITUD_MAXIMA = 128;

export const REQUISITOS: { clave: Requisito; texto: string }[] = [
  { clave: "LONGITUD_MINIMA", texto: `Mínimo ${LONGITUD_MINIMA} caracteres` },
  { clave: "MAYUSCULA", texto: "Una letra mayúscula" },
  { clave: "MINUSCULA", texto: "Una letra minúscula" },
  { clave: "DIGITO", texto: "Un dígito" },
  { clave: "SIMBOLO", texto: "Un símbolo (!, ?, #…)" },
];

/** Símbolo es «ni letra, ni dígito, ni espacio»: igual que en el dominio del servidor. */
function tieneSimbolo(contrasena: string): boolean {
  return /[^\p{L}\p{N}\s]/u.test(contrasena);
}

/** @returns los requisitos que la contraseña **sí** cumple. */
export function requisitosCumplidos(contrasena: string): Set<Requisito> {
  const cumplidos = new Set<Requisito>();
  if (contrasena.length >= LONGITUD_MINIMA) cumplidos.add("LONGITUD_MINIMA");
  if (/\p{Lu}/u.test(contrasena)) cumplidos.add("MAYUSCULA");
  if (/\p{Ll}/u.test(contrasena)) cumplidos.add("MINUSCULA");
  if (/\p{N}/u.test(contrasena)) cumplidos.add("DIGITO");
  if (tieneSimbolo(contrasena)) cumplidos.add("SIMBOLO");
  return cumplidos;
}

export function cumpleLaPolitica(contrasena: string): boolean {
  return requisitosCumplidos(contrasena).size === REQUISITOS.length;
}

export type Fortaleza = "vacia" | "debil" | "media" | "fuerte";

/**
 * Fortaleza orientativa, derivada solo de los requisitos cumplidos y la longitud.
 *
 * **No pretende medir entropía real.** Un medidor que dice «fuerte» ante `Password123!`
 * enseña a confiar en contraseñas malas, así que aquí «fuerte» exige los cinco requisitos
 * y holgura de longitud, no solo pasar el mínimo.
 */
export function fortalezaDe(contrasena: string): Fortaleza {
  if (contrasena.length === 0) return "vacia";
  const cumplidos = requisitosCumplidos(contrasena).size;
  if (cumplidos === REQUISITOS.length && contrasena.length >= 16) return "fuerte";
  if (cumplidos >= 4) return "media";
  return "debil";
}

/** Móvil peruano: nueve dígitos que empiezan en 9. */
export function esCelularPeruano(celular: string): boolean {
  return /^9[0-9]{8}$/.test(celular.replace(/[\s-]/g, ""));
}

/** Deliberadamente permisiva: lo que confirma que un correo existe es enviarle un mensaje. */
export function esCorreoValido(correo: string): boolean {
  return /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(correo.trim());
}
