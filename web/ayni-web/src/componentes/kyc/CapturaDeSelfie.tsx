"use client";

import * as faceapi from "@vladmandic/face-api";
import { Camera, RotateCcw, ShieldCheck, TriangleAlert } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";

import { Boton } from "@/componentes/Boton";
import {
  ErrorDeApi,
  solicitarUrlDeSubida,
  subirDocumento,
} from "@/lib/api";

type Fase =
  | "cargando-modelos"
  | "pidiendo-permiso"
  | "en-vivo"
  | "sin-camara"
  | "revisando"
  | "subiendo";

interface Props {
  solicitudId: string;
  onCompletado: () => void;
}

export function CapturaDeSelfie({ solicitudId, onCompletado }: Props) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const animationFrameRef = useRef<number | null>(null);

  const [fase, setFase] = useState<Fase>("cargando-modelos");
  const [modelosCargados, setModelosCargados] = useState(false);
  const [rostrosDetectados, setRostrosDetectados] = useState<number>(0);
  const [errorDeSubida, setErrorDeSubida] = useState<string | null>(null);
  const [vivacidadConfirmada, setVivacidadConfirmada] = useState(false);
  
  const [foto, setFoto] = useState<{
    blob: Blob;
    url: string;
    extension: string;
    nombre: string;
  } | null>(null);

  const detenerCamara = useCallback(() => {
    if (animationFrameRef.current) {
      cancelAnimationFrame(animationFrameRef.current);
      animationFrameRef.current = null;
    }
    streamRef.current?.getTracks().forEach((pista) => pista.stop());
    streamRef.current = null;
  }, []);

  useEffect(() => {
    let vigente = true;
    async function cargarModelos() {
      try {
        await Promise.all([
          faceapi.nets.tinyFaceDetector.loadFromUri("/models"),
          faceapi.nets.faceExpressionNet.loadFromUri("/models")
        ]);
        if (vigente) setModelosCargados(true);
      } catch (e) {
        console.error("Error cargando modelos de face-api:", e);
      }
    }
    cargarModelos();
    return () => { vigente = false; };
  }, []);

  const procesarVideo = useCallback(async () => {
    if (!videoRef.current || videoRef.current.paused || videoRef.current.ended) return;

    try {
      const detecciones = await faceapi.detectAllFaces(
        videoRef.current,
        new faceapi.TinyFaceDetectorOptions({ inputSize: 160, scoreThreshold: 0.5 })
      ).withFaceExpressions();
      
      setRostrosDetectados(detecciones.length);
      
      if (detecciones.length === 1) {
        if (detecciones[0].expressions.happy > 0.8) {
          setVivacidadConfirmada(true);
        }
      } else {
        // Reset liveness if multiple or zero faces are detected suddenly
        setVivacidadConfirmada(false);
      }
    } catch (e) {
      // Ignorar errores transitorios de detección
    }

    animationFrameRef.current = requestAnimationFrame(procesarVideo);
  }, []);

  useEffect(() => {
    if (!modelosCargados) return;
    if (fase === "revisando" || fase === "subiendo") return;

    let vigente = true;
    setFase("pidiendo-permiso");

    navigator.mediaDevices
      .getUserMedia({ video: { facingMode: "user" }, audio: false })
      .then((stream) => {
        if (!vigente) {
          stream.getTracks().forEach((pista) => pista.stop());
          return;
        }
        streamRef.current = stream;
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
          videoRef.current.onplay = () => {
            procesarVideo();
          };
        }
        setFase("en-vivo");
      })
      .catch(() => {
        if (vigente) setFase("sin-camara");
      });

    return () => {
      vigente = false;
      detenerCamara();
    };
  }, [modelosCargados, fase, procesarVideo, detenerCamara]);

  useEffect(() => {
    return () => {
      if (foto) URL.revokeObjectURL(foto.url);
    };
  }, [foto]);

  function tomarFoto() {
    // AYNI-108 & AYNI-109: Solo permitir si hay exactamente 1 rostro y se verificó vivacidad
    if (rostrosDetectados !== 1 || !vivacidadConfirmada) return;

    const video = videoRef.current;
    if (!video) return;

    const canvas = document.createElement("canvas");
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    const contexto = canvas.getContext("2d");
    if (!contexto) return;

    // Efecto de espejo inverso (la cámara frontal suele verse en espejo)
    contexto.translate(canvas.width, 0);
    contexto.scale(-1, 1);
    contexto.drawImage(video, 0, 0, canvas.width, canvas.height);

    canvas.toBlob(
      (blob) => {
        if (!blob) return;
        setFoto({
          blob,
          url: URL.createObjectURL(blob),
          extension: "jpg",
          nombre: "selfie.jpg",
        });
        setErrorDeSubida(null);
        detenerCamara();
        setFase("revisando");
      },
      "image/jpeg",
      0.92
    );
  }

  function volverAElegir() {
    if (foto) URL.revokeObjectURL(foto.url);
    setFoto(null);
    setErrorDeSubida(null);
    setVivacidadConfirmada(false);
    setFase("en-vivo");
  }

  async function usarEstaImagen() {
    if (!foto) return;
    setFase("subiendo");
    setErrorDeSubida(null);

    try {
      const { url } = await solicitarUrlDeSubida(solicitudId, "SELFIE", foto.extension);
      const archivo = new File([foto.blob], `selfie.${foto.extension}`, {
        type: foto.blob.type || "image/jpeg",
      });
      await subirDocumento(url, archivo);
      onCompletado();
    } catch (error) {
      setErrorDeSubida(
        error instanceof ErrorDeApi
          ? (error.problema.detail ?? error.message)
          : "No pudimos subir tu foto. Inténtalo de nuevo."
      );
      setFase("revisando");
    }
  }

  return (
    <div className="flex flex-col gap-5">
      <div>
        <h1 className="text-[22px] font-bold text-azul-700">Selfie de verificación</h1>
        <p className="mt-1 text-[14.5px] text-gris-700">
          Asegúrate de estar en un lugar bien iluminado, sin lentes ni gorra, y mira directamente a la cámara.
        </p>
      </div>

      <p className="flex items-start gap-2.5 rounded-[12px] border border-azul-200 bg-azul-050 p-4 text-[12.5px] text-azul-800">
        <ShieldCheck aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0 text-azul-600" />
        La imagen se transmite y se guarda cifrada. Se usa solo para verificar tu identidad mediante cotejo facial (Ley N.º 29733).
      </p>

      {errorDeSubida && (
        <p
          role="alert"
          className="flex items-start gap-2.5 rounded-[12px] border border-error bg-blanco p-4 text-[13.5px] text-error"
        >
          <TriangleAlert aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0" />
          {errorDeSubida}
        </p>
      )}

      {fase !== "revisando" && fase !== "subiendo" && (
        <div
          role="status"
          aria-live="polite"
          className="relative overflow-hidden rounded-[16px] bg-gris-900"
          style={{ aspectRatio: "3 / 4" }}
        >
          <video
            ref={videoRef}
            autoPlay
            playsInline
            muted
            aria-hidden="true"
            className={`h-full w-full object-cover transform -scale-x-100 ${
              fase === "en-vivo" ? "" : "hidden"
            }`}
          />

          {fase === "en-vivo" && (
            <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center gap-3 p-8">
              <div
                className={`w-full max-w-[280px] rounded-full border-4 border-dashed transition-colors duration-300 ${
                  rostrosDetectados === 1 ? "border-exito" : "border-error"
                }`}
                style={{ aspectRatio: "3 / 4" }}
              />
              <p className={`rounded-full px-4 py-1.5 text-[12.5px] font-semibold text-blanco ${
                rostrosDetectados === 1 ? (vivacidadConfirmada ? "bg-exito" : "bg-aviso-600 text-blanco") : "bg-error"
              }`}>
                {rostrosDetectados === 0 && "No se detecta un rostro"}
                {rostrosDetectados === 1 && !vivacidadConfirmada && "Por favor, sonríe a la cámara"}
                {rostrosDetectados === 1 && vivacidadConfirmada && "Rostro y vivacidad detectados"}
                {rostrosDetectados > 1 && "Múltiples rostros detectados"}
              </p>
            </div>
          )}

          {(fase === "cargando-modelos" || fase === "pidiendo-permiso") && (
            <p className="absolute inset-0 flex items-center justify-center text-[13.5px] text-blanco">
              {fase === "cargando-modelos" ? "Iniciando motor de IA..." : "Pidiendo acceso a tu cámara…"}
            </p>
          )}

          {fase === "sin-camara" && (
            <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 px-6 text-center">
              <TriangleAlert aria-hidden="true" className="h-8 w-8 text-dorado-500" />
              <p className="text-[14px] font-semibold text-blanco">
                No pudimos acceder a tu cámara
              </p>
              <p className="text-[12.5px] text-gris-300">
                Revisa que le hayas dado permiso a tu navegador para continuar con el registro.
              </p>
            </div>
          )}
        </div>
      )}

      {(fase === "revisando" || fase === "subiendo") && foto && (
        <div
          className="relative overflow-hidden rounded-[16px] bg-gris-900"
          style={{ aspectRatio: "3 / 4" }}
        >
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src={foto.url}
            alt="Tu selfie capturada"
            className="h-full w-full object-cover"
          />
          {fase === "subiendo" && (
            <div className="absolute inset-0 flex items-center justify-center bg-noche/50">
              <p className="text-[14px] font-semibold text-blanco">Subiendo tu selfie…</p>
            </div>
          )}
        </div>
      )}

      {fase === "en-vivo" && (
        <Boton onClick={tomarFoto} anchoCompleto disabled={rostrosDetectados !== 1 || !vivacidadConfirmada}>
          <Camera aria-hidden="true" className="h-4 w-4" />
          Tomar selfie
        </Boton>
      )}

      {(fase === "revisando" || fase === "subiendo") && (
        <div className="flex flex-col gap-3 sm:flex-row">
          <Boton
            variante="contorno"
            onClick={volverAElegir}
            disabled={fase === "subiendo"}
            className="sm:flex-1"
          >
            <RotateCcw aria-hidden="true" className="h-4 w-4" />
            Volver a tomar
          </Boton>
          <Boton onClick={usarEstaImagen} cargando={fase === "subiendo"} className="sm:flex-1">
            Usar esta foto
          </Boton>
        </div>
      )}
    </div>
  );
}
