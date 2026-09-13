"use client";

import { Camera, FileUp, RotateCcw, TriangleAlert, Upload } from "lucide-react";
import { useCallback, useEffect, useId, useRef, useState } from "react";

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

/**
 * Extensiones que acepta `POST .../documentos/url-de-subida` (ver
 * `SolicitudDeUrlDeSubidaDto` en el backend). No hay un límite de tamaño documentado en
 * ningún lado del diseño — 10 MB es un tope de sentido común para una foto de documento,
 * no un requisito de negocio, y se documenta así en el ADR-0023.
 */
const TIPOS_ACEPTADOS: Record<string, string> = {
  "image/jpeg": "jpg",
  "image/png": "png",
};
const TAMANO_MAXIMO_BYTES = 10 * 1024 * 1024;

type Medio = "camara" | "archivo";
type Fase =
  "pidiendo-permiso" | "en-vivo" | "sin-camara" | "eligiendo-archivo" | "revisando" | "subiendo";

interface Props {
  solicitudId: string;
  tipoDocumento: TipoDeDocumentoKyc;
  /** «Anverso» o «Reverso», para los textos de la pantalla. */
  cara: string;
  onCompletado: () => void;
}

/**
 * Cámara con guía visual de encuadre para un documento KYC, con carga desde archivo como
 * alternativa siempre disponible (AYNI-13 subtareas 12 y 13).
 *
 * **Por qué la transmisión de cámara no se detiene al tomar la foto.** Detenerla forzaría
 * pedir permiso otra vez si la persona pulsa «Volver a tomar» — con Chrome mostrando de
 * nuevo el diálogo del navegador por cada reintento. Se mantiene viva en segundo plano
 * (oculta con CSS, no desmontada) mientras se revisa la foto, y solo se detiene al cambiar
 * a carga por archivo, confirmar la subida, o salir de la pantalla.
 *
 * **Por qué «subir un archivo» no es solo el mensaje de error de la cámara.** El docente
 * pidió esta alternativa explícitamente para tres casos que no son el mismo: sin cámara,
 * con una cámara insuficiente (el OCR no alcanza su umbral con esa calidad), y quien ya
 * conserva un escaneo de su documento — ese tercer caso no tiene nada que ver con que la
 * cámara falle. Por eso el enlace a «Subir un archivo» está siempre visible, no solo
 * cuando `getUserMedia` rechaza. Ver ADR-0023 y sprint-backlog Sprint 2.
 *
 * **Qué falta a propósito.** El mensaje específico de por qué una foto no sirve depende de
 * `POST /kyc/verify` en Python, que todavía no existe — ver ADR-0022.
 */
