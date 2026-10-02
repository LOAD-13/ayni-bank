import {
  ArrowDownLeft,
  Bell,
  CreditCard,
  Headphones,
  ScanFace,
  Send,
  Snowflake,
  Store,
  TrendingUp,
  Zap,
} from "lucide-react";
import Image from "next/image";

const BENEFICIOS = [
  { Icono: ScanFace, titulo: "Ingreso biométrico", sub: "Rostro o huella, sin claves" },
  { Icono: Bell, titulo: "Alertas al instante", sub: "Cada movimiento, en tiempo real" },
  { Icono: Snowflake, titulo: "Congela tu tarjeta", sub: "Un toque y queda bloqueada" },
  { Icono: Headphones, titulo: "Ayuda humana", sub: "Chat con personas, no bots" },
];

/**
 * Movimientos de la maqueta.
 *
 * Los comercios son ficticios a propósito: usar nombres reales en material de un banco
 * sugiere acuerdos comerciales que no existen. El importe lleva siempre signo y etiqueta,
 * porque distinguir un abono de un cargo solo por el color no es accesible.
 */
const MOVIMIENTOS = [
  {
    Icono: TrendingUp,
    titulo: "Interés del día",
    sub: "Abono automático",
    importe: "+ S/ 1.58",
    abono: true,
  },
  {
    Icono: Store,
    titulo: "Bodega Doña Rosa",
    sub: "Compra con tarjeta",
    importe: "− S/ 84.20",
    abono: false,
  },
  {
    Icono: ArrowDownLeft,
    titulo: "Transferencia a Ayni · María Q.",
    sub: "Transferencia recibida",
    importe: "+ S/ 250.00",
    abono: true,
  },
  {
    Icono: Zap,
    titulo: "Servicio Eléctrico Regional",
    sub: "Pago de servicio",
    importe: "− S/ 112.40",
    abono: false,
  },
];

const ACCESOS = [
  { Icono: Send, texto: "Transferir" },
  { Icono: CreditCard, texto: "Pagar" },
  { Icono: Zap, texto: "Recargar" },
  { Icono: TrendingUp, texto: "Metas" },
];

/** Sección «La app Ayni», con la maqueta del teléfono del diseño aprobado. */
export function SeccionApp() {
  return (
    <section id="app" className="relative overflow-hidden bg-blanco">
      <div aria-hidden="true" className="pointer-events-none absolute inset-0">
        <div className="absolute top-[8%] left-[4%] h-[380px] w-[380px] rounded-full bg-azul-200/60 blur-[90px]" />
        <div className="absolute right-[8%] bottom-[10%] h-[340px] w-[340px] rounded-full bg-dorado-200/80 blur-[90px]" />
      </div>

      <div className="relative mx-auto grid max-w-[1440px] items-center gap-14 px-6 py-20 lg:grid-cols-[380px_1fr] lg:px-16 lg:py-28">
        <MaquetaDelTelefono />

        <div>
          <p className="flex items-center gap-2.5">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-dorado-500" />
            <span className="text-[12.5px] font-bold tracking-[0.14em] text-azul-600">
              LA APP AYNI
            </span>
          </p>

          <h2 className="mt-4 max-w-[520px] text-[clamp(1.9rem,4vw,50px)] leading-[1.1] font-bold text-azul-900">
            Todo tu banco cabe en tu bolsillo.
          </h2>

          <p className="mt-5 max-w-[560px] text-[16.5px] leading-[1.65] text-gris-700">
            Abre tu cuenta, activa tu tarjeta, transfiere, paga servicios y pon metas de ahorro.
            Todo desde un solo lugar, sin filas y sin horarios.
          </p>

          <ul className="mt-10 grid gap-4 sm:grid-cols-2">
            {BENEFICIOS.map(({ Icono, titulo, sub }) => (
              <li key={titulo} className="flex items-start gap-3.5 rounded-[14px] bg-azul-050 p-4">
                <span
                  aria-hidden="true"
                  className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-[10px] bg-azul-900 text-oro"
                >
                  <Icono className="h-[18px] w-[18px]" />
                </span>
                <div>
                  <p className="text-[15.5px] font-bold text-azul-900">{titulo}</p>
                  <p className="mt-0.5 text-[13px] text-gris-500">{sub}</p>
                </div>
              </li>
            ))}
          </ul>

          <div className="mt-8 flex flex-wrap gap-4">
            <Image
              src="/pen/landing-7b56bf45aefb.png"
              alt="Descargar en App Store"
              width={160}
              height={48}
              className="h-[44px] w-auto"
            />
            <Image
              src="/pen/landing-34778c80aad1.png"
              alt="Disponible en Google Play"
              width={160}
              height={48}
              className="h-[44px] w-auto"
            />
          </div>
        </div>
      </div>
    </section>
  );
}

