/** Las diez afirmaciones del cuestionario SUS (anexo A de `docs/calidad/reporte-usabilidad.md`). */
export const AFIRMACIONES_SUS = [
  "Creo que me gustaría usar esta aplicación con frecuencia.",
  "Encontré la aplicación innecesariamente compleja.",
  "Pensé que la aplicación era fácil de usar.",
  "Creo que necesitaría ayuda de una persona con conocimientos técnicos para usarla.",
  "Las funciones de la aplicación estaban bien integradas.",
  "Pensé que había demasiada inconsistencia en la aplicación.",
  "Imagino que la mayoría de la gente aprendería a usarla muy rápido.",
  "Encontré la aplicación muy engorrosa de usar.",
  "Me sentí muy seguro al usar la aplicación.",
  "Necesité aprender muchas cosas antes de poder usarla.",
] as const;

/**
 * Puntuación SUS de 0 a 100 a partir de diez respuestas de 1 a 5.
 *
 * Las afirmaciones impares son positivas (aportan respuesta − 1) y las pares negativas
 * (aportan 5 − respuesta); la suma, de 0 a 40, se multiplica por 2,5.
 */
export function puntuacionSus(respuestas: readonly number[]): number {
  if (respuestas.length !== 10 || respuestas.some((r) => !Number.isInteger(r) || r < 1 || r > 5)) {
    throw new RangeError("El SUS necesita diez respuestas enteras de 1 a 5.");
  }
  const suma = respuestas.reduce((total, r, i) => total + (i % 2 === 0 ? r - 1 : 5 - r), 0);
  return suma * 2.5;
}

/** Lectura habitual: 68 es la media publicada del SUS (Sauro y Lewis). */
export function interpretarSus(puntuacion: number): string {
  if (puntuacion >= 80.3) return "Excelente: por encima del 90 % de las aplicaciones medidas.";
  if (puntuacion >= 68) return "Buena: por encima de la media de 68.";
  if (puntuacion >= 51) return "Mejorable: por debajo de la media de 68.";
  return "Deficiente: hay problemas de usabilidad serios.";
}
