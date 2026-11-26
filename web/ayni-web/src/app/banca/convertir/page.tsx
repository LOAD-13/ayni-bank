"use client";

import { ArrowRight } from "lucide-react";
import Link from "next/link";
import { useEffect, useRef, useState, type FormEvent } from "react";

import { Boton } from "@/componentes/Boton";
import { CampoDeTexto } from "@/componentes/CampoDeTexto";
import { AvisoDeTipoDeCambio } from "@/componentes/banca/AvisoDeTipoDeCambio";
import { MarcoDeOperacion } from "@/componentes/banca/MarcoDeOperacion";
import { useSesion } from "@/componentes/banca/SesionDeBanca";
import {
  abrirCuentaEnDolares,
  consultarMiCuenta,
  convertirMoneda,
  cotizarConversion,
  ErrorDeApi,
  type ConversionRealizada,
  type CotizacionDeConversion,
  type CuentaAbierta,
  type MonedaDeCuenta,
} from "@/lib/api";
import { formatearFecha, formatearImporte, normalizarImporte, nuevaClave } from "@/lib/formato";

const ID_DEL_AVISO = "aviso-tipo-de-cambio";

const NOMBRE: Record<MonedaDeCuenta, string> = { PEN: "soles", USD: "dólares" };

/** El código del segmento tal como lo guarda el servidor, en palabras para el cliente. */
const TARIFA: Record<string, string> = { ESTANDAR: "estándar", PREFERENCIAL: "preferencial" };

/**
 * Cambio entre la cuenta en soles y la cuenta en dólares (HU-12, AYNI-274).
 *
 * Primero se simula: el servidor cotiza sin mover dinero y la pantalla enseña el tipo que se
 * aplicaría, la referencia oficial de la que sale y el margen del banco. Al confirmar se envía
 * el tipo que el cliente vio; si ya no es ese, el servidor no convierte y se vuelve a
 * mostrar el nuevo. Si el tipo es el último conocido y no el de hoy, el aviso queda enlazado
 * al botón de confirmar para que el lector de pantalla lo repita justo al decidir.
 */
