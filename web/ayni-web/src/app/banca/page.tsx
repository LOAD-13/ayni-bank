"use client";

import {
  ArrowLeftRight,
  ChevronRight,
  Copy,
  Eye,
  EyeOff,
  PiggyBank,
  ReceiptText,
  RefreshCw,
  ShieldCheck,
  Wallet,
  WifiOff,
} from "lucide-react";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import { Pista } from "@/componentes/Pista";
import { AsistenteDeBienvenida } from "@/componentes/banca/AsistenteDeBienvenida";
import { PanelDeRendimiento } from "@/componentes/banca/PanelDeRendimiento";
import { useSesion } from "@/componentes/banca/SesionDeBanca";
import { PISTAS_DEL_PANEL } from "@/componentes/banca/pistas";
import { ListaDeMovimientos } from "@/componentes/banca/ListaDeMovimientos";
import { TarjetaAyni } from "@/componentes/landing/TarjetaAyni";
import {
  consultarMiCuenta,
  consultarMovimientos,
  consultarRendimiento,
  consultarTarjeta,
  consultarTitular,
  ErrorDeApi,
  type CuentaAbierta,
  type Movimiento,
  type RendimientoDeCuenta,
  type TarjetaVirtual,
  type Titular,
} from "@/lib/api";
import { formatearImporte } from "@/lib/formato";

type Estado =
  | { tipo: "cargando" }
  | {
      tipo: "lista";
      cuenta: CuentaAbierta;
      movimientos: Movimiento[];
      rendimiento: RendimientoDeCuenta | null;
      tarjeta: TarjetaVirtual | null;
    }
  | { tipo: "sin-cuenta" }
  | { tipo: "error"; mensaje: string };

/**
 * Resumen de la banca · HU-08. Sigue el lienzo «7 · Banca por Internet · panel principal»
 * del diseño: saludo, saldo total con la cuenta y su TREA, tarjeta de débito, rendimiento,
 * accesos rápidos y últimos movimientos.
 *
 * Solo se muestra lo que existe de verdad: la tarjeta con sus cuatro últimos dígitos y el rendimiento estimado con la TREA vigente, rotulado como tal.
 */
