"use client";

import { Info } from "lucide-react";
import { useState, type FormEvent } from "react";

import { Boton } from "@/componentes/Boton";
import { CampoDeTexto } from "@/componentes/CampoDeTexto";
import { MarcoDeOperacion } from "@/componentes/banca/MarcoDeOperacion";
import { useSesion } from "@/componentes/banca/SesionDeBanca";
import { TarjetaDeComprobante } from "@/componentes/banca/TarjetaDeComprobante";
import { depositarSimulado, ErrorDeApi, type Comprobante } from "@/lib/api";
import { normalizarImporte, nuevaClave } from "@/lib/formato";

/**
 * Depósito simulado. En un banco real el dinero entraría por una transferencia
 * interbancaria (HU-11) o un agente; en el entorno académico se simula, pero con las mismas
 * reglas que cualquier movimiento: partida doble contra la cuenta técnica de fondeo,
 * idempotencia y un tope por operación.
 */
export default function PaginaDeDeposito() {
  const { token } = useSesion();
  const [comprobante, setComprobante] = useState<Comprobante | null>(null);
  const [errorDeCampo, setErrorDeCampo] = useState<string | undefined>();
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  async function depositar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    const importe = normalizarImporte(
      String(new FormData(evento.currentTarget).get("importe") ?? ""),
    );
    if (!importe) {
      setErrorDeCampo("Escribe un importe válido, con hasta dos decimales.");
      return;
    }
    setErrorDeCampo(undefined);
    setEnviando(true);
    setError(null);
    try {
      const vigente = await token();
      setComprobante(await depositarSimulado(vigente, importe, nuevaClave()));
    } catch (fallo) {
      setError(
        fallo instanceof ErrorDeApi
          ? (fallo.problema.detail ?? fallo.message)
          : "No pudimos completar el depósito. Inténtalo de nuevo.",
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
      titulo="Depositar"
      subtitulo="Añade saldo de prueba a tu cuenta Ayni."
      limite="Hasta S/ 2 000.00 por depósito simulado."
    >
      <p className="mt-5 flex gap-3 rounded-lg border border-azul-200 bg-blanco p-4 text-small text-gris-700">
        <Info aria-hidden="true" className="h-5 w-5 shrink-0 text-azul-700" />
        Entorno académico: el depósito es simulado, pero se registra como cualquier movimiento real,
        por partida doble y con su comprobante. Máximo S/ 2 000.00 por operación.
      </p>

      {error && (
        <p
          role="alert"
          className="mt-5 rounded-lg border border-error bg-blanco p-4 text-body text-error"
        >
          {error}
        </p>
      )}

      <form
        onSubmit={depositar}
        noValidate
        className="mt-6 flex flex-col gap-5 rounded-[20px] border border-azul-200 bg-blanco p-7"
      >
        <CampoDeTexto
          etiqueta="Importe en soles"
          name="importe"
          inputMode="decimal"
          autoComplete="off"
          placeholder="0.00"
          error={errorDeCampo}
        />
        <Boton type="submit" cargando={enviando}>
          Depositar
        </Boton>
      </form>
    </MarcoDeOperacion>
  );
}
