"use client";

import { BadgeCheck, TriangleAlert } from "lucide-react";
import Link from "next/link";
import { type FormEvent, useEffect, useId, useRef, useState } from "react";

import { Boton } from "@/componentes/Boton";
import { CampoDeTexto } from "@/componentes/CampoDeTexto";
import {
  confirmarDatosDelDni,
  type DatosLeidosDelDni,
  ErrorDeApi,
  extraerDatosDelDni,
} from "@/lib/api";

interface Props {
  solicitudId: string;
  /** La lectura se confirmó y coincide con lo declarado: se sigue a la selfie. */
  onConfirmado: () => void;
  /** La solicitud quedó en manos de un operador, o se verificará cuando kyc-service vuelva. */
  onDerivada: (estado: "EN_REVISION_MANUAL" | "VERIFICACION_DIFERIDA") => void;
}

type Estado =
  | { fase: "leyendo" }
  | { fase: "ilegible"; intentosRestantes: number }
  | { fase: "confirmando"; datos: DatosLeidosDelDni }
  | { fase: "error"; mensaje: string };

/**
 * HU-02 · el titular revisa lo que leyó el OCR de su DNI, corrige lo que haga falta y
 * confirma (criterio de aceptación «el solicitante puede corregir manualmente cualquier dato
 * mal extraído antes de confirmar»).
 *
 * **El número llega enmascarado** y solo se pide entero si la persona dice que está mal: la
 * respuesta de lectura no exige sesión todavía (T-12), y no tiene por qué viajar completo si
 * el OCR lo leyó bien. Ver ADR-0028.
 *
 * **La lectura se pide una sola vez por montaje.** Cada lectura lanza el OCR y, si falla,
 * consume un intento del reverso; el doble efecto de React en desarrollo no debe gastar dos.
 */
export function ConfirmacionDeDatosDelDni({ solicitudId, onConfirmado, onDerivada }: Props) {
  const [estado, setEstado] = useState<Estado>({ fase: "leyendo" });
  const yaSePidio = useRef(false);

  // Las devoluciones de llamada cambian en cada render del padre; la lectura no debe
  // repetirse por eso.
  const onDerivadaRef = useRef(onDerivada);
  useEffect(() => {
    onDerivadaRef.current = onDerivada;
  }, [onDerivada]);

  useEffect(() => {
    if (yaSePidio.current) return;
    yaSePidio.current = true;

    extraerDatosDelDni(solicitudId)
      .then((resultado) => {
        if (resultado.estado === "ACEPTADO" && resultado.datos) {
          setEstado({ fase: "confirmando", datos: resultado.datos });
        } else if (resultado.estado === "RECHAZADO") {
          setEstado({ fase: "ilegible", intentosRestantes: resultado.intentosRestantes ?? 0 });
        } else if (
          resultado.estado === "EN_REVISION_MANUAL" ||
          resultado.estado === "VERIFICACION_DIFERIDA"
        ) {
          onDerivadaRef.current(resultado.estado);
        }
      })
      .catch((error: unknown) => {
        setEstado({
          fase: "error",
          mensaje:
            error instanceof ErrorDeApi
              ? (error.problema.detail ?? error.message)
              : "No pudimos leer los datos de tu DNI. Inténtalo de nuevo.",
        });
      });
  }, [solicitudId]);

  if (estado.fase === "leyendo") {
    return (
      <p role="status" className="py-10 text-center text-[14.5px] text-gris-700">
        Leyendo los datos de tu DNI…
      </p>
    );
  }

  if (estado.fase === "ilegible" || estado.fase === "error") {
    const mensaje =
      estado.fase === "error"
        ? estado.mensaje
        : `No pudimos leer los datos de tu DNI. Vuelve a fotografiar el reverso, con la zona de letras y números de abajo bien nítida. ${
            estado.intentosRestantes === 1
              ? "Te queda 1 intento."
              : `Te quedan ${estado.intentosRestantes} intentos.`
          }`;
    return (
      <div className="flex flex-col gap-5">
        <p
          role="alert"
          className="flex items-start gap-2.5 rounded-[12px] border border-error bg-blanco p-4 text-[13.5px] text-error"
        >
          <TriangleAlert aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0" />
          {mensaje}
        </p>
        <Link
          href={`/registro/dni-reverso?solicitudId=${solicitudId}`}
          className="inline-flex min-h-[44px] items-center justify-center rounded-full bg-azul-700 px-7 text-[15px] font-bold text-blanco hover:bg-azul-800"
        >
          Volver a fotografiar el reverso
        </Link>
      </div>
    );
  }

  return (
    <FormularioDeConfirmacion
      solicitudId={solicitudId}
      leidos={estado.datos}
      onConfirmado={onConfirmado}
      onDerivada={onDerivada}
    />
  );
}

interface FormularioProps extends Props {
  leidos: DatosLeidosDelDni;
}

