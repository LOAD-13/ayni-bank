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
  TrendingUp,
  Wallet,
  WifiOff,
} from "lucide-react";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import { useSesion } from "@/componentes/banca/SesionDeBanca";
import { TablaDeMovimientos } from "@/componentes/banca/TablaDeMovimientos";
import {
  consultarMiCuenta,
  consultarMovimientos,
  consultarTitular,
  ErrorDeApi,
  type CuentaAbierta,
  type Movimiento,
  type Titular,
} from "@/lib/api";
import { formatearImporte, rendimientoMensualEstimado } from "@/lib/formato";

type Estado =
  | { tipo: "cargando" }
  | { tipo: "lista"; cuenta: CuentaAbierta; movimientos: Movimiento[] }
  | { tipo: "sin-cuenta" }
  | { tipo: "error"; mensaje: string };

/**
 * Resumen de la banca · HU-08. Sigue el lienzo «7 · Banca por Internet · panel principal»
 * del diseño: saludo, saldo total con la cuenta y su TREA, tarjeta de débito, rendimiento,
 * accesos rápidos y últimos movimientos.
 *
 * Solo se muestra lo que existe de verdad. La tarjeta de débito (HU-09) y el tipo de cambio
 * (HU-12) aún no se emiten: la tarjeta aparece como «próximamente» y el rendimiento es una
 * estimación con la TREA vigente, rotulada como tal, no un abono.
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
      const [cuenta, movimientos] = await Promise.all([
        consultarMiCuenta(vigente),
        consultarMovimientos(vigente, 10),
      ]);
      setEstado({ tipo: "lista", cuenta, movimientos });
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

      {estado.tipo === "sin-cuenta" ? (
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
            <TarjetaDeDebito titular={nombre ?? null} />
          </div>

          <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_380px]">
            <Rendimiento cuenta={estado.tipo === "lista" ? estado.cuenta : null} oculto={oculto} />
            <DatosDeLaCuenta cuenta={estado.tipo === "lista" ? estado.cuenta : null} />
          </div>

          <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
            <AccesoRapido
              href="/banca/transferir"
              titulo="Transferir"
              detalle="A otra cuenta Ayni, al instante"
              Icono={ArrowLeftRight}
            />
            <AccesoRapido
              href="/banca/depositar"
              titulo="Depositar"
              detalle="Depósito simulado (entorno académico)"
              Icono={PiggyBank}
            />
            <AccesoRapido
              href="#movimientos"
              titulo="Movimientos"
              detalle="Cargos y abonos de tu cuenta"
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
            </div>
            {estado.tipo === "lista" ? (
              <TablaDeMovimientos movimientos={estado.movimientos} oculto={oculto} />
            ) : (
              <p role="status" className="px-6 pb-6 text-body text-gris-700">
                {estado.tipo === "error" ? "No se pudieron cargar los movimientos." : "Cargando…"}
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
        )}
      </div>

      {estado.tipo === "error" ? (
        <div
          role="alert"
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

/** La tarjeta del lienzo. Aún no se emite (HU-09): sin número ni vigencia inventados. */
function TarjetaDeDebito({ titular }: { titular: string | null }) {
  return (
    <section aria-label="Tarjeta de débito" className="flex flex-col gap-3">
      <div className="relative overflow-hidden rounded-[20px] bg-gradient-to-br from-azul-900 via-azul-800 to-azul-600 p-6 text-blanco shadow-lg">
        <div className="flex items-center justify-between">
          <span className="text-body font-bold tracking-wide">AYNI Bank</span>
          <span className="rounded-full border border-blanco/40 px-3 py-0.5 text-caption font-semibold">
            Próximamente
          </span>
        </div>
        <span
          aria-hidden="true"
          className="mt-6 block h-9 w-12 rounded-md bg-gradient-to-br from-dorado-400 to-dorado-700"
        />
        <p className="mt-5 font-mono text-h3 tracking-[0.25em] text-blanco/90">
          •••• •••• •••• ••••
        </p>
        <div className="mt-4 flex justify-between text-caption uppercase text-blanco/70">
          <span>
            Titular
            <span className="block text-small font-semibold normal-case text-blanco">
              {titular ?? "—"}
            </span>
          </span>
          <span className="text-right">
            Débito
            <span className="block text-small font-semibold normal-case text-blanco">Virtual</span>
          </span>
        </div>
      </div>
      <p className="text-small text-gris-700">
        Tu tarjeta de débito virtual llega en la siguiente entrega. Por seguridad solo mostraremos
        sus últimos 4 dígitos.
      </p>
    </section>
  );
}

function Rendimiento({ cuenta, oculto }: { cuenta: CuentaAbierta | null; oculto: boolean }) {
  const mensual = cuenta?.trea ? rendimientoMensualEstimado(cuenta.saldo, cuenta.trea) : null;
  return (
    <section
      aria-labelledby="titulo-rendimiento"
      className="rounded-[20px] border border-exito/30 bg-[#EAF4EF] p-7"
    >
      <p id="titulo-rendimiento" className="flex items-center gap-3 text-body font-bold text-exito">
        <span
          aria-hidden="true"
          className="flex h-9 w-9 items-center justify-center rounded-lg bg-exito text-blanco"
        >
          <TrendingUp className="h-5 w-5" />
        </span>
        Rendimiento estimado del mes
      </p>
      <p className="mt-4 text-h2 font-bold text-azul-900">
        {mensual === null
          ? "Tu dinero empieza a rendir con tu primer depósito"
          : oculto
            ? "Tu dinero gana S/ •••• al mes"
            : `Tu dinero gana unos ${formatearImporte(mensual, "PEN")} al mes`}
      </p>
      <p className="mt-2 text-small text-gris-800">
        {cuenta?.trea
          ? `Calculado con tu saldo actual y la TREA vigente de ${cuenta.trea} %. El interés se devenga cada día y se abona a fin de mes.`
          : "El interés se devenga cada día sobre tu saldo y se abona a fin de mes."}
      </p>
      <div aria-hidden="true" className="mt-5 flex h-16 items-end gap-1">
        {Array.from({ length: 24 }, (_, i) => (
          <span
            key={i}
            className={`flex-1 rounded-sm ${i >= 20 ? "bg-exito" : "bg-exito/35"}`}
            style={{ height: `${30 + (i * 70) / 23}%` }}
          />
        ))}
      </div>
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
      <button
        type="button"
        disabled={!cuenta}
        onClick={() => {
          if (!cuenta) return;
          void navigator.clipboard?.writeText(cuenta.numero).then(() => setCopiado(true));
        }}
        className="mt-5 flex w-full items-center justify-center gap-2 rounded-full bg-azul-800 py-3 text-body font-semibold text-blanco hover:bg-azul-900 disabled:opacity-50"
      >
        <Copy aria-hidden="true" className="h-4 w-4" />
        {copiado ? "Número copiado" : "Copiar número de cuenta"}
      </button>
    </section>
  );
}

/** Quien entra sin cuenta abierta: se le dice por qué y se le lleva a terminar la apertura. */
function CuentaPendiente({ titular }: { titular: Titular | null }) {
  const solicitud = titular?.solicitudPendiente;
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
          : titular?.estado === "EN_REVISION"
            ? "Estamos revisando tu identidad. Te avisaremos por correo en cuanto termine."
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
}: {
  href: string;
  titulo: string;
  detalle: string;
  Icono: typeof Wallet;
}) {
  return (
    <Link
      href={href}
      className="flex items-center gap-4 rounded-[16px] border border-azul-200 bg-blanco p-5 hover:border-azul-400"
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
  );
}
