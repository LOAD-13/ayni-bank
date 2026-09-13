"use client";

import { Camera, RotateCcw, TriangleAlert } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";

import { Boton } from "@/componentes/Boton";
import {
  ErrorDeApi,
  solicitarUrlDeSubida,
  subirDocumento,
  type TipoDeDocumentoKyc,
} from "@/lib/api";

/**
 * Ancho ÷ alto de un documento de identidad ISO/IEC 7810 ID-1 (85.60 × 53.98 mm), que es
 * el formato físico del DNI peruano. El marco guía usa esta proporción para que encuadrar
 * el documento dentro de él sea, literalmente, encuadrarlo bien.
 */
const PROPORCION_DNI = 85.6 / 53.98;

type Fase = "pidiendo-permiso" | "en-vivo" | "sin-camara" | "revisando" | "subiendo";

interface Props {
  solicitudId: string;
  tipoDocumento: TipoDeDocumentoKyc;
  /** «Anverso» o «Reverso», para los textos de la pantalla. */
  cara: string;
  onCompletado: () => void;
}

/**
 * Cámara con guía visual de encuadre para un documento KYC (AYNI-13 subtarea 12).
 *
 * **Por qué la transmisión no se detiene al tomar la foto.** Detenerla forzaría pedir
 * permiso de cámara otra vez si la persona pulsa «Volver a tomar» — con Chrome mostrando
 * de nuevo el diálogo del navegador por cada reintento. Se mantiene viva en segundo plano
 * (oculta con CSS, no desmontada) mientras se revisa la foto, y solo se detiene al confirmar
 * la subida o al salir de la pantalla.
 *
 * **Qué falta a propósito.** El mensaje específico de por qué una foto no sirve
 * («hay un reflejo», «acerca el documento») depende de `POST /kyc/verify` en Python, que
 * todavía no existe (ver ADR-0013 y la deuda técnica anotada en el plan de AYNI-13). Esta
 * pantalla da la guía ANTES de la foto —el marco geométrico— y sube lo que la persona
 * confirma; la retroalimentación DESPUÉS de subir es trabajo del caso de uso de integración
 * pendiente. Y sin cámara, la única salida hoy es la subtarea 13 (carga desde archivo),
 * todavía no construida — ver el enlace a «Prefiero subir un archivo».
 */
