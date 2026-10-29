"use client";

import Link from "next/link";
import { useEffect, useRef, useState, type FormEvent } from "react";

import { Boton } from "@/componentes/Boton";
import { CampoDeTexto } from "@/componentes/CampoDeTexto";
import { IndicadorDeProgreso, PASOS_DE_UNA_TRANSFERENCIA } from "@/componentes/IndicadorDeProgreso";
import { MarcoDeOperacion } from "@/componentes/banca/MarcoDeOperacion";
import { PasoDeConfirmacion } from "@/componentes/banca/PasoDeConfirmacion";
import { useSesion } from "@/componentes/banca/SesionDeBanca";
import {
  ErrorDeApi,
  beneficiariosFrecuentes,
  borrarBeneficiario,
  guardarBeneficiario,
  iniciarConfirmacion,
  transferirInterbancaria,
  validarCci,
  verificarConfirmacion,
  type Beneficiario,
  type ConfirmacionIniciada,
  type TransferenciaInterbancaria,
} from "@/lib/api";
import { formatearImporte, normalizarImporte, nuevaClave } from "@/lib/formato";

interface Borrador {
  cci: string;
  importe: string;
  concepto: string;
}

type EstadoDelCci = "vacio" | "incompleto" | "validando" | "valido" | "invalido";

/** Espera desde la última tecla antes de preguntar al servidor si el CCI es válido. */
const ESPERA_DE_VALIDACION_MS = 400;

/**
 * Transferencia a otro banco por CCI · HU-11. Los mismos cuatro pasos que la transferencia
 * entre cuentas Ayni (datos, revisión, segundo factor y resultado), porque mueve dinero igual.
 *
 * El CCI se valida mientras se escribe, con una espera de 400 ms para no consultar en cada
 * tecla; si llega una respuesta de un CCI que ya no es el escrito, se descarta. El servidor lo
 * vuelve a validar al enviar: la validación del navegador no es un control de seguridad.
 */