/** Maqueta del teléfono. Es una ilustración del producto, no una interfaz operable. */
function MaquetaDelTelefono() {
  return (
    <div
      role="img"
      aria-label="Vista previa de la aplicación Ayni: saldo de la cuenta en soles, accesos rápidos y últimos movimientos"
      className="mx-auto w-full max-w-[340px] rounded-[38px] bg-noche p-3 shadow-lg"
    >
      <div className="overflow-hidden rounded-[30px] bg-azul-050" aria-hidden="true">
        <div className="flex items-center justify-between bg-blanco px-5 py-2.5">
          <span className="text-[12px] font-bold text-azul-900">9:41</span>
          <span className="text-[11px] text-gris-500">▮▮▮</span>
        </div>

        <div className="bg-blanco px-5 pt-2 pb-5">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-[17px] font-bold text-azul-900">Hola, Joaquín</p>
              <p className="text-[11.5px] text-gris-500">Buenas tardes</p>
            </div>
            <Image
              src="/pen/landing-c545a0328b79.png"
              alt=""
              width={36}
              height={36}
              className="h-8 w-8"
            />
          </div>

          <div className="mt-4 rounded-[18px] bg-gradient-to-br from-azul-900 to-azul-600 p-4">
            <p className="text-[11px] font-semibold text-azul-300">Cuenta Ayni · Soles</p>
            <p className="cifra mt-1 text-[29px] leading-none font-bold text-blanco">
              S/ 12,480.65
            </p>
            <p className="mt-2.5 inline-flex rounded-full bg-exito/20 px-2.5 py-1 text-[11px] font-bold text-verde-rendimiento">
              + S/ 1.56 hoy · 4.50% TREA
            </p>
          </div>

          <div className="mt-4 grid grid-cols-4 gap-2">
            {ACCESOS.map(({ Icono, texto }) => (
              <span
                key={texto}
                className="flex flex-col items-center gap-1 rounded-[12px] bg-blanco py-2.5 text-[10px] font-semibold text-gris-700 shadow-sm"
              >
                <Icono className="h-4 w-4 text-azul-700" />
                {texto}
              </span>
            ))}
          </div>
        </div>

        <div className="px-5 pt-3 pb-5">
          <p className="text-[14px] font-bold text-azul-900">Movimientos</p>
          <ul className="mt-2 flex flex-col gap-1">
            {MOVIMIENTOS.map(({ Icono, titulo, sub, importe, abono }) => (
              <li
                key={titulo}
                className="flex items-center justify-between gap-3 rounded-[12px] bg-blanco px-3 py-2.5"
              >
                <span className="inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-azul-100 text-azul-600">
                  <Icono className="h-3.5 w-3.5" />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-[11.5px] font-bold text-azul-900">
                    {titulo}
                  </span>
                  <span className="block text-[10px] text-gris-500">{sub}</span>
                </span>
                <span
                  className={`cifra shrink-0 text-[12px] font-bold ${
                    abono ? "text-exito" : "text-gris-900"
                  }`}
                >
                  {importe}
                </span>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  );
}