function FormularioDeConfirmacion({
  solicitudId,
  leidos,
  onConfirmado,
  onDerivada,
}: FormularioProps) {
  const idSexo = useId();
  const [corregirNumero, setCorregirNumero] = useState(false);
  const [numero, setNumero] = useState("");
  const [nombres, setNombres] = useState(leidos.nombres);
  const [apellidos, setApellidos] = useState(leidos.apellidos);
  const [fechaNacimiento, setFechaNacimiento] = useState(leidos.fechaNacimiento);
  const [sexo, setSexo] = useState<"M" | "F">(leidos.sexo);
  const [fechaEmision, setFechaEmision] = useState(leidos.fechaEmision ?? "");
  const [errores, setErrores] = useState<Record<string, string>>({});
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  async function confirmar(evento: FormEvent) {
    evento.preventDefault();
    setErrores({});
    setErrorGeneral(null);

    if (corregirNumero && !/^\d{8}$/.test(numero)) {
      setErrores({ numero: "El DNI debe tener ocho dígitos." });
      return;
    }
    if (!fechaEmision) {
      setErrores({ fechaEmision: "Escribe la fecha de emisión que figura en tu DNI." });
      return;
    }

    setEnviando(true);
    try {
      const { estado } = await confirmarDatosDelDni(solicitudId, {
        numero: corregirNumero ? numero : undefined,
        nombres,
        apellidos,
        fechaNacimiento,
        sexo,
        fechaEmision,
      });
      if (estado === "ACEPTADO") onConfirmado();
      else onDerivada(estado);
    } catch (error) {
      if (error instanceof ErrorDeApi) {
        setErrores(error.porCampo());
        setErrorGeneral(error.problema.detail ?? error.message);
      } else {
        setErrorGeneral("No pudimos confirmar tus datos. Inténtalo de nuevo.");
      }
    } finally {
      setEnviando(false);
    }
  }

  return (
    <form onSubmit={confirmar} noValidate className="flex flex-col gap-5">
      <div>
        <h1 className="text-[22px] font-bold text-azul-700">Confirma tus datos</h1>
        <p className="mt-1 text-[14.5px] text-gris-700">
          Los leímos de tu DNI. Revísalos y corrige lo que no esté bien antes de continuar.
        </p>
      </div>

      {leidos.confiable && (
        <p className="flex items-start gap-2.5 rounded-[12px] border border-azul-200 bg-azul-050 p-4 text-[12.5px] text-azul-800">
          <BadgeCheck aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0 text-azul-600" />
          El número y la fecha de nacimiento se comprobaron con los dígitos de control de tu DNI.
        </p>
      )}

      {errorGeneral && (
        <p
          role="alert"
          className="rounded-[12px] border border-error bg-blanco p-4 text-[13.5px] text-error"
        >
          {errorGeneral}
        </p>
      )}

      <div className="flex flex-col gap-2">
        {corregirNumero ? (
          <CampoDeTexto
            etiqueta="Número de DNI"
            inputMode="numeric"
            maxLength={8}
            autoComplete="off"
            value={numero}
            onChange={(e) => setNumero(e.target.value.replace(/\D/g, ""))}
            error={errores.numero}
          />
        ) : (
          <div className="flex flex-col gap-1.5">
            <span className="text-small font-medium text-gris-700">Número de DNI</span>
            <span className="cifra min-h-[44px] rounded-md border border-gris-300 bg-gris-100 px-4 py-2.5 text-body text-gris-900">
              {leidos.numeroEnmascarado}
            </span>
          </div>
        )}
        <button
          type="button"
          onClick={() => setCorregirNumero((actual) => !actual)}
          className="self-start text-[13px] font-semibold text-azul-600 hover:underline"
        >
          {corregirNumero ? "El número leído es correcto" : "El número no es correcto"}
        </button>
      </div>

      <CampoDeTexto
        etiqueta="Nombres"
        value={nombres}
        maxLength={80}
        onChange={(e) => setNombres(e.target.value)}
        error={errores.nombres}
      />
      <CampoDeTexto
        etiqueta="Apellidos"
        value={apellidos}
        maxLength={120}
        onChange={(e) => setApellidos(e.target.value)}
        error={errores.apellidos}
      />

      <div className="grid gap-5 sm:grid-cols-2">
        <CampoDeTexto
          etiqueta="Fecha de nacimiento"
          type="date"
          value={fechaNacimiento}
          onChange={(e) => setFechaNacimiento(e.target.value)}
          error={errores.fechaNacimiento}
        />
        <CampoDeTexto
          etiqueta="Fecha de emisión"
          type="date"
          value={fechaEmision}
          ayuda={leidos.fechaEmision ? undefined : "No la pudimos leer: cópiala de tu DNI."}
          onChange={(e) => setFechaEmision(e.target.value)}
          error={errores.fechaEmision}
        />
      </div>

      <div className="flex flex-col gap-1.5">
        <label htmlFor={idSexo} className="text-small font-medium text-gris-700">
          Sexo
        </label>
        <select
          id={idSexo}
          value={sexo}
          onChange={(e) => setSexo(e.target.value as "M" | "F")}
          className="min-h-[44px] rounded-md border border-gris-300 bg-blanco px-4 text-body text-gris-900"
        >
          <option value="F">Femenino</option>
          <option value="M">Masculino</option>
        </select>
      </div>

      <Boton type="submit" cargando={enviando} anchoCompleto>
        Confirmar y continuar
      </Boton>
    </form>
  );
}
