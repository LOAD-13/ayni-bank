"use client";

import {
  Camera,
  FileImage,
  FileUp,
  Hand,
  Layers,
  RotateCcw,
  Scan,
  ShieldCheck,
  Sun,
  TriangleAlert,
  Upload,
} from "lucide-react";
import { useCallback, useEffect, useId, useRef, useState } from "react";

import { Boton } from "@/componentes/Boton";
import {
  ErrorDeApi,
  evaluarCaptura,
  type LadoDelDni,
  type MotivoDeRechazo,
  solicitarUrlDeSubida,
  subirDocumento,
} from "@/lib/api";

/**
 * Ancho ÷ alto de un documento de identidad ISO/IEC 7810 ID-1 (85.60 × 53.98 mm), que es
 * el formato físico del DNI peruano. El marco guía usa esta proporción para que encuadrar
 * el documento dentro de él sea, literalmente, encuadrarlo bien.
 */
const PROPORCION_DNI = 85.6 / 53.98;

/** Sin PDF: kyc-service analiza la imagen con OpenCV, que no lee PDF (ADR-0028). */
const TIPOS_ACEPTADOS: Record<string, string> = {
  "image/jpeg": "jpg",
  "image/png": "png",
  "image/webp": "webp",
};

/** Qué repetir según el motivo del rechazo (escenarios 2 y 3 de HU-02). */
const QUE_REPETIR: Record<MotivoDeRechazo, string> = {
  NO_ES_DNI:
    "No reconocimos un DNI en la foto. Fotografía tu DNI completo, sobre una superficie lisa.",
  ENCUADRE: "Tu DNI no se ve completo. Encuádralo entero dentro del marco, sin cortar los bordes.",
  DESENFOQUE: "La foto salió borrosa. Mantén el celular quieto y espera a que enfoque.",
  REFLEJO: "Hay un reflejo sobre tu DNI. Inclínalo un poco o aléjate de la luz directa.",
  ILUMINACION: "La foto está muy oscura o muy clara. Busca un lugar con luz pareja.",
};

export function mensajeDeRechazo(
  motivo: MotivoDeRechazo | undefined,
  intentosRestantes = 0,
): string {
  const queRepetir = QUE_REPETIR[motivo ?? "NO_ES_DNI"];
  if (intentosRestantes <= 0) return queRepetir;
  return `${queRepetir} ${
    intentosRestantes === 1 ? "Te queda 1 intento." : `Te quedan ${intentosRestantes} intentos.`
  }`;
}
const TAMANO_MAXIMO_BYTES = 5 * 1024 * 1024;

type Medio = "camara" | "archivo";
type Fase =
  "pidiendo-permiso" | "en-vivo" | "sin-camara" | "eligiendo-archivo" | "revisando" | "subiendo";

interface Props {
  solicitudId: string;
  tipoDocumento: LadoDelDni;
  /** «Anverso» o «Reverso», para los textos de la pantalla. */
  cara: string;
  /** La foto se aceptó: se puede seguir al paso siguiente. */
  onCompletado: () => void;
  /** La solicitud quedó en manos de un operador, o se verificará cuando kyc-service vuelva. */
  onDerivada: (estado: "EN_REVISION_MANUAL" | "VERIFICACION_DIFERIDA") => void;
}

