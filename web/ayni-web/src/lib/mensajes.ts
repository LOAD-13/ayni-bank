/**
 * Mensaje de reserva cuando una operación falla sin un motivo conocido: la red se cortó o el servidor
 * respondió algo inesperado · HU-17, AYNI-292. Dice qué pasó y qué hacer, nunca solo «inténtalo de nuevo».
 *
 * En una operación que mueve dinero no se puede saber si llegó a hacerse antes del corte: por eso se pide
 * mirar los movimientos antes de repetirla.
 */
export function falloInesperado(accion: string, opciones: { mueveDinero?: boolean } = {}): string {
  const queHacer = opciones.mueveDinero
    ? "Antes de repetirla, revisa en tus movimientos si ya se hizo."
    : "Revisa tu conexión y vuelve a intentarlo en unos minutos.";
  return `No pudimos ${accion}. ${queHacer} Si sigue fallando, escríbenos desde la sección Ayuda.`;
}
