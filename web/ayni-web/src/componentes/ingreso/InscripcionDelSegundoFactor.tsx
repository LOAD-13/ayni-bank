"use client";

import QRCode from "qrcode";
import { useEffect, useState } from "react";

/**
 * Alta del segundo factor: el QR que se escanea una sola vez.
 *
 * **El QR se genera en el navegador, no en el servidor.** Podría venir ya dibujado desde
 * la API, pero entonces el secreto viajaría además como imagen y quedaría en la caché del
 * navegador y en cualquier proxy intermedio. Aquí se dibuja sobre un `canvas` a partir del
 * URI que ya llegó en la respuesta, y nada nuevo sale a la red.
 *
 * Se muestra también el secreto en texto: hay lectores de QR que fallan, escritorios sin
 * cámara enfrente y gente que prefiere teclearlo. Todas las aplicaciones de autenticación
 * admiten la entrada manual.
 */
export function InscripcionDelSegundoFactor({ uri }: { uri: string }) {
  const [imagen, setImagen] = useState<string | null>(null);
  const [mostrarElSecreto, setMostrarElSecreto] = useState(false);

  useEffect(() => {
    let vigente = true;
    QRCode.toDataURL(uri, { width: 220, margin: 1 })
      .then((datos) => {
        if (vigente) setImagen(datos);
      })
      .catch(() => {
        // Si el dibujo falla queda la entrada manual, que es lo que importa.
        if (vigente) setImagen(null);
      });

    return () => {
      vigente = false;
    };
  }, [uri]);

  const secreto = new URL(uri).searchParams.get("secret") ?? "";

  return (
    <div className="mt-4 rounded-[14px] border border-azul-200 bg-azul-050 p-5">
      <p className="text-[14px] font-bold text-azul-800">Activa tu segundo factor</p>
      <p className="mt-1.5 text-[13.5px] leading-[1.55] text-gris-700">
        Escanea este código con Google Authenticator, Authy o Microsoft Authenticator. Es la única
        vez que aparece.
      </p>

      {imagen && (
        <div className="mt-4 flex justify-center">
          {/* Sin `next/image`: es un data URI generado en el navegador, no un archivo que
              el optimizador pueda procesar. */}
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src={imagen}
            alt="Código QR para dar de alta el segundo factor"
            width={220}
            height={220}
            className="rounded-[10px] border border-gris-300 bg-blanco p-2"
          />
        </div>
      )}

      <button
        type="button"
        onClick={() => setMostrarElSecreto((v) => !v)}
        aria-expanded={mostrarElSecreto}
        className="mt-4 text-[13.5px] font-semibold text-azul-600 hover:underline"
      >
        {mostrarElSecreto ? "Ocultar el código manual" : "No puedo escanearlo"}
      </button>

      {mostrarElSecreto && (
        <p className="cifra mt-2.5 rounded-[10px] border border-gris-300 bg-blanco px-4 py-3 text-center text-[14px] tracking-[0.12em] break-all text-gris-900">
          {secreto}
        </p>
      )}

      <p className="mt-4 text-[13.5px] leading-[1.55] text-gris-700">
        Después escribe abajo el código de 6 dígitos que muestre la aplicación.
      </p>
    </div>
  );
}