function formatearTamano(bytes: number): string {
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
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
 * **Después de subir, se evalúa.** La foto no se da por buena al llegar a MinIO: identity
 * la manda evaluar a kyc-service y, si no es un DNI o no tiene calidad, la persona ve el
 * motivo concreto y repite (escenarios 2 y 3, ADR-0028).
 */
export function CapturaDeDocumento({
  solicitudId,
  tipoDocumento,
  cara,
  onCompletado,
  onDerivada,
}: Props) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const inputArchivoRef = useRef<HTMLInputElement>(null);
  const idDeInput = useId();

  const [medio, setMedio] = useState<Medio>("camara");
  const [fase, setFase] = useState<Fase>("pidiendo-permiso");
  const [foto, setFoto] = useState<{
    blob: Blob;
    url: string;
    extension: string;
    nombre: string;
    tamanoBytes: number;
  } | null>(null);
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
        setFoto({
          blob,
          url: URL.createObjectURL(blob),
          extension: "jpg",
          nombre: `${tipoDocumento.toLowerCase()}.jpg`,
          tamanoBytes: blob.size,
        });
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
      setErrorDeArchivo("El archivo debe ser una foto JPG, PNG o WEBP.");
      return;
    }
    if (archivo.size > TAMANO_MAXIMO_BYTES) {
      setErrorDeArchivo("El archivo pesa demasiado. El máximo es 5 MB.");
      return;
    }

    setErrorDeArchivo(null);
    setErrorDeSubida(null);
    setFoto({
      blob: archivo,
      url: URL.createObjectURL(archivo),
      extension,
      nombre: archivo.name,
      tamanoBytes: archivo.size,
    });
    setFase("revisando");
  }

  function descartarFoto() {
    if (foto) URL.revokeObjectURL(foto.url);
    setFoto(null);
    if (inputArchivoRef.current) inputArchivoRef.current.value = "";
    setFase(medio === "camara" ? "en-vivo" : "eligiendo-archivo");
  }

  function volverAElegir() {
    setErrorDeSubida(null);
    descartarFoto();
  }

  async function usarEstaImagen() {
    if (!foto) return;
    setFase("subiendo");
    setErrorDeSubida(null);

    try {
      const destino = await solicitarUrlDeSubida(solicitudId, tipoDocumento, foto.extension);
      const archivo = new File([foto.blob], `${tipoDocumento.toLowerCase()}.${foto.extension}`, {
        type: foto.blob.type || "image/jpeg",
      });
      await subirDocumento(destino, archivo);
      const resultado = await evaluarCaptura(solicitudId, tipoDocumento, destino.claveDeObjeto);

      if (resultado.estado === "RECHAZADO") {
        // La foto ya no existe en el servidor: se descarta aquí también y se vuelve a la
        // cámara (o al selector), con el motivo a la vista.
        setErrorDeSubida(mensajeDeRechazo(resultado.motivo, resultado.intentosRestantes));
        descartarFoto();
        return;
      }

      detenerCamara();
      if (resultado.estado === "ACEPTADO") {
        onCompletado();
      } else {
        onDerivada(resultado.estado);
      }
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
    <div className="flex flex-col gap-8">
      <div>
        <h1 className="text-[26px] font-bold leading-tight text-azul-800 sm:text-[32px]">
          Captura el {cara.toLowerCase()} de tu DNI
        </h1>
        <p className="mt-2 text-[15px] text-gris-700">
          {medio === "camara"
            ? "Encuadra el documento dentro del marco, en un lugar bien iluminado y sin reflejos."
            : "Sube una foto o un escaneo claro de tu DNI, sin reflejos ni recortes."}
        </p>
      </div>

      <div className="grid grid-cols-1 gap-8 lg:grid-cols-[minmax(0,1fr)_368px]">
      <div className="flex flex-col gap-5">

      {errorDeSubida && (
        <p
          role="alert"
          className="flex items-start gap-2.5 rounded-[12px] border border-error bg-blanco p-4 text-[13.5px] text-error"
        >
          <TriangleAlert aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0" />
          {errorDeSubida}
        </p>
      )}

      {medio === "archivo" && fase === "eligiendo-archivo" && (
        <SelectorDeArchivo
          idDeInput={idDeInput}
          inputRef={inputArchivoRef}
          error={errorDeArchivo}
          onArchivo={archivoElegido}
        />
      )}

      {medio === "archivo" && (fase === "revisando" || fase === "subiendo") && foto && (
        <TarjetaDeArchivoCargado
          nombre={foto.nombre}
          tamanoBytes={foto.tamanoBytes}
          subiendo={fase === "subiendo"}
        />
      )}

      {medio === "camara" && (
        <div
          role="status"
          aria-live="polite"
          className="relative overflow-hidden rounded-[20px] bg-gradient-to-br from-azul-700 to-noche"
          style={{ aspectRatio: "16 / 10" }}
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
                className="w-full max-w-[420px] rounded-[14px] border-[3px] border-exito"
                style={{ aspectRatio: PROPORCION_DNI }}
              />
              <p className="rounded-full bg-exito px-4 py-1.5 text-[12.5px] font-semibold text-blanco">
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
              Revisando tu foto…
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
            {medio === "camara" ? "Volver a tomar" : "Cambiar"}
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

      <ConsejosDeCaptura />
      </div>
    </div>
  );
}

const CONSEJOS = [
  { Icono: Sun, texto: "Busca luz pareja y evita reflejos sobre el plástico." },
  { Icono: Scan, texto: "Que se vean las cuatro esquinas dentro del marco." },
  { Icono: Layers, texto: "Retira la funda o la mica del documento." },
  { Icono: Hand, texto: "Apóyalo en una superficie firme y no lo muevas." },
];

/** La columna lateral del diseño: cómo lograr la foto a la primera y qué pasa con ella. */
function ConsejosDeCaptura() {
  return (
    <aside className="flex flex-col gap-5">
      <h2 className="text-[18px] font-bold text-azul-800">Para que salga a la primera</h2>
      <ul className="flex flex-col gap-4">
        {CONSEJOS.map(({ Icono, texto }) => (
          <li key={texto} className="flex items-start gap-3.5 text-[14.5px] text-gris-700">
            <span
              aria-hidden="true"
              className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[10px] bg-azul-050 text-azul-700"
            >
              <Icono className="h-4 w-4" />
            </span>
            <span className="pt-1.5">{texto}</span>
          </li>
        ))}
      </ul>
      <p className="flex items-start gap-2.5 rounded-[14px] bg-azul-050 p-4 text-[13px] leading-relaxed text-azul-800">
        <ShieldCheck aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0 text-azul-600" />
        La imagen se transmite y se guarda cifrada. Se usa solo para verificar tu identidad y puedes
        pedir su eliminación en cualquier momento (Ley N.º 29733).
      </p>
    </aside>
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
        className={`flex min-h-[340px] cursor-pointer flex-col items-center justify-center gap-3 rounded-[16px] border-2 border-dashed p-8 text-center ${
          error ? "border-error bg-blanco" : "border-gris-300 bg-azul-050 hover:bg-azul-100"
        }`}
      >
        <Upload aria-hidden="true" className="h-8 w-8 text-azul-600" />
        <p className="text-[14.5px] font-semibold text-azul-800">
          Arrastra tu archivo aquí o haz clic para elegirlo
        </p>
        <p className="text-[12.5px] text-gris-500">JPG, PNG o WEBP · máximo 5 MB</p>
        <input
          ref={inputRef}
          id={idDeInput}
          type="file"
          accept="image/jpeg,image/png,image/webp"
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

interface TarjetaDeArchivoCargadoProps {
  nombre: string;
  tamanoBytes: number;
  subiendo: boolean;
}

function TarjetaDeArchivoCargado({ nombre, tamanoBytes, subiendo }: TarjetaDeArchivoCargadoProps) {
  return (
    <div className="flex min-h-[340px] flex-col items-center justify-center gap-3 rounded-[16px] border-2 border-dashed border-gris-300 bg-azul-050 p-8">
      <div className="flex w-full max-w-[420px] items-center gap-3 rounded-[12px] border border-gris-300 bg-blanco p-3">
        <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-[8px] bg-azul-100">
          <FileImage aria-hidden="true" className="h-5 w-5 text-azul-600" />
        </span>
        <div className="min-w-0 flex-1">
          <p className="truncate text-[13.5px] font-semibold text-gris-900">{nombre}</p>
          <p className="text-[12px] text-gris-500">
            {formatearTamano(tamanoBytes)} · {subiendo ? "subiendo…" : "cargado correctamente"}
          </p>
        </div>
      </div>
    </div>
  );
}
