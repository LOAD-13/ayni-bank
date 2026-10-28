"use client";

import { useEffect, useState, type FormEvent } from "react";
import { useSesion } from "@/componentes/banca/SesionDeBanca";
import { normalizarImporte } from "@/lib/formato";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

type Beneficiario = {
  id: string;
  alias: string;
  cci: string;
};

type Transferencia = {
  id: string;
  estado: "PENDIENTE" | "COMPLETADA" | "RECHAZADA";
};

export default function PaginaInterbancaria() {
  const { usuarioId, token } = useSesion();

  const [cci, setCci] = useState("");
  const [importe, setImporte] = useState("");
  const [concepto, setConcepto] = useState("");
  const [alias, setAlias] = useState("");

  const [cciValido, setCciValido] = useState(false);
  const [validando, setValidando] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [mensaje, setMensaje] = useState("");
  const [error, setError] = useState("");

  const [beneficiarios, setBeneficiarios] = useState<Beneficiario[]>([]);

  const [clave, setClave] = useState("");

  useEffect(() => {
    setClave(crypto.randomUUID());
  }, []);

  // Validar el CCI desde el backend.
  useEffect(() => {
    setCciValido(false);

    if (!/^\d{20}$/.test(cci)) {
      setValidando(false);
      return;
    }

    const controller = new AbortController();

    async function validar() {
      setValidando(true);

      try {
        const jwt = await token();

        const respuesta = await fetch(`${API_URL}/api/v1/interbancarias/validar-cci/${cci}`, {
          headers: {
            Authorization: `Bearer ${jwt}`,
          },
          signal: controller.signal,
        });

        if (!respuesta.ok) {
          throw new Error("Error al validar");
        }

        const resultado: boolean = await respuesta.json();

        if (!controller.signal.aborted) {
          setCciValido(resultado === true);
        }
      } catch {
        if (!controller.signal.aborted) {
          setCciValido(false);
        }
      } finally {
        if (!controller.signal.aborted) {
          setValidando(false);
        }
      }
    }

    void validar();

    return () => controller.abort();
  }, [cci, token]);

  // Consultar beneficiarios frecuentes.
  useEffect(() => {
    if (!usuarioId) return;

    const controller = new AbortController();

    async function cargar() {
      try {
        const jwt = await token();

        const respuesta = await fetch(
          `${API_URL}/api/v1/interbancarias/beneficiarios?usuarioId=${usuarioId}`,
          {
            headers: {
              Authorization: `Bearer ${jwt}`,
            },
            signal: controller.signal,
          },
        );

        if (!respuesta.ok) return;

        const datos: Beneficiario[] = await respuesta.json();

        if (!controller.signal.aborted) {
          setBeneficiarios(datos);
        }
      } catch {
        // Se mantiene la pantalla disponible.
      }
    }

    void cargar();

    return () => controller.abort();
  }, [usuarioId, token]);

  // Guardar beneficiario frecuente.
  async function guardarBeneficiario() {
    if (!usuarioId || !cciValido || !alias.trim()) return;

    setError("");

    try {
      const jwt = await token();

      const respuesta = await fetch(`${API_URL}/api/v1/interbancarias/beneficiarios`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${jwt}`,
        },
        body: JSON.stringify({
          usuarioId,
          alias: alias.trim(),
          cci,
        }),
      });

      if (!respuesta.ok) throw new Error();

      const guardado: Beneficiario = await respuesta.json();

      setBeneficiarios((actuales) => [...actuales.filter((b) => b.cci !== guardado.cci), guardado]);

      setAlias("");
      setMensaje("Beneficiario guardado correctamente.");
    } catch {
      setError("No se pudo guardar el beneficiario.");
    }
  }

  // Eliminar beneficiario frecuente.
  async function eliminarBeneficiario(id: string) {
    if (!usuarioId) return;

    try {
      const jwt = await token();

      const respuesta = await fetch(
        `${API_URL}/api/v1/interbancarias/beneficiarios/${id}?usuarioId=${usuarioId}`,
        {
          method: "DELETE",
          headers: {
            Authorization: `Bearer ${jwt}`,
          },
        },
      );

      if (!respuesta.ok) throw new Error();

      setBeneficiarios((actuales) => actuales.filter((b) => b.id !== id));

      setMensaje("Beneficiario eliminado.");
    } catch {
      setError("No se pudo eliminar el beneficiario.");
    }
  }

  // Enviar transferencia interbancaria.
  async function transferir(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();

    if (!usuarioId || !clave || !cciValido || enviando) {
      return;
    }

    const monto = normalizarImporte(importe);

    if (monto === null || Number(monto) < 1 || Number(monto) > 5000) {
      setError("El importe debe estar entre S/ 1 y S/ 5,000.");
      return;
    }

    setEnviando(true);
    setError("");
    setMensaje("");

    try {
      const jwt = await token();

      const respuesta = await fetch(`${API_URL}/api/v1/interbancarias`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${jwt}`,
        },
        body: JSON.stringify({
          usuarioId,
          clave,
          cci,
          importe: monto,
          concepto: concepto.trim(),
        }),
      });

      if (!respuesta.ok) throw new Error();

      const resultado: Transferencia = await respuesta.json();

      setMensaje(`Transferencia ${resultado.estado}. Operacion: ${resultado.id}`);

      setClave(crypto.randomUUID());
      setImporte("");
      setConcepto("");
    } catch {
      setError("No se pudo confirmar la operacion. " + "Consulta su estado antes de reintentar.");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <main className="mx-auto max-w-2xl p-6">
      <h1 className="mb-2 text-2xl font-bold">Transferencia interbancaria</h1>

      <p className="mb-6 text-gray-600">Envia dinero a otro banco mediante CCI.</p>

      {error && (
        <p role="alert" className="mb-4 text-red-600">
          {error}
        </p>
      )}

      {mensaje && (
        <p role="status" className="mb-4 text-green-700">
          {mensaje}
        </p>
      )}

      <form onSubmit={transferir} className="space-y-5 rounded-xl border bg-white p-6">
        <div>
          <label htmlFor="cci" className="block font-medium">
            CCI del destinatario
          </label>

          <input
            id="cci"
            type="text"
            inputMode="numeric"
            maxLength={20}
            required
            value={cci}
            onChange={(e) => setCci(e.target.value.replace(/\D/g, ""))}
            placeholder="20 digitos del CCI"
            className="w-full rounded-lg border p-3"
          />

          <p role="status" className="mt-2 text-sm">
            {validando ? "Validando CCI..." : cciValido ? "CCI valido" : "Ingrese un CCI valido"}
          </p>
        </div>

        <div>
          <label htmlFor="beneficiario" className="block font-medium">
            Beneficiarios frecuentes
          </label>

          <select
            id="beneficiario"
            value=""
            onChange={(e) => setCci(e.target.value)}
            className="w-full rounded-lg border p-3"
          >
            <option value="">Seleccionar beneficiario</option>

            {beneficiarios.map((b) => (
              <option key={b.id} value={b.cci}>
                {b.alias} - {b.cci}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="alias" className="block font-medium">
            Alias del beneficiario
          </label>

          <div className="flex gap-2">
            <input
              id="alias"
              type="text"
              maxLength={80}
              value={alias}
              onChange={(e) => setAlias(e.target.value)}
              placeholder="Nombre o alias"
              className="min-w-0 flex-1 rounded-lg border p-3"
            />

            <button
              type="button"
              onClick={guardarBeneficiario}
              disabled={!cciValido || !alias.trim()}
              className="rounded-lg bg-blue-800 px-4 text-white disabled:opacity-50"
            >
              Guardar
            </button>
          </div>
        </div>

        {beneficiarios.map((b) => (
          <div key={b.id} className="flex justify-between gap-2 rounded border p-2">
            <span className="text-sm">
              {b.alias} - {b.cci}
            </span>

            <button
              type="button"
              onClick={() => eliminarBeneficiario(b.id)}
              className="text-red-600"
            >
              Eliminar
            </button>
          </div>
        ))}

        <div>
          <label htmlFor="importe" className="block font-medium">
            Importe (S/)
          </label>

          <input
            id="importe"
            type="number"
            min="1"
            max="5000"
            step="0.01"
            required
            value={importe}
            onChange={(e) => setImporte(e.target.value)}
            placeholder="0.00"
            className="w-full rounded-lg border p-3"
          />
        </div>

        <div>
          <label htmlFor="concepto" className="block font-medium">
            Concepto
          </label>

          <input
            id="concepto"
            type="text"
            maxLength={140}
            value={concepto}
            onChange={(e) => setConcepto(e.target.value)}
            placeholder="Motivo de la transferencia"
            className="w-full rounded-lg border p-3"
          />
        </div>

        <button
          type="submit"
          disabled={!cciValido || validando || enviando || !clave}
          className="w-full rounded-lg bg-blue-800 p-3 font-semibold text-white disabled:opacity-50"
        >
          {enviando ? "Procesando transferencia..." : "Realizar transferencia"}
        </button>
      </form>
    </main>
  );
}