export default function PanelDeBanca() {
  const { token, usuarioId } = useSesion();
  const [titular, setTitular] = useState<Titular | null>(null);
  const [estado, setEstado] = useState<Estado>({ tipo: "cargando" });
  const [oculto, setOculto] = useState(false);
  const [actualizado, setActualizado] = useState<Date | null>(null);

  const cargar = useCallback(async () => {
    setEstado({ tipo: "cargando" });
    try {
      const vigente = await token();
      // El rendimiento no bloquea el panel: si su consulta falla, se muestra la estimacion.
      const [cuenta, movimientos, rendimiento, tarjeta] = await Promise.all([
        consultarMiCuenta(vigente),
        consultarMovimientos(vigente, 10),
        consultarRendimiento(vigente).catch(() => null),
        consultarTarjeta(vigente).catch(() => null),
      ]);
      setEstado({ tipo: "lista", cuenta, movimientos, rendimiento, tarjeta });
      setActualizado(new Date());
    } catch (fallo) {
      if (fallo instanceof ErrorDeApi && fallo.problema.codigo === "SIN_CUENTA") {
        setEstado({ tipo: "sin-cuenta" });
        return;
      }
      setEstado({
        tipo: "error",
        mensaje:
          "El servicio de cuentas no respondió a tiempo. Tu dinero está seguro y tus movimientos no se vieron afectados: lo único que falló es la consulta.",
      });
    }
  }, [token]);

  useEffect(() => {
    void cargar();
  }, [cargar]);

  useEffect(() => {
    consultarTitular(usuarioId)
      .then(setTitular)
      .catch(() => setTitular(null));
  }, [usuarioId]);

  const nombre = titular?.nombreDePila;

  return (
    <div className="flex flex-col gap-6">
      <AsistenteDeBienvenida />
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-h1 font-bold text-azul-800">{nombre ? `Hola, ${nombre}` : "Hola"}</h1>
          <p className="text-body text-gris-700">Este es el resumen de tus cuentas.</p>
        </div>
        {actualizado && (
          <button
            type="button"
            onClick={() => void cargar()}
            className="flex items-center gap-2 rounded-full border border-azul-200 bg-blanco px-4 py-2 text-small text-gris-700 hover:border-azul-400"
          >
            <RefreshCw aria-hidden="true" className="h-4 w-4" />
            Actualizado{" "}
            {actualizado.toLocaleString("es-PE", {
              day: "numeric",
              month: "short",
              hour: "2-digit",
              minute: "2-digit",
            })}
          </button>
        )}
      </header>

      {estado.tipo === "cargando" && !actualizado ? (
        <div
          role="status"
          aria-live="polite"
          className="h-40 animate-pulse rounded-[20px] border border-azul-200 bg-blanco"
        >
          <span className="sr-only">Cargando tu resumen…</span>
        </div>
      ) : estado.tipo === "sin-cuenta" ? (
        <CuentaPendiente titular={titular} />
      ) : (
        <>
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_380px]">
            <SaldoTotal
              estado={estado}
              oculto={oculto}
              alternar={() => setOculto((o) => !o)}
              reintentar={() => void cargar()}
            />
            <TarjetaDeDebito
              tarjeta={estado.tipo === "lista" ? estado.tarjeta : null}
              cargando={estado.tipo !== "lista"}
            />
          </div>

          <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_380px]">
            <PanelDeRendimiento
              cuenta={estado.tipo === "lista" ? estado.cuenta : null}
              rendimiento={estado.tipo === "lista" ? estado.rendimiento : null}
              oculto={oculto}
            />
            <DatosDeLaCuenta cuenta={estado.tipo === "lista" ? estado.cuenta : null} />
          </div>

          <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
            <AccesoRapido
              href="/banca/transferir"
              titulo="Transferir"
              pista={PISTAS_DEL_PANEL.transferir}
              detalle="A otra cuenta Ayni, al instante"
              Icono={ArrowLeftRight}
            />
            <AccesoRapido
              href="/banca/depositar"
              titulo="Depositar"
              pista={PISTAS_DEL_PANEL.depositar}
              detalle="Depósito simulado (entorno académico)"
              Icono={PiggyBank}
            />
            <AccesoRapido
              href="/banca/movimientos"
              titulo="Movimientos"
              pista={PISTAS_DEL_PANEL.movimientos}
              detalle="Historial, detalle y gráficas del mes"
              Icono={ReceiptText}
            />
          </div>

          <section
            id="movimientos"
            aria-labelledby="titulo-movimientos"
            className="overflow-hidden rounded-[20px] border border-azul-200 bg-blanco"
          >
            <div className="flex items-center justify-between px-6 py-5">
              <h2 id="titulo-movimientos" className="text-h3 font-bold text-azul-900">
                Últimos movimientos
              </h2>
              <Link
                href="/banca/movimientos"
                className="flex min-h-[44px] items-center gap-1 text-small font-semibold text-azul-600 hover:underline"
              >
                Ver todos <ChevronRight aria-hidden="true" className="h-4 w-4" />
              </Link>
            </div>
            {estado.tipo === "lista" ? (
              <ListaDeMovimientos movimientos={estado.movimientos} oculto={oculto} />
            ) : (
              <p role="status" aria-live="polite" className="px-6 pb-6 text-body text-gris-700">
                {estado.tipo === "error"
                  ? "No pudimos cargar tus movimientos. Inténtalo de nuevo en unos minutos."
                  : "Cargando…"}
              </p>
            )}
          </section>
        </>
      )}
    </div>
  );
}