export function CapturaDeDocumento({ solicitudId, tipoDocumento, cara, onCompletado }: Props) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const inputArchivoRef = useRef<HTMLInputElement>(null);
  const idDeInput = useId();

  const [medio, setMedio] = useState<Medio>("camara");
  const [fase, setFase] = useState<Fase>("pidiendo-permiso");
  const [foto, setFoto] = useState<{ blob: Blob; url: string; extension: string } | null>(null);
  const [errorDeArchivo, setErrorDeArchivo] = useState<string | null>(null);
  const [errorDeSubida, setErrorDeSubida] = useState<string | null>(null);

  const detenerCamara = useCallback(() => {
    streamRef.current?.getTracks().forEach((pista) => pista.stop());
    streamRef.current = null;
  }, []);

  useEffect(() => {
    if (medio !== "camara") return;

    let vigente = true;
    setFase("pidiendo-permiso");

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
  }, [medio, detenerCamara]);

  useEffect(() => {
    return () => {
      if (foto) URL.revokeObjectURL(foto.url);
    };
  }, [foto]);

  function elegirMedio(nuevo: Medio) {
    if (foto) URL.revokeObjectURL(foto.url);
    setFoto(null);
    setErrorDeArchivo(null);
    setErrorDeSubida(null);
    setMedio(nuevo);
    if (nuevo === "archivo") {
      detenerCamara();
      setFase("eligiendo-archivo");
    }
    // Si vuelve a "camara", el efecto de arriba (que depende de `medio`) vuelve a pedir
    // el permiso y pone la fase en "pidiendo-permiso" por su cuenta.
  }

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
        setFoto({ blob, url: URL.createObjectURL(blob), extension: "jpg" });
        setErrorDeSubida(null);
        setFase("revisando");
      },
      "image/jpeg",
      0.92,
    );
  }

  function archivoElegido(lista: FileList | null) {
    const archivo = lista?.[0];
    if (!archivo) return;

    const extension = TIPOS_ACEPTADOS[archivo.type];
    if (!extension) {
      setErrorDeArchivo("El archivo debe ser una foto JPG o PNG.");
      return;
    }
    if (archivo.size > TAMANO_MAXIMO_BYTES) {
      setErrorDeArchivo("El archivo pesa demasiado. El máximo es 10 MB.");
      return;
    }

    setErrorDeArchivo(null);
    setErrorDeSubida(null);
    setFoto({ blob: archivo, url: URL.createObjectURL(archivo), extension });
    setFase("revisando");
  }

  function volverAElegir() {
    if (foto) URL.revokeObjectURL(foto.url);
    setFoto(null);
    setErrorDeSubida(null);
    if (inputArchivoRef.current) inputArchivoRef.current.value = "";
    setFase(medio === "camara" ? "en-vivo" : "eligiendo-archivo");
  }

  async function usarEstaImagen() {
    if (!foto) return;
    setFase("subiendo");
    setErrorDeSubida(null);

    try {
      const { url } = await solicitarUrlDeSubida(solicitudId, tipoDocumento, foto.extension);
      const archivo = new File([foto.blob], `${tipoDocumento.toLowerCase()}.${foto.extension}`, {
        type: foto.blob.type || "image/jpeg",
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
          {medio === "camara"
            ? "Coloca tu DNI dentro del marco, en un lugar bien iluminado y sin reflejos."
            : "Sube una foto o un escaneo claro, sin reflejos ni recortes."}
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

      {medio === "archivo" && fase === "eligiendo-archivo" ? (
        <SelectorDeArchivo
          idDeInput={idDeInput}
          inputRef={inputArchivoRef}
          error={errorDeArchivo}
          onArchivo={archivoElegido}
        />
      ) : (
        <div
          role="status"
          aria-live="polite"
          className="relative overflow-hidden rounded-[16px] bg-gris-900"
          style={{ aspectRatio: "4 / 3" }}
        >
          {/* El video es funcional, no decorativo, pero no tiene contenido que un lector
              de pantalla pueda anunciar: el estado con significado (`fase`) se dice
              aparte, en los mensajes de texto de este mismo `role="status"`. */}
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
              {/* El marco guía: mismas proporciones que un DNI físico (ISO/IEC 7810
                  ID-1). Encuadrar el documento dentro de él es, literalmente,
                  encuadrarlo bien. */}
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
              <p className="text-[14px] font-semibold text-blanco">
                No pudimos acceder a tu cámara
              </p>
              <p className="text-[12.5px] text-gris-300">
                Revisa que le hayas dado permiso a tu navegador, o sube un archivo en su lugar.
              </p>
            </div>
          )}

          {fase === "subiendo" && (
            <p className="absolute inset-x-0 bottom-4 text-center text-[12.5px] font-semibold text-blanco">
              Subiendo tu foto…
            </p>
          )}
        </div>
      )}

      {medio === "camara" && fase === "en-vivo" && (
        <Boton onClick={tomarFoto} anchoCompleto>
          <Camera aria-hidden="true" className="h-4 w-4" />
          Tomar foto
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
            {medio === "camara" ? "Volver a tomar" : "Elegir otro archivo"}
          </Boton>
          <Boton onClick={usarEstaImagen} cargando={fase === "subiendo"} className="sm:flex-1">
            Usar esta foto
          </Boton>
        </div>
      )}

      {/* El cambio de medio siempre está disponible, no solo cuando la cámara falla: hay
          quien ya tiene un escaneo del documento y quien prefiere no usar la cámara aunque
          funcione. Ver ADR-0023. */}
      {fase !== "revisando" && fase !== "subiendo" && (
        <button
          type="button"
          onClick={() => elegirMedio(medio === "camara" ? "archivo" : "camara")}
          className="inline-flex items-center justify-center gap-2 self-center text-[13.5px] font-semibold text-azul-600 hover:underline"
        >
          {medio === "camara" ? (
            <>
              <FileUp aria-hidden="true" className="h-4 w-4" />
              Subir un archivo en su lugar
            </>
          ) : (
            <>
              <Camera aria-hidden="true" className="h-4 w-4" />
              Usar la cámara en su lugar
            </>
          )}
        </button>
      )}
    </div>
  );
}

interface SelectorDeArchivoProps {
  idDeInput: string;
  inputRef: React.RefObject<HTMLInputElement | null>;
  error: string | null;
  onArchivo: (lista: FileList | null) => void;
}

/** La zona de carga: clic o arrastrar-y-soltar, con el mismo `<input>` para los dos. */
function SelectorDeArchivo({ idDeInput, inputRef, error, onArchivo }: SelectorDeArchivoProps) {
  return (
    <div>
      <label
        htmlFor={idDeInput}
        onDragOver={(e) => e.preventDefault()}
        onDrop={(e) => {
          e.preventDefault();
          onArchivo(e.dataTransfer.files);
        }}
        className={`flex min-h-[220px] cursor-pointer flex-col items-center justify-center gap-3 rounded-[16px] border-2 border-dashed p-8 text-center ${
          error ? "border-error bg-blanco" : "border-gris-300 bg-azul-050 hover:bg-azul-100"
        }`}
      >
        <Upload aria-hidden="true" className="h-8 w-8 text-azul-600" />
        <p className="text-[14.5px] font-semibold text-azul-800">
          Arrastra tu archivo aquí o haz clic para elegirlo
        </p>
        <p className="text-[12.5px] text-gris-500">Formatos JPG o PNG, hasta 10 MB</p>
        <input
          ref={inputRef}
          id={idDeInput}
          type="file"
          accept="image/jpeg,image/png"
          onChange={(e) => onArchivo(e.target.files)}
          className="sr-only"
        />
      </label>
      {error && (
        <p role="alert" className="mt-2 text-[12.5px] font-medium text-error">
          {error}
        </p>
      )}
    </div>
  );
}