export function CapturaDeDocumento({ solicitudId, tipoDocumento, cara, onCompletado }: Props) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);

  const [fase, setFase] = useState<Fase>("pidiendo-permiso");
  const [foto, setFoto] = useState<{ blob: Blob; url: string } | null>(null);
  const [errorDeSubida, setErrorDeSubida] = useState<string | null>(null);

  const detenerCamara = useCallback(() => {
    streamRef.current?.getTracks().forEach((pista) => pista.stop());
    streamRef.current = null;
  }, []);

  useEffect(() => {
    let vigente = true;

    navigator.mediaDevices
      .getUserMedia({ video: { facingMode: "environment" }, audio: false })
      .then((stream) => {
        if (!vigente) {
          stream.getTracks().forEach((pista) => pista.stop());
          return;
        }
        streamRef.current = stream;
        if (videoRef.current) videoRef.current.srcObject = stream;
        setFase("en-vivo");
      })
      .catch(() => {
        if (vigente) setFase("sin-camara");
      });

    return () => {
      vigente = false;
      detenerCamara();
    };
  }, [detenerCamara]);

  useEffect(() => {
    return () => {
      if (foto) URL.revokeObjectURL(foto.url);
    };
  }, [foto]);

  function tomarFoto() {
    const video = videoRef.current;
    if (!video) return;

    const canvas = document.createElement("canvas");
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    const contexto = canvas.getContext("2d");
    if (!contexto) return;

    contexto.drawImage(video, 0, 0, canvas.width, canvas.height);
    canvas.toBlob(
      (blob) => {
        if (!blob) return;
        setFoto({ blob, url: URL.createObjectURL(blob) });
        setErrorDeSubida(null);
        setFase("revisando");
      },
      "image/jpeg",
      0.92,
    );
  }

  function volverATomar() {
    if (foto) URL.revokeObjectURL(foto.url);
    setFoto(null);
    setErrorDeSubida(null);
    setFase("en-vivo");
  }

  async function usarEstaFoto() {
    if (!foto) return;
    setFase("subiendo");
    setErrorDeSubida(null);

    try {
      const { url } = await solicitarUrlDeSubida(solicitudId, tipoDocumento, "jpg");
      const archivo = new File([foto.blob], `${tipoDocumento.toLowerCase()}.jpg`, {
        type: "image/jpeg",
      });
      await subirDocumento(url, archivo);
      detenerCamara();
      onCompletado();
    } catch (error) {
      setErrorDeSubida(
        error instanceof ErrorDeApi
          ? (error.problema.detail ?? error.message)
          : "No pudimos subir tu documento. Inténtalo de nuevo.",
      );
      setFase("revisando");
    }
  }

  return (
    <div className="flex flex-col gap-5">
      <div>
        <h1 className="text-[22px] font-bold text-azul-700">DNI · {cara}</h1>
        <p className="mt-1 text-[14.5px] text-gris-700">
          Coloca tu DNI dentro del marco, en un lugar bien iluminado y sin reflejos.
        </p>
      </div>

      {errorDeSubida && (
        <p
          role="alert"
          className="flex items-start gap-2.5 rounded-[12px] border border-error bg-blanco p-4 text-[13.5px] text-error"
        >
          <TriangleAlert aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0" />
          {errorDeSubida}
        </p>
      )}

      <div
        role="status"
        aria-live="polite"
        className="relative overflow-hidden rounded-[16px] bg-gris-900"
        style={{ aspectRatio: "4 / 3" }}
      >
        {/* El video es funcional, no decorativo, pero no tiene contenido que un lector de
            pantalla pueda anunciar: el estado con significado (`fase`) se dice aparte, en
            los mensajes de texto de este mismo `role="status"`. */}
        <video
          ref={videoRef}
          autoPlay
          playsInline
          muted
          aria-hidden="true"
          className={`h-full w-full object-cover ${fase === "en-vivo" ? "" : "hidden"}`}
        />

        {fase === "en-vivo" && (
          <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center gap-3 p-8">
            {/* El marco guía: mismas proporciones que un DNI físico (ISO/IEC 7810 ID-1).
                Encuadrar el documento dentro de él es, literalmente, encuadrarlo bien. */}
            <div
              className="w-full max-w-[360px] rounded-[14px] border-4 border-dashed border-dorado-500"
              style={{ aspectRatio: PROPORCION_DNI }}
            />
            <p className="rounded-full bg-noche/70 px-4 py-1.5 text-[12.5px] font-semibold text-blanco">
              Encuadra tu DNI dentro del marco
            </p>
          </div>
        )}

        {(fase === "revisando" || fase === "subiendo") && foto && (
          // Vista previa de un Blob local: next/image exige una URL servible, no un blob:
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={foto.url}
            alt="Foto del DNI que vas a enviar"
            className="h-full w-full object-cover"
          />
        )}

        {fase === "pidiendo-permiso" && (
          <p className="absolute inset-0 flex items-center justify-center text-[13.5px] text-blanco">
            Pidiendo acceso a tu cámara…
          </p>
        )}

        {fase === "sin-camara" && (
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 px-6 text-center">
            <TriangleAlert aria-hidden="true" className="h-8 w-8 text-dorado-500" />
            <p className="text-[14px] font-semibold text-blanco">No pudimos acceder a tu cámara</p>
            <p className="text-[12.5px] text-gris-300">
              Revisa que le hayas dado permiso a tu navegador, o vuelve a intentarlo desde otro
              dispositivo.
            </p>
          </div>
        )}

        {fase === "subiendo" && (
          <p className="absolute inset-x-0 bottom-4 text-center text-[12.5px] font-semibold text-blanco">
            Subiendo tu foto…
          </p>
        )}
      </div>

      {fase === "en-vivo" && (
        <Boton onClick={tomarFoto} anchoCompleto>
          <Camera aria-hidden="true" className="h-4 w-4" />
          Tomar foto
        </Boton>
      )}

      {(fase === "revisando" || fase === "subiendo") && (
        <div className="flex flex-col gap-3 sm:flex-row">
          <Boton
            variante="contorno"
            onClick={volverATomar}
            disabled={fase === "subiendo"}
            className="sm:flex-1"
          >
            <RotateCcw aria-hidden="true" className="h-4 w-4" />
            Volver a tomar
          </Boton>
          <Boton onClick={usarEstaFoto} cargando={fase === "subiendo"} className="sm:flex-1">
            Usar esta foto
          </Boton>
        </div>
      )}

      {/* La subtarea 13 (carga desde archivo) todavía no existe — ver sprint-backlog
          Sprint 2. Sin cámara, hoy no hay alternativa real: se dice la verdad en vez de
          ofrecer un enlace que no lleva a ninguna parte, siguiendo el mismo criterio que
          `/pendiente`. */}
      {fase === "sin-camara" && (
        <p className="text-center text-[12.5px] text-gris-500">
          Pronto podrás subir el DNI como archivo en vez de usar la cámara.
        </p>
      )}
    </div>
  );
}
