"use client";

import { CheckCircle2, KeyRound, Mail, MessageSquare } from "lucide-react";
import { type TipoDeSegundoFactor } from "@/lib/api";

interface Props {
  metodoSeleccionado: TipoDeSegundoFactor;
  onSeleccionarMetodo: (metodo: TipoDeSegundoFactor) => void;
  deshabilitado?: boolean;
}

export function SelectorMetodoSegundoFactor({
  metodoSeleccionado,
  onSeleccionarMetodo,
  deshabilitado = false,
}: Props) {
  const opciones: {
    tipo: TipoDeSegundoFactor;
    titulo: string;
    descripcion: string;
    icono: typeof KeyRound;
    noDisponible?: boolean;
    etiquetaEstado?: string;
    motivoNoDisponible?: string;
  }[] = [
    {
      tipo: "APP_AUTENTICADORA",
      titulo: "App Autenticadora",
      descripcion: "Código temporal (TOTP) generado en Google Authenticator o Authy",
      icono: KeyRound,
    },
    {
      tipo: "CORREO_ELECTRONICO",
      titulo: "Correo Electrónico",
      descripcion: "Código de un solo uso enviado a tu dirección de correo registrada",
      icono: Mail,
    },
    {
      tipo: "SMS",
      titulo: "Mensaje SMS",
      descripcion: "Código de un solo uso enviado por mensaje de texto a tu celular",
      icono: MessageSquare,
      noDisponible: true,
      etiquetaEstado: "Próximamente",
      motivoNoDisponible: "El envío por SMS no está habilitado en esta versión.",
    },
  ];

  return (
    <div className="mt-4 space-y-3">
      <label className="block text-[14px] font-semibold text-gris-700">
        Elige tu método de segundo factor (2FA):
      </label>
      <div className="grid gap-3 sm:grid-cols-1">
        {opciones.map(
          ({
            tipo,
            titulo,
            descripcion,
            icono: Icono,
            noDisponible,
            etiquetaEstado,
            motivoNoDisponible,
          }) => {
            const esActivo = metodoSeleccionado === tipo;
            const opcionDeshabilitada = deshabilitado || Boolean(noDisponible);
            return (
              <button
                key={tipo}
                type="button"
                disabled={opcionDeshabilitada}
                aria-disabled={opcionDeshabilitada}
                onClick={() => {
                  if (!opcionDeshabilitada) {
                    onSeleccionarMetodo(tipo);
                  }
                }}
                className={`relative flex items-start gap-3.5 rounded-[14px] border p-4 text-left transition-all duration-200 ${
                  esActivo
                    ? "border-azul-600 bg-azul-050 ring-2 ring-azul-600/20"
                    : "border-gris-300 bg-blanco hover:border-azul-300 hover:bg-azul-050/50"
                } ${opcionDeshabilitada ? "cursor-not-allowed opacity-60" : "cursor-pointer"}`}
              >
                <span
                  className={`mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-[10px] ${
                    esActivo ? "bg-azul-600 text-blanco" : "bg-gris-100 text-gris-600"
                  }`}
                >
                  <Icono className="h-5 w-5" />
                </span>
                <div className="flex-1 pr-6">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="text-[14.5px] font-bold text-gris-900">{titulo}</p>
                    {etiquetaEstado && (
                      <span className="rounded-full border border-gris-300 bg-gris-100 px-2.5 py-0.5 text-[11.5px] font-semibold text-gris-700">
                        {etiquetaEstado}
                      </span>
                    )}
                  </div>
                  <p className="mt-0.5 text-[13px] leading-snug text-gris-600">{descripcion}</p>
                  {motivoNoDisponible && (
                    <p className="mt-1 text-[12px] font-medium text-gris-700">
                      {motivoNoDisponible}
                    </p>
                  )}
                </div>
                {esActivo && (
                  <CheckCircle2 className="absolute top-4 right-4 h-5 w-5 text-azul-600" />
                )}
              </button>
            );
          },
        )}
      </div>
    </div>
  );
}