export default function PaginaInterbancaria() {
  const { token } = useSesion();
  const [cci, setCci] = useState("");
  const [estadoDelCci, setEstadoDelCci] = useState<EstadoDelCci>("vacio");
  const [borrador, setBorrador] = useState<Borrador | null>(null);
  const [escrito, setEscrito] = useState<Borrador | null>(null);
  const [errores, setErrores] = useState<Partial<Record<keyof Borrador, string>>>({});
  const [clave, setClave] = useState<string | null>(null);
  const [confirmacion, setConfirmacion] = useState<ConfirmacionIniciada | null>(null);
  const [resultado, setResultado] = useState<TransferenciaInterbancaria | null>(null);
  const [beneficiarios, setBeneficiarios] = useState<Beneficiario[]>([]);
  const [alias, setAlias] = useState("");
  const [aviso, setAviso] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const consulta = useRef(0);

  useEffect(() => {
    let vigente = true;
    void token()
      .then((t) => beneficiariosFrecuentes(t))
      .then((lista) => vigente && setBeneficiarios(lista))
      .catch(() => {
        // Sin beneficiarios la transferencia sigue disponible.
      });
    return () => {
      vigente = false;
    };
  }, [token]);

  useEffect(() => {
    const limpio = cci.replace(/[\s-]/g, "");
    if (!limpio) {
      setEstadoDelCci("vacio");
      return;
    }
    if (!/^\d{20}$/.test(limpio)) {
      setEstadoDelCci("incompleto");
      return;
    }
    setEstadoDelCci("validando");
    const numero = ++consulta.current;
    const espera = setTimeout(() => {
      void token()
        .then((t) => validarCci(t, limpio))
        .then((valido) => {
          if (numero === consulta.current) setEstadoDelCci(valido ? "valido" : "invalido");
        })
        .catch(() => {
          if (numero === consulta.current) setEstadoDelCci("incompleto");
        });
    }, ESPERA_DE_VALIDACION_MS);
    return () => clearTimeout(espera);
  }, [cci, token]);

  function revisar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    const datos = new FormData(evento.currentTarget);
    const tecleado = {
      cci,
      importe: String(datos.get("importe") ?? ""),
      concepto: String(datos.get("concepto") ?? ""),
    };
    setEscrito(tecleado);
    const limpio = cci.replace(/[\s-]/g, "");
    const importe = normalizarImporte(tecleado.importe);

    const encontrados: Partial<Record<keyof Borrador, string>> = {};
    if (estadoDelCci !== "valido") {
      encontrados.cci = "Revisa el CCI: son 20 dígitos y los dos últimos lo validan.";
    }
    if (!importe) {
      encontrados.importe = "Escribe un importe válido, con hasta dos decimales.";
    }
    setErrores(encontrados);
    if (Object.keys(encontrados).length > 0 || !importe) return;

    setError(null);
    setBorrador({ cci: limpio, importe, concepto: tecleado.concepto.trim() });
    setClave(nuevaClave());
  }

  async function confirmar() {
    if (!borrador || !clave) return;
    setEnviando(true);
    setError(null);
    try {
      setConfirmacion(
        await iniciarConfirmacion(await token(), {
          destino: borrador.cci,
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

  async function verificar(codigo: string) {
    if (!borrador || !clave || !confirmacion) return;
    setEnviando(true);
    setError(null);
    try {
      const vigente = await token();
      const confirmada = await verificarConfirmacion(vigente, confirmacion.confirmacionId, codigo);
      setResultado(
        await transferirInterbancaria(
          vigente,
          borrador.cci,
          borrador.importe,
          borrador.concepto,
          clave,
          confirmada.token,
        ),
      );
    } catch (fallo) {
      // La clave se conserva: reintentar una transferencia ya hecha devuelve su resultado.
      if (fallo instanceof ErrorDeApi && (fallo.estado === 410 || fallo.estado === 403)) {
        setConfirmacion(null);
      }
      setError(mensajeDe(fallo));
    } finally {
      setEnviando(false);
    }
  }

  async function guardar() {
    const limpio = cci.replace(/[\s-]/g, "");
    if (estadoDelCci !== "valido" || !alias.trim()) return;
    setError(null);
    try {
      const guardado = await guardarBeneficiario(await token(), alias.trim(), limpio);
      setBeneficiarios((lista) => [...lista.filter((b) => b.cci !== guardado.cci), guardado]);
      setAlias("");
      setAviso(`Guardamos a ${guardado.alias} entre tus beneficiarios.`);
    } catch (fallo) {
      setError(mensajeDe(fallo));
    }
  }

  async function borrar(beneficiario: Beneficiario) {
    setError(null);
    try {
      await borrarBeneficiario(await token(), beneficiario.id);
      setBeneficiarios((lista) => lista.filter((b) => b.id !== beneficiario.id));
      setAviso(`Quitamos a ${beneficiario.alias} de tus beneficiarios.`);
    } catch (fallo) {
      setError(mensajeDe(fallo));
    }
  }

  const pasos = (
    <IndicadorDeProgreso
      pasos={PASOS_DE_UNA_TRANSFERENCIA}
      pasoActual={resultado ? 4 : confirmacion ? 3 : borrador ? 2 : 1}
    />
  );

  if (resultado) {
    return (
      <div className="flex flex-col gap-6">
        <div className="mx-auto w-full max-w-2xl">{pasos}</div>
        <Resultado resultado={resultado} />
      </div>
    );
  }

  return (
    <MarcoDeOperacion
      titulo="Transferir a otro banco"
      subtitulo="Con el CCI de 20 dígitos de la cuenta de destino."
      limite="Desde S/ 1.00 hasta S/ 5 000.00 por transferencia."
    >
      {pasos}
      {error && !confirmacion && (
        <p
          role="alert"
          className="mt-5 rounded-lg border border-error bg-blanco p-4 text-body text-error"
        >
          {error}
        </p>
      )}
      {aviso && (
        <p role="status" className="mt-5 text-small text-gris-700">
          {aviso}
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
            <Fila clave="CCI de destino" valor={agrupar(borrador.cci)} />
            <Fila clave="Importe" valor={formatearImporte(borrador.importe)} />
            <Fila clave="Concepto" valor={borrador.concepto || "Transferencia interbancaria"} />
          </dl>
          <p className="mt-4 text-small text-gris-700">
            El dinero sale de tu cuenta al confirmar. Si el otro banco no la acepta, te lo
            devolvemos y te avisamos por correo.
          </p>
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
        <>
          {beneficiarios.length > 0 && (
            <section aria-labelledby="titulo-beneficiarios" className="mt-6">
              <h2 id="titulo-beneficiarios" className="text-small font-semibold text-gris-900">
                Tus beneficiarios frecuentes
              </h2>
              <ul className="mt-3 flex flex-col gap-2">
                {beneficiarios.map((b) => (
                  <li
                    key={b.id}
                    className="flex items-center justify-between gap-3 rounded-lg border border-azul-200 bg-blanco px-4 py-3"
                  >
                    <button
                      type="button"
                      onClick={() => setCci(b.cci)}
                      className="text-left text-small text-azul-900 underline-offset-2 hover:underline"
                    >
                      {b.alias} · termina en {b.cci.slice(-4)}
                    </button>
                    <button
                      type="button"
                      onClick={() => borrar(b)}
                      className="text-small text-gris-700 hover:text-error"
                      aria-label={`Quitar a ${b.alias} de tus beneficiarios`}
                    >
                      Quitar
                    </button>
                  </li>
                ))}
              </ul>
            </section>
          )}
          <form
            onSubmit={revisar}
            noValidate
            className="mt-6 flex flex-col gap-5 rounded-[20px] border border-azul-200 bg-blanco p-7"
          >
            <CampoDeTexto
              etiqueta="CCI de destino"
              name="cci"
              inputMode="numeric"
              autoComplete="off"
              placeholder="002-193-000012345678-86"
              ayuda={ayudaDelCci(estadoDelCci)}
              value={cci}
              onChange={(e) => setCci(e.target.value)}
              error={
                errores.cci ?? (estadoDelCci === "invalido" ? ayudaDelCci("invalido") : undefined)
              }
            />
            <CampoDeTexto
              etiqueta="Importe en soles"
              name="importe"
              inputMode="decimal"
              autoComplete="off"
              placeholder="0.00"
              defaultValue={escrito?.importe}
              error={errores.importe}
            />
            <CampoDeTexto
              etiqueta="Concepto (opcional)"
              name="concepto"
              maxLength={60}
              autoComplete="off"
              defaultValue={escrito?.concepto}
            />
            {estadoDelCci === "valido" && (
              <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
                <div className="flex-1">
                  <CampoDeTexto
                    etiqueta="Guardar como beneficiario (opcional)"
                    name="alias"
                    maxLength={80}
                    autoComplete="off"
                    placeholder="Mamá, Alquiler…"
                    value={alias}
                    onChange={(e) => setAlias(e.target.value)}
                  />
                </div>
                <Boton type="button" variante="contorno" disabled={!alias.trim()} onClick={guardar}>
                  Guardar
                </Boton>
              </div>
            )}
            <Boton type="submit">Continuar</Boton>
          </form>
        </>
      )}
    </MarcoDeOperacion>
  );
}

function Resultado({ resultado }: { resultado: TransferenciaInterbancaria }) {
  const textos = {
    COMPLETADA: {
      titulo: "Transferencia enviada",
      cuerpo: "El otro banco ya la aceptó. Te enviamos el detalle por correo.",
    },
    PENDIENTE: {
      titulo: "Transferencia en camino",
      cuerpo:
        "El otro banco todavía no la confirma. Te avisaremos por correo; si no la acepta, te devolvemos el dinero.",
    },
    RECHAZADA: {
      titulo: "El otro banco no aceptó la transferencia",
      cuerpo: "Te devolvimos el dinero a tu cuenta. Revisa el CCI con quien te lo dio.",
    },
  }[resultado.estado];
  return (
    <section
      aria-labelledby="titulo-resultado"
      className="mx-auto mt-10 max-w-xl rounded-[20px] border border-azul-200 bg-blanco p-7"
    >
      <h1 id="titulo-resultado" className="text-h3 font-bold text-azul-900">
        {textos.titulo}
      </h1>
      <p className="mt-3 text-body text-gris-700">{textos.cuerpo}</p>
      <p className="mt-3 text-small text-gris-700">
        Operación {resultado.id.slice(0, 8).toUpperCase()}
      </p>
      <Link
        href="/banca"
        className="mt-6 inline-block rounded-lg bg-azul-700 px-5 py-3 text-body font-semibold text-blanco"
      >
        Volver a mi banca
      </Link>
    </section>
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

function ayudaDelCci(estado: EstadoDelCci): string {
  switch (estado) {
    case "validando":
      return "Comprobando el CCI…";
    case "valido":
      return "CCI válido.";
    case "invalido":
      return "Este CCI no es válido: revisa los dos últimos dígitos.";
    default:
      return "Los 20 dígitos del CCI, con o sin guiones.";
  }
}

/** 00219300001234567886 → 002-193-000012345678-86, como lo muestran los bancos. */
function agrupar(cci: string): string {
  return cci.replace(/^(\d{3})(\d{3})(\d{12})(\d{2})$/, "$1-$2-$3-$4");
}

/** Mensajes para el cliente a partir de los códigos de identity y core-banking. */
function mensajeDe(fallo: unknown): string {
  if (!(fallo instanceof ErrorDeApi)) {
    return "No pudimos completar la transferencia. Inténtalo de nuevo.";
  }
  const intentos = fallo.problema.intentosRestantes;
  if (fallo.estado === 422 && typeof intentos === "number") {
    return intentos > 0
      ? `El código no es correcto. Te quedan ${intentos} intento${intentos === 1 ? "" : "s"}.`
      : "El código no es correcto y se agotaron los intentos. Vuelve a confirmar.";
  }
  switch (fallo.estado) {
    case 410:
      return "La confirmación venció. Vuelve a confirmar la transferencia.";
    case 403:
      return "Tu confirmación ya no es válida. Vuelve a confirmar la transferencia.";
    case 423:
      return "Pausamos las operaciones unos minutos por varios códigos incorrectos.";
    default:
      return fallo.problema.detail ?? fallo.message;
  }
}
