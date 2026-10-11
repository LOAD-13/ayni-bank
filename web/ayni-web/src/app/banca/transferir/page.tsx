"use client";

import { useState, type FormEvent } from "react";

import { Boton } from "@/componentes/Boton";
import { CampoDeTexto } from "@/componentes/CampoDeTexto";
import { MarcoDeOperacion } from "@/componentes/banca/MarcoDeOperacion";
import { useSesion } from "@/componentes/banca/SesionDeBanca";
import { PasoDeConfirmacion } from "@/componentes/banca/PasoDeConfirmacion";
import { TarjetaDeComprobante } from "@/componentes/banca/TarjetaDeComprobante";
import {
  ErrorDeApi,
  iniciarConfirmacion,
  transferir,
  verificarConfirmacion,
  type Comprobante,
  type ConfirmacionIniciada,
} from "@/lib/api";
import { formatearImporte, normalizarImporte, nuevaClave } from "@/lib/formato";

interface Borrador {
  cuentaDestino: string;
  importe: string;
  concepto: string;
}

/**
 * Transferencia entre cuentas Ayni · HU-07. Cuatro pasos: datos, revisión, código del
 * segundo factor y comprobante. El código lo comprueba identity, que devuelve un token atado
 * a esta operación exacta; sin él, core-banking no mueve el dinero (ADR-0031).
 *
 * La clave de idempotencia se genera al CONFIRMAR, una sola vez por operación: si la red
 * falla y la persona pulsa «Confirmar» otra vez, viaja la misma clave y el servidor
 * devuelve el comprobante de la primera en lugar de cobrar dos veces.
 */