function SaldoTotal({
  estado,
  oculto,
  alternar,
  reintentar,
}: {
  estado: Estado;
  oculto: boolean;
  alternar: () => void;
  reintentar: () => void;
}) {
  const cuenta = estado.tipo === "lista" ? estado.cuenta : null;
  const importe = (valor: string, moneda: string) =>
    oculto ? "S/ ••••" : formatearImporte(valor, moneda);
  return (
    <section
      aria-labelledby="titulo-saldo"
      className="rounded-[20px] border border-azul-200 bg-blanco p-7"
    >
      <div className="flex items-center justify-between">
        <h2 id="titulo-saldo" className="text-h3 font-bold text-azul-900">
          Saldo total
        </h2>
        {estado.tipo === "error" ? (
          <span className="flex items-center gap-2 rounded-full border border-dorado-500 bg-dorado-100 px-3 py-1 text-caption font-bold text-dorado-800">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-dorado-700" /> Sin conexión
            con el servicio
          </span>
        ) : (
          <Pista texto={PISTAS_DEL_PANEL.saldos}>
            <button
              type="button"
              onClick={alternar}
              className="flex items-center gap-2 rounded-full border border-gris-300 px-3 py-1 text-small text-gris-700 hover:border-azul-400"
            >
              {oculto ? (
                <Eye aria-hidden="true" className="h-4 w-4" />
              ) : (
                <EyeOff aria-hidden="true" className="h-4 w-4" />
              )}
              {oculto ? "Mostrar saldos" : "Ocultar saldos"}
            </button>
          </Pista>
        )}
      </div>

      {estado.tipo === "error" ? (
        <div
          role="alert"
          aria-live="assertive"
          className="mt-5 flex gap-4 rounded-xl border border-dorado-500 bg-dorado-100/60 p-5"
        >
          <span
            aria-hidden="true"
            className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full border border-dorado-500 text-dorado-700"
          >
            <WifiOff className="h-5 w-5" />
          </span>
          <div>
            <p className="text-body font-bold text-dorado-800">No pudimos cargar tus saldos</p>
            <p className="mt-1 text-small text-gris-800">{estado.mensaje}</p>
            <button
              type="button"
              onClick={reintentar}
              className="mt-4 flex items-center gap-2 rounded-full bg-azul-800 px-5 py-2 text-small font-semibold text-blanco hover:bg-azul-900"
            >
              <RefreshCw aria-hidden="true" className="h-4 w-4" /> Reintentar
            </button>
          </div>
        </div>
      ) : (
        <>
          <div className="mt-4 border-b border-azul-100 pb-5">
            <p className="text-small text-gris-700">Total en soles</p>
            <p className="cifra text-display text-azul-900" data-testid="saldo-total">
              {cuenta ? importe(cuenta.saldo, cuenta.moneda) : "—"}
            </p>
          </div>
          <div className="mt-5 flex flex-wrap items-center justify-between gap-4 rounded-xl border border-azul-200 bg-azul-050 p-4">
            <div className="flex items-center gap-3">
              <span
                aria-hidden="true"
                className="flex h-11 w-11 items-center justify-center rounded-lg bg-azul-800 text-blanco"
              >
                <Wallet className="h-5 w-5" />
              </span>
              <div>
                <p className="text-body font-semibold text-gris-900">Cuenta Ayni Soles</p>
                <p className="text-small text-gris-700" data-testid="numero-de-cuenta">
                  {cuenta?.numeroFormateado ?? "Cargando…"}
                </p>
              </div>
            </div>
            <div className="flex items-center gap-3">
              {cuenta?.trea && (
                <span className="rounded-full bg-dorado-100 px-3 py-1 text-caption font-bold text-dorado-800">
                  TREA {cuenta.trea} %
                </span>
              )}
              <span className="cifra text-h3 text-azul-900">
                {cuenta ? importe(cuenta.saldo, cuenta.moneda) : ""}
              </span>
            </div>
          </div>
        </>
      )}
    </section>
  );
}

/** La tarjeta de débito virtual (HU-09): solo sus cuatro últimos dígitos, o cómo emitirla. */
function TarjetaDeDebito({
  tarjeta,
  cargando,
}: {
  tarjeta: TarjetaVirtual | null;
  cargando: boolean;
}) {
  return (
    <section aria-label="Tarjeta de débito" className="flex flex-col gap-3">
      {tarjeta ? (
        <TarjetaAyni
          titular="DÉBITO VIRTUAL"
          ultimos4={tarjeta.ultimos4}
          inicio="4999"
          vencimiento={tarjeta.vencimiento}
        />
      ) : (
        <div
          aria-hidden="true"
          className="aspect-[1.586] w-full max-w-[300px] rounded-[18px] border border-dashed border-azul-300 bg-blanco"
        />
      )}
      <p className="text-small text-gris-700">
        {cargando
          ? "Cargando tu tarjeta…"
          : tarjeta
            ? `Débito virtual · vence ${tarjeta.vencimiento} · ${tarjeta.estado === "ACTIVA" ? "Activa" : "Congelada"}`
            : "Tu tarjeta de débito virtual se emite al abrirse tu cuenta; en unos segundos estará aquí."}
      </p>
      <Link
        href="/banca/tarjeta"
        className="inline-flex min-h-[44px] w-fit items-center gap-1 text-small font-semibold text-azul-600 hover:underline"
      >
        {tarjeta ? "Gestionar tu tarjeta" : "Ver tu tarjeta"}
        <ChevronRight aria-hidden="true" className="h-4 w-4" />
      </Link>
    </section>
  );
}