export default function PaginaDeConversion() {
  const { token } = useSesion();
  const [soles, setSoles] = useState<CuentaAbierta | null>(null);
  const [dolares, setDolares] = useState<CuentaAbierta | null>(null);
  const [cargando, setCargando] = useState(true);
  const [errorDeCarga, setErrorDeCarga] = useState<string | null>(null);

  const [origen, setOrigen] = useState<MonedaDeCuenta>("PEN");
  const [importe, setImporte] = useState("");
  const [errorDeCampo, setErrorDeCampo] = useState<string | undefined>();
  const [cotizacion, setCotizacion] = useState<CotizacionDeConversion | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [trabajando, setTrabajando] = useState(false);
  const [hecha, setHecha] = useState<ConversionRealizada | null>(null);
  // Una clave por conversión concreta —sentido, importe y tipo—, no por clic: si la red falla
  // y se vuelve a confirmar lo mismo, viaja la misma clave y el servidor devuelve la conversión
  // ya hecha en lugar de cambiar el dinero dos veces.
  const clave = useRef<{ huella: string; valor: string } | null>(null);
  const claveDeApertura = useRef(nuevaClave());

  async function cargarCuentas() {
    setCargando(true);
    setErrorDeCarga(null);
    try {
      const vigente = await token();
      setSoles(await consultarMiCuenta(vigente, "PEN"));
      try {
        setDolares(await consultarMiCuenta(vigente, "USD"));
      } catch (fallo) {
        // 404: todavía no abrió su cuenta en dólares; es el estado de partida, no un fallo.
        if (fallo instanceof ErrorDeApi && fallo.estado === 404) setDolares(null);
        else throw fallo;
      }
    } catch (fallo) {
      setErrorDeCarga(mensajeDe(fallo, "No pudimos cargar tus cuentas. Inténtalo de nuevo."));
    } finally {
      setCargando(false);
    }
  }

  useEffect(() => {
    void cargarCuentas();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function abrirCuenta() {
    setTrabajando(true);
    setError(null);
    try {
      setDolares(await abrirCuentaEnDolares(await token(), claveDeApertura.current));
    } catch (fallo) {
      setError(mensajeDe(fallo, "No pudimos abrir tu cuenta en dólares. Inténtalo de nuevo."));
    } finally {
      setTrabajando(false);
    }
  }

  function cambiarEntrada(nuevoOrigen: MonedaDeCuenta, nuevoImporte: string) {
    setOrigen(nuevoOrigen);
    setImporte(nuevoImporte);
    // Lo que se simuló ya no es lo que hay escrito.
    setCotizacion(null);
    setAviso(null);
    setError(null);
  }

  async function simular(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    const normalizado = normalizarImporte(importe);
    if (!normalizado) {
      setErrorDeCampo("Escribe un importe válido, con hasta dos decimales.");
      return;
    }
    setErrorDeCampo(undefined);
    setTrabajando(true);
    setError(null);
    setAviso(null);
    try {
      setCotizacion(await cotizarConversion(await token(), origen, normalizado));
    } catch (fallo) {
      setCotizacion(null);
      setError(mensajeDe(fallo, "No pudimos cotizar ahora. Inténtalo de nuevo en unos minutos."));
    } finally {
      setTrabajando(false);
    }
  }

  async function confirmar() {
    const normalizado = normalizarImporte(importe);
    if (!cotizacion || !normalizado) return;
    const huella = `${origen}|${normalizado}|${cotizacion.tipoAplicado}|${cotizacion.enCache}`;
    if (clave.current?.huella !== huella) clave.current = { huella, valor: nuevaClave() };
    setTrabajando(true);
    setError(null);
    setAviso(null);
    try {
      const vigente = await token();
      setHecha(
        await convertirMoneda(
          vigente,
          {
            origen,
            importe: normalizado,
            tipoMostrado: cotizacion.tipoAplicado,
            enCacheMostrado: cotizacion.enCache,
          },
          clave.current.valor,
        ),
      );
    } catch (fallo) {
      if (fallo instanceof ErrorDeApi && fallo.problema.codigo === "TIPO_DE_CAMBIO_CAMBIO") {
        // No se convirtió nada: se enseña el tipo nuevo y el cliente decide con él delante.
        setAviso("El tipo de cambio cambió mientras confirmabas. Revisa el nuevo tipo.");
        try {
          setCotizacion(await cotizarConversion(await token(), origen, normalizado));
        } catch {
          setCotizacion(null);
        }
      } else {
        setError(mensajeDe(fallo, "No pudimos completar la conversión. Inténtalo de nuevo."));
      }
    } finally {
      setTrabajando(false);
    }
  }

  function otra() {
    setHecha(null);
    setCotizacion(null);
    setImporte("");
    setAviso(null);
    clave.current = null;
    void cargarCuentas();
  }

  return (
    <MarcoDeOperacion
      titulo="Convertir"
      subtitulo="Cambia dinero entre tu cuenta en soles y tu cuenta en dólares."
      limite="Hasta S/ 5 000.00 por conversión, o su equivalente en dólares."
    >
      {cargando ? (
        <p role="status" className="text-body text-gris-700">
          Cargando tus cuentas…
        </p>
      ) : errorDeCarga ? (
        <div role="alert" className="flex flex-col gap-4 text-body text-error">
          <p>{errorDeCarga}</p>
          <Boton variante="contorno" onClick={() => void cargarCuentas()}>
            Reintentar
          </Boton>
        </div>
      ) : hecha ? (
        <Resultado conversion={hecha} alOtra={otra} />
      ) : !dolares ? (
        <SinCuentaEnDolares abrir={abrirCuenta} trabajando={trabajando} error={error} />
      ) : (
        <>
          {error && (
            <p
              role="alert"
              className="mb-5 rounded-lg border border-error bg-blanco p-4 text-body text-error"
            >
              {error}
            </p>
          )}
          <form onSubmit={simular} noValidate className="flex flex-col gap-5">
            <fieldset className="flex flex-col gap-2">
              <legend className="text-small font-medium text-gris-700">Quiero cambiar</legend>
              {(["PEN", "USD"] as const).map((moneda) => (
                <label key={moneda} className="flex min-h-[44px] items-center gap-3 text-body">
                  <input
                    type="radio"
                    name="origen"
                    value={moneda}
                    checked={origen === moneda}
                    onChange={() => cambiarEntrada(moneda, importe)}
                  />
                  {moneda === "PEN" ? "Soles a dólares" : "Dólares a soles"}
                </label>
              ))}
            </fieldset>
            <CampoDeTexto
              etiqueta={`Importe en ${NOMBRE[origen]}`}
              name="importe"
              inputMode="decimal"
              autoComplete="off"
              placeholder="0.00"
              value={importe}
              onChange={(e) => cambiarEntrada(origen, e.target.value)}
              error={errorDeCampo}
              ayuda={`Disponible: ${formatearImporte(
                (origen === "PEN" ? soles : dolares)?.saldo ?? "0.00",
                origen,
              )}`}
            />
            <Boton type="submit" variante="contorno" cargando={trabajando && !cotizacion}>
              Simular conversión
            </Boton>
          </form>

          {aviso && (
            <p role="status" className="mt-5 rounded-lg border border-dorado-300 p-4 text-body">
              {aviso}
            </p>
          )}

          {cotizacion && (
            <section aria-labelledby="titulo-simulacion" className="mt-6 flex flex-col gap-4">
              <h2 id="titulo-simulacion" className="text-h2 font-semibold text-azul-800">
                Así quedaría tu conversión
              </h2>
              <Detalle cotizacion={cotizacion} />
              <AvisoDeTipoDeCambio
                id={ID_DEL_AVISO}
                enCache={cotizacion.enCache}
                obtenidoEn={cotizacion.obtenidoEn}
              />
              <Boton
                onClick={() => void confirmar()}
                cargando={trabajando}
                aria-describedby={cotizacion.enCache ? ID_DEL_AVISO : undefined}
              >
                Confirmar conversión
              </Boton>
            </section>
          )}
        </>
      )}
    </MarcoDeOperacion>
  );
}

function Detalle({ cotizacion }: { cotizacion: CotizacionDeConversion }) {
  return (
    <dl className="grid grid-cols-[1fr_auto] gap-x-4 gap-y-2 text-body">
      <dt className="text-gris-700">Entregas</dt>
      <dd className="font-semibold">
        {formatearImporte(cotizacion.importeOrigen ?? "0.00", cotizacion.origen)}
      </dd>
      <dt className="text-gris-700">Recibes</dt>
      <dd className="font-semibold">
        {formatearImporte(cotizacion.importeDestino ?? "0.00", cotizacion.destino)}
      </dd>
      <dt className="text-gris-700">Tipo de cambio aplicado</dt>
      <dd>S/ {cotizacion.tipoAplicado} por US$ 1</dd>
      <dt className="text-gris-700">Tipo de referencia oficial</dt>
      <dd>S/ {cotizacion.tipoReferencia} por US$ 1</dd>
      <dt className="text-gris-700">Margen de Ayni</dt>
      <dd>
        {cotizacion.margen} % · tarifa {TARIFA[cotizacion.segmento] ?? cotizacion.segmento}
      </dd>
    </dl>
  );
}

function Resultado({
  conversion,
  alOtra,
}: {
  conversion: ConversionRealizada;
  alOtra: () => void;
}) {
  return (
    <section aria-labelledby="titulo-resultado" className="flex flex-col gap-4">
      <h2 id="titulo-resultado" className="text-h2 font-semibold text-azul-800">
        Conversión realizada
      </h2>
      <p role="status" className="text-body">
        Cambiaste {formatearImporte(conversion.importeOrigen, conversion.monedaOrigen)} y recibiste{" "}
        <strong>{formatearImporte(conversion.importeDestino, conversion.monedaDestino)}</strong>.
      </p>
      <dl className="grid grid-cols-[1fr_auto] gap-x-4 gap-y-2 text-body">
        <dt className="text-gris-700">Tipo de cambio aplicado</dt>
        <dd>S/ {conversion.tipoAplicado} por US$ 1</dd>
        <dt className="text-gris-700">Tipo de referencia oficial</dt>
        <dd>S/ {conversion.tipoReferencia} por US$ 1</dd>
        <dt className="text-gris-700">Margen de Ayni</dt>
        <dd>{conversion.margen} %</dd>
        <dt className="text-gris-700">Tipo obtenido</dt>
        <dd>{formatearFecha(conversion.tipoObtenidoEn)}</dd>
      </dl>
      <div className="flex flex-wrap gap-3">
        <Boton onClick={alOtra}>Hacer otra conversión</Boton>
        <Link
          href="/banca/movimientos"
          className="inline-flex min-h-[44px] items-center gap-2 px-2 text-small font-semibold text-azul-700 hover:underline"
        >
          Ver mis movimientos
          <ArrowRight aria-hidden="true" className="h-4 w-4" />
        </Link>
      </div>
    </section>
  );
}

function SinCuentaEnDolares({
  abrir,
  trabajando,
  error,
}: {
  abrir: () => void;
  trabajando: boolean;
  error: string | null;
}) {
  return (
    <section aria-labelledby="titulo-sin-cuenta" className="flex flex-col gap-4">
      <h2 id="titulo-sin-cuenta" className="text-h2 font-semibold text-azul-800">
        Aún no tienes cuenta en dólares
      </h2>
      <p className="text-body text-gris-700">
        Ábrela ahora, sin costo, para poder cambiar soles a dólares y al revés con el tipo de cambio
        oficial más un margen que verás antes de confirmar.
      </p>
      {error && (
        <p role="alert" className="text-body text-error">
          {error}
        </p>
      )}
      <Boton onClick={abrir} cargando={trabajando}>
        Abrir cuenta en dólares
      </Boton>
    </section>
  );
}

function mensajeDe(fallo: unknown, alternativo: string): string {
  return fallo instanceof ErrorDeApi ? (fallo.problema.detail ?? fallo.message) : alternativo;
}
