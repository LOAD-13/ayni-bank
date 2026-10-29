"use client";

import { Eye, KeyRound } from "lucide-react";
import { useEffect, useState } from "react";

import { Boton } from "@/componentes/Boton";
import { PasoDeConfirmacion } from "@/componentes/banca/PasoDeConfirmacion";
import { useSesion } from "@/componentes/banca/SesionDeBanca";
import { TarjetaAyni } from "@/componentes/landing/TarjetaAyni";
import {
  ErrorDeApi,
  IMPORTE_SIMBOLICO_DE_REVELACION,
  OPERACION_REVELAR_PAN,
  consultarTarjeta,
  cvvDeLaTarjeta,
  iniciarConfirmacion,
  revelarPan,
  verificarConfirmacion,
  type ConfirmacionIniciada,
  type CvvDinamico,
  type PanRevelado,
  type TarjetaVirtual as Tarjeta,
} from "@/lib/api";
import { nuevaClave } from "@/lib/formato";

const HORA = new Intl.DateTimeFormat("es-PE", {
  hour: "2-digit",
  minute: "2-digit",
  timeZone: "America/Lima",
});

interface Props {
  /** Avisa si el titular tiene tarjeta, para que la página decida qué más mostrar. */
  alCargar?: (existe: boolean) => void;
}

/**
 * La tarjeta de débito virtual · HU-09, con la identidad de marca de la portada.
 *
 * La tarjeta visual solo recibe los cuatro últimos dígitos (regla del §5.3 del diseño). El
 * número completo se muestra aparte, tras confirmar con el segundo factor, y se oculta solo
 * cuando vence el plazo que da el servidor. El CVV se pide cuando se quiere ver: no vive en la
 * página antes ni se guarda.
 */
export function TarjetaVirtual({ alCargar }: Props) {
  const { token } = useSesion();
  const [tarjeta, setTarjeta] = useState<Tarjeta | null>(null);
  const [cvv, setCvv] = useState<CvvDinamico | null>(null);
  const [revelado, setRevelado] = useState<PanRevelado | null>(null);
  const [confirmacion, setConfirmacion] = useState<ConfirmacionIniciada | null>(null);
  const [clave, setClave] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let vigente = true;
    void token()
      .then(consultarTarjeta)
      .then((t) => {
        if (!vigente) return;
        setTarjeta(t);
        alCargar?.(true);
      })
      .catch(() => vigente && alCargar?.(false));
    return () => {
      vigente = false;
    };
  }, [token, alCargar]);

  // El número completo desaparece solo al vencer su plazo.
  useEffect(() => {
    if (!revelado) return;
    const restante = new Date(revelado.visibleHasta).getTime() - Date.now();
    const espera = setTimeout(() => setRevelado(null), Math.max(restante, 0));
    return () => clearTimeout(espera);
  }, [revelado]);

  if (!tarjeta) return null;

  async function verCvv() {
    setError(null);
    try {
      setCvv(await cvvDeLaTarjeta(await token()));
    } catch (fallo) {
      setError(mensajeDe(fallo));
    }
  }

  async function pedirConfirmacion() {
    if (!tarjeta) return;
    const nueva = nuevaClave();
    setClave(nueva);
    setEnviando(true);
    setError(null);
    try {
      setConfirmacion(
        await iniciarConfirmacion(await token(), {
          destino: OPERACION_REVELAR_PAN + tarjeta.ultimos4,
          importe: IMPORTE_SIMBOLICO_DE_REVELACION,
          moneda: "PEN",
          claveIdempotencia: nueva,
        }),
      );
    } catch (fallo) {
      setError(mensajeDe(fallo));
    } finally {
      setEnviando(false);
    }
  }

  async function verificar(codigo: string) {
    if (!confirmacion || !clave) return;
    setEnviando(true);
    setError(null);
    try {
      const vigente = await token();
      const confirmada = await verificarConfirmacion(vigente, confirmacion.confirmacionId, codigo);
      setRevelado(await revelarPan(vigente, clave, confirmada.token));
      setConfirmacion(null);
    } catch (fallo) {
      setError(mensajeDe(fallo));
    } finally {
      setEnviando(false);
    }
  }

  return (
    <section
      aria-labelledby="titulo-tarjeta-virtual"
      className="flex flex-col gap-4 rounded-[20px] border border-azul-200 bg-blanco p-6 md:flex-row md:items-start md:gap-8"
    >
      <TarjetaAyni
        titular="DÉBITO VIRTUAL"
        ultimos4={tarjeta.ultimos4}
        inicio="4999"
        vencimiento={tarjeta.vencimiento}
      />
      <div className="flex flex-1 flex-col gap-3">
        <h2 id="titulo-tarjeta-virtual" className="text-h3 font-bold text-azul-900">
          Tu tarjeta de débito virtual
        </h2>
        <p className="text-small text-gris-700">
          {tarjeta.numeroEnmascarado} · vence {tarjeta.vencimiento} ·{" "}
          {tarjeta.estado === "ACTIVA" ? "Activa" : "Congelada"}
        </p>

        {error && (
          <p
            role="alert"
            className="rounded-lg border border-error bg-blanco p-3 text-small text-error"
          >
            {error}
          </p>
        )}

        <div aria-live="polite" className="flex flex-col gap-2">
          {cvv && (
            <p className="text-body text-gris-900">
              CVV <strong className="cifra tracking-widest">{cvv.cvv}</strong>{" "}
              <span className="text-small text-gris-700">
                (cambia a las {HORA.format(new Date(cvv.validoHasta))})
              </span>
            </p>
          )}
          {revelado && (
            <p className="text-body text-gris-900">
              Número completo <strong className="cifra tracking-wider">{revelado.numero}</strong>{" "}
              <span className="text-small text-gris-700">(se ocultará en unos segundos)</span>
            </p>
          )}
        </div>

        {confirmacion ? (
          <PasoDeConfirmacion
            metodo={confirmacion.metodo}
            onVerificar={verificar}
            onCancelar={() => setConfirmacion(null)}
            enviando={enviando}
            error={null}
            textoDeConfirmar="Confirmar y ver el número"
          />
        ) : (
          <div className="flex flex-col gap-3 sm:flex-row">
            <Boton type="button" variante="contorno" onClick={verCvv}>
              <KeyRound aria-hidden="true" className="h-4 w-4" />
              Ver CVV
            </Boton>
            <Boton
              type="button"
              variante="contorno"
              onClick={pedirConfirmacion}
              cargando={enviando}
            >
              <Eye aria-hidden="true" className="h-4 w-4" />
              Ver número completo
            </Boton>
          </div>
        )}
      </div>
    </section>
  );
}

function mensajeDe(fallo: unknown): string {
  if (!(fallo instanceof ErrorDeApi)) {
    return "No pudimos completar la operación. Inténtalo de nuevo.";
  }
  if (fallo.estado === 403 || fallo.estado === 410) {
    return "Tu confirmación ya no es válida. Vuelve a pedir el número completo.";
  }
  if (fallo.estado === 409) {
    return "Activa tu segundo factor en Configuración para ver el número completo.";
  }
  return fallo.problema.detail ?? fallo.message;
}