function DatosDeLaCuenta({ cuenta }: { cuenta: CuentaAbierta | null }) {
  const [copiado, setCopiado] = useState(false);
  return (
    <section
      aria-labelledby="titulo-datos"
      className="rounded-[20px] border border-azul-200 bg-blanco p-7"
    >
      <div className="flex items-center justify-between">
        <h2 id="titulo-datos" className="text-h3 font-bold text-azul-900">
          Recibir dinero
        </h2>
        <span className="flex items-center gap-1 rounded-full bg-dorado-100 px-3 py-1 text-caption font-bold text-dorado-800">
          <ShieldCheck aria-hidden="true" className="h-3.5 w-3.5" /> Sin comisiones
        </span>
      </div>
      <dl className="mt-5 grid grid-cols-1 gap-3">
        <div className="rounded-xl border border-azul-200 p-4">
          <dt className="text-small text-gris-700">Número de cuenta</dt>
          <dd className="cifra mt-1 text-h3 text-azul-900">{cuenta?.numeroFormateado ?? "—"}</dd>
        </div>
        <div className="rounded-xl border border-azul-200 p-4">
          <dt className="text-small text-gris-700">Código interbancario (CCI)</dt>
          <dd className="cifra mt-1 text-body font-semibold text-azul-900">
            {cuenta?.cciFormateado ?? "—"}
          </dd>
        </div>
      </dl>
      <Pista texto={PISTAS_DEL_PANEL.copiar} className="relative mt-5 flex">
        <button
          type="button"
          disabled={!cuenta}
          onClick={() => {
            if (!cuenta) return;
            void navigator.clipboard?.writeText(cuenta.numero).then(() => setCopiado(true));
          }}
          className="flex w-full items-center justify-center gap-2 rounded-full bg-azul-800 py-3 text-body font-semibold text-blanco hover:bg-azul-900 disabled:opacity-50"
        >
          <Copy aria-hidden="true" className="h-4 w-4" />
          {copiado ? "Número copiado" : "Copiar número de cuenta"}
        </button>
      </Pista>
    </section>
  );
}

/** Quien entra sin cuenta abierta: se le dice por qué y se le lleva a terminar la apertura. */
function CuentaPendiente({ titular }: { titular: Titular | null }) {
  const solicitud = titular?.solicitudPendiente;
  // HU-05 escenario 3 (AYNI-327): una solicitud rechazada no venció, y volver a registrarse
  // con el mismo correo no es posible, así que no se le ofrece.
  const rechazada = titular?.estadoSolicitud === "RECHAZADA";
  return (
    <section
      aria-labelledby="titulo-pendiente"
      className="rounded-[20px] border border-azul-200 bg-blanco p-8"
    >
      <h2 id="titulo-pendiente" className="text-h2 font-bold text-azul-900">
        Tu cuenta aún no está abierta
      </h2>
      <p className="mt-2 max-w-2xl text-body text-gris-800">
        {solicitud
          ? "Falta verificar tu identidad: una foto de cada lado de tu DNI y una selfie. Toma unos tres minutos y tu cuenta se abre al terminar."
          : rechazada
            ? "No pudimos aprobar tu solicitud de apertura. Te escribimos por correo con el motivo."
            : titular?.estado === "EN_REVISION" || titular?.estadoSolicitud === "EN_REVISION_MANUAL"
              ? "Estamos revisando tu identidad. Un analista compara tu documento con tus datos y te escribirá por correo con el resultado. No necesitas volver a registrarte."
              : titular?.estado === "ACTIVO"
                ? "Tu identidad ya está verificada y estamos terminando de abrir tu cuenta. Tarda unos segundos: vuelve a cargar esta página."
                : "Tu solicitud de apertura venció. Vuelve a registrarte para empezar de nuevo."}
      </p>
      {solicitud && (
        <Link
          href={`/registro/dni-anverso?solicitudId=${solicitud}`}
          className="mt-6 inline-flex items-center gap-2 rounded-full bg-azul-800 px-6 py-3 text-body font-semibold text-blanco hover:bg-azul-900"
        >
          Continuar la verificación <ChevronRight aria-hidden="true" className="h-5 w-5" />
        </Link>
      )}
    </section>
  );
}

function AccesoRapido({
  href,
  titulo,
  detalle,
  Icono,
  pista,
}: {
  href: string;
  titulo: string;
  detalle: string;
  Icono: typeof Wallet;
  pista: string;
}) {
  return (
    <Pista texto={pista}>
      <Link
        href={href}
        className="flex w-full items-center gap-4 rounded-[16px] border border-azul-200 bg-blanco p-5 hover:border-azul-400"
      >
        <span
          aria-hidden="true"
          className="flex h-11 w-11 items-center justify-center rounded-xl bg-azul-100 text-azul-700"
        >
          <Icono className="h-5 w-5" />
        </span>
        <span className="flex-1">
          <span className="block text-body font-semibold text-gris-900">{titulo}</span>
          <span className="block text-small text-gris-700">{detalle}</span>
        </span>
        <ChevronRight aria-hidden="true" className="h-5 w-5 text-gris-500" />
      </Link>
    </Pista>
  );
}
