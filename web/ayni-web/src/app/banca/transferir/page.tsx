"use client";

import { useState, type FormEvent } from "react";

import { Boton } from "@/componentes/Boton";
import { CampoDeTexto } from "@/componentes/CampoDeTexto";
import { MarcoDeOperacion } from "@/componentes/banca/MarcoDeOperacion";
import { useSesion } from "@/componentes/banca/SesionDeBanca";
import { TarjetaDeComprobante } from "@/componentes/banca/TarjetaDeComprobante";
import { ErrorDeApi, transferir, type Comprobante } from "@/lib/api";
import { formatearImporte, normalizarImporte, nuevaClave } from "@/lib/formato";

interface Borrador {
  cuentaDestino: string;
  importe: string;
  concepto: string;
}

/**
 * Transferencia entre cuentas Ayni · HU-07. Tres pasos: datos, confirmación, comprobante.
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

  async function confirmar() {
    if (!borrador || !clave) return;
    setEnviando(true);
    setError(null);
    try {
      const vigente = await token();
      setComprobante(
        await transferir(
          vigente,
          borrador.cuentaDestino,
          borrador.importe,
          borrador.concepto,
          clave,
        ),
      );
    } catch (fallo) {
      setError(
        fallo instanceof ErrorDeApi
          ? (fallo.problema.detail ?? fallo.message)
          : "No pudimos completar la transferencia. Inténtalo de nuevo.",
      );
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
      {error && (
        <p
          role="alert"
          className="mt-5 rounded-lg border border-error bg-blanco p-4 text-body text-error"
        >
          {error}
        </p>
      )}

      {borrador ? (
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
              Confirmar y transferir
            </Boton>
            <Boton
              type="button"
              variante="contorno"
              disabled={enviando}
              onClick={() => {
                setBorrador(null);
                setClave(null);
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
