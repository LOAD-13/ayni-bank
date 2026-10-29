import { createHmac } from "node:crypto";

/**
 * Código TOTP (RFC 6238, SHA-1, 6 dígitos, 30 s) a partir de la semilla Base32 de la cuenta de
 * prueba. Lo usa la suite contra la nube para pasar el segundo factor sin una persona con el
 * teléfono en la mano. La semilla llega por variable de entorno y nunca se escribe en el repo.
 */
export function codigoTotp(semillaBase32: string, instante = Date.now(), digitos = 6): string {
  const contador = BigInt(Math.floor(instante / 1000 / 30));
  const mensaje = Buffer.alloc(8);
  mensaje.writeBigUInt64BE(contador);
  const hmac = createHmac("sha1", deBase32(semillaBase32)).update(mensaje).digest();
  const desplazamiento = hmac[hmac.length - 1] & 0x0f;
  const binario = hmac.readUInt32BE(desplazamiento) & 0x7fffffff;
  return String(binario % 10 ** digitos).padStart(digitos, "0");
}

function deBase32(texto: string): Buffer {
  const alfabeto = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  let bits = "";
  for (const caracter of texto.replace(/[=\s]/g, "").toUpperCase()) {
    const valor = alfabeto.indexOf(caracter);
    if (valor < 0) throw new Error("La semilla TOTP no es Base32 válido");
    bits += valor.toString(2).padStart(5, "0");
  }
  const bytes = bits.match(/.{8}/g) ?? [];
  return Buffer.from(bytes.map((b) => parseInt(b, 2)));
}