export default function PaginaDeTransferencia() {
  const { token } = useSesion();
  const [borrador, setBorrador] = useState<Borrador | null>(null);
  const [clave, setClave] = useState<string | null>(null);
  const [comprobante, setComprobante] = useState<Comprobante | null>(null);
  const [errores, setErrores] = useState<Partial<Record<keyof Borrador, string>>>({});
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [confirmacion, setConfirmacion] = useState<ConfirmacionIniciada | null>(null);

  function revisar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    const datos = new FormData(evento.currentTarget);
    const cuentaDestino = String(datos.get("cuentaDestino") ?? "").replace(/[\s-]/g, "");
    const importe = normalizarImporte(String(datos.get("importe") ?? ""));
    const concepto = String(datos.get("concepto") ?? "").trim();

    const encontrados: Partial<Record<keyof Borrador, string>> = {};
    if (!/^\d{14}$/.test(cuentaDestino)) {
      encontrados.cuentaDestino = "El número de cuenta Ayni tiene 14 dígitos.";
    }
    if (!importe) {
      encontrados.importe = "Escribe un importe válido, con hasta dos decimales.";
    }
    setErrores(encontrados);
    if (Object.keys(encontrados).length > 0 || !importe) return;

    setError(null);
    setBorrador({ cuentaDestino, importe, concepto });
    setClave(nuevaClave());
  }

  /** Paso 3: pide a identity la confirmación; si el método es el correo, llega el código. */
  async function confirmar() {
    if (!borrador || !clave) return;
    setEnviando(true);
    setError(null);
    try {
      setConfirmacion(
        await iniciarConfirmacion(await token(), {
          destino: borrador.cuentaDestino,
          importe: borrador.importe,
          moneda: "PEN",
          claveIdempotencia: clave,
        }),
      );
    } catch (fallo) {
      setError(mensajeDe(fallo));
    } finally {
      setEnviando(false);
    }
  }

  /** Paso 4: el código da el token de confirmación, y con él se transfiere. */
  async function verificar(codigo: string) {
    if (!borrador || !clave || !confirmacion) return;
    setEnviando(true);
    setError(null);
    try {
      const vigente = await token();
      const confirmada = await verificarConfirmacion(vigente, confirmacion.confirmacionId, codigo);
      setComprobante(
        await transferir(
          vigente,
          borrador.cuentaDestino,
          borrador.importe,
          borrador.concepto,
          clave,
          confirmada.token,
        ),
      );
    } catch (fallo) {
      // Si la confirmación ya no sirve, hay que pedir otra; la clave se conserva para que
      // un reintento de una transferencia ya hecha devuelva su comprobante.
      if (fallo instanceof ErrorDeApi && (fallo.estado === 410 || fallo.estado === 403)) {
        setConfirmacion(null);
      }
      setError(mensajeDe(fallo));
    } finally {
      setEnviando(false);
    }
  }

  if (comprobante) {
    return <TarjetaDeComprobante comprobante={comprobante} />;
  }

  return (
    <MarcoDeOperacion
      titulo="Transferir"
      subtitulo="A otra cuenta Ayni, al instante y sin comisión."
      limite="Desde S/ 1.00 hasta S/ 5 000.00 por transferencia."
    >
      {error && !confirmacion && (
        <p
          role="alert"
          className="mt-5 rounded-lg border border-error bg-blanco p-4 text-body text-error"
        >
          {error}
        </p>
      )}

      {borrador && confirmacion ? (
        <PasoDeConfirmacion
          metodo={confirmacion.metodo}
          onVerificar={verificar}
          onCancelar={() => {
            setConfirmacion(null);
            setError(null);
          }}
          enviando={enviando}
          error={error}
        />
      ) : borrador ? (
        <section
          aria-labelledby="titulo-confirmar"
          className="mt-6 rounded-[20px] border border-azul-200 bg-blanco p-7"
        >
          <h2 id="titulo-confirmar" className="text-h3 font-bold text-azul-900">
            Confirma la transferencia
          </h2>
          <dl className="mt-4 divide-y divide-azul-100">
            <Fila clave="Cuenta de destino" valor={borrador.cuentaDestino} />
            <Fila clave="Importe" valor={formatearImporte(borrador.importe)} />
            <Fila clave="Concepto" valor={borrador.concepto || "Transferencia"} />
          </dl>
          <div className="mt-6 flex flex-col gap-3 sm:flex-row">
            <Boton type="button" onClick={confirmar} cargando={enviando}>
              Confirmar con mi segundo factor
            </Boton>
            <Boton
              type="button"
              variante="contorno"
              disabled={enviando}
              onClick={() => {
                setBorrador(null);
                setClave(null);
                setConfirmacion(null);
              }}
            >
              Corregir
            </Boton>
          </div>
        </section>
      ) : (
        <form
          onSubmit={revisar}
          noValidate
          className="mt-6 flex flex-col gap-5 rounded-[20px] border border-azul-200 bg-blanco p-7"
        >
          <CampoDeTexto
            etiqueta="Cuenta Ayni de destino"
            name="cuentaDestino"
            inputMode="numeric"
            autoComplete="off"
            placeholder="001-1000000001-0-01"
            ayuda="Los 14 dígitos de la cuenta, con o sin guiones."
            error={errores.cuentaDestino}
          />
          <CampoDeTexto
            etiqueta="Importe en soles"
            name="importe"
            inputMode="decimal"
            autoComplete="off"
            placeholder="0.00"
            error={errores.importe}
          />
          <CampoDeTexto
            etiqueta="Concepto (opcional)"
            name="concepto"
            maxLength={60}
            autoComplete="off"
          />
          <Boton type="submit">Continuar</Boton>
        </form>
      )}
    </MarcoDeOperacion>
  );
}

function Fila({ clave, valor }: { clave: string; valor: string }) {
  return (
    <div className="flex justify-between gap-6 py-3">
      <dt className="text-small text-gris-700">{clave}</dt>
      <dd className="text-right text-small font-semibold text-gris-900">{valor}</dd>
    </div>
  );
}

/** Mensajes para el cliente; los códigos vienen de identity y core-banking (ADR-0031). */
function mensajeDe(fallo: unknown): string {
  if (!(fallo instanceof ErrorDeApi)) {
    return "No pudimos completar la transferencia. Inténtalo de nuevo.";
  }
  const intentos = fallo.problema.intentosRestantes;
  switch (fallo.estado) {
    case 422:
      if (typeof intentos === "number") {
        return intentos > 0
          ? `El código no es correcto. Te quedan ${intentos} intento${intentos === 1 ? "" : "s"}.`
          : "El código no es correcto y se agotaron los intentos. Vuelve a confirmar.";
      }
      return fallo.problema.detail ?? fallo.message;
    case 410:
      return "La confirmación venció. Vuelve a confirmar la transferencia.";
    case 403:
      return "Tu confirmación ya no es válida. Vuelve a confirmar la transferencia.";
    case 409:
      return "Activa tu segundo factor en Configuración para poder transferir.";
    case 423:
      return "Pausamos las operaciones unos minutos por varios códigos incorrectos.";
    default:
      return fallo.problema.detail ?? fallo.message;
  }
}
