import Image from "next/image";
import Link from "next/link";

/**
 * Catálogo de productos del diseño aprobado.
 *
 * Varios de estos productos —crédito, depósito a plazo, seguros, cuenta negocio— quedan
 * **fuera del alcance declarado en el Acta** (§2.2 del documento de diseño). Se conservan
 * porque forman parte del diseño que aprobó el docente, y su enlace lleva a la página de
 * pendientes en lugar de prometer algo que el proyecto no va a construir. El inventario
 * está en `docs/gestion/landing-funcionalidades-pendientes.md`.
 */
const PRODUCTOS = [
  {
    icono: "/pen/landing-3406b3a6ea01.png",
    titulo: "Cuenta de ahorro",
    sub: "4.50% TREA con devengo diario y cero mantenimiento.",
    accion: "Ver detalle",
    destino: "#cuenta",
  },
  {
    icono: "/pen/landing-ba234392b891.png",
    titulo: "Depósito a plazo",
    sub: "Elige el plazo y asegura tu tasa desde el primer día.",
    accion: "Simular",
    destino: "/pendiente",
  },
  {
    icono: "/pen/landing-df600097f04a.png",
    titulo: "Tarjeta de crédito",
    sub: "Sin membresía el primer año y control total desde la app.",
    accion: "Solicitar",
    destino: "/pendiente",
  },
  {
    icono: "/pen/landing-998e88e0688d.png",
    titulo: "Préstamo personal",
    sub: "Respuesta en minutos y desembolso el mismo día.",
    accion: "Cotizar",
    destino: "/pendiente",
  },
  {
    icono: "/pen/landing-6df79482b662.png",
    titulo: "Seguros simples",
    sub: "Protege tu tarjeta, tu celular y a tu familia.",
    accion: "Conocer",
    destino: "/pendiente",
  },
  {
    icono: "/pen/landing-0ca1f60c2231.png",
    titulo: "Cuenta negocio",
    sub: "Cobra con QR, factura y controla tu caja en un solo lugar.",
    accion: "Empezar",
    destino: "/pendiente",
  },
];

export function SeccionProductos() {
  return (
    <section id="productos" className="bg-blanco">
      <div className="mx-auto max-w-[1440px] px-6 py-20 lg:px-16 lg:py-24">
        <header className="flex flex-col gap-6 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p className="flex items-center gap-2.5">
              <span aria-hidden="true" className="h-2 w-2 rounded-full bg-dorado-500" />
              <span className="text-[12.5px] font-bold tracking-[0.14em] text-azul-600">
                PRODUCTOS
              </span>
            </p>
            <h2 className="mt-4 max-w-[560px] text-[clamp(1.9rem,4vw,50px)] leading-[1.1] font-bold text-azul-900">
              Lo que necesitas, cuando lo necesitas.
            </h2>
          </div>

          <Link
            href="/pendiente"
            className="inline-flex w-fit shrink-0 items-center gap-2 rounded-full bg-azul-900 px-6 py-3.5 text-[15px] font-bold text-blanco hover:bg-azul-800"
          >
            Ver todos los productos <span aria-hidden="true">→</span>
          </Link>
        </header>

        <ul className="mt-12 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {PRODUCTOS.map(({ icono, titulo, sub, accion, destino }) => (
            <li key={titulo}>
              <Link
                href={destino}
                className="flex h-full flex-col rounded-[20px] bg-azul-050 p-7 transition-colors hover:bg-azul-100"
              >
                <span className="inline-flex w-fit rounded-[16px] bg-gradient-to-br from-azul-900 to-azul-700 p-3.5">
                  <Image
                    src={icono}
                    alt=""
                    aria-hidden="true"
                    width={64}
                    height={64}
                    className="h-10 w-10"
                  />
                </span>
                <span className="mt-5 block text-[21px] leading-tight font-bold text-azul-900">
                  {titulo}
                </span>
                <span className="mt-2 block flex-1 text-[14.5px] leading-[1.55] text-gris-700">
                  {sub}
                </span>
                <span className="mt-5 inline-flex items-center gap-1.5 text-[14px] font-bold text-azul-600">
                  {accion} <span aria-hidden="true">↗</span>
                </span>
              </Link>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
