import Image from "next/image";

/**
 * Sección «Cómo funciona».
 *
 * En el prototipo el nodo se llama «Testimonios», pero su contenido son **hechos del
 * producto**, no opiniones de personas. Se mantiene así a propósito: inventar testimonios
 * y atribuirlos a clientes que no existen sería fabricar reseñas.
 */
const HECHOS = [
  {
    foto: "/pen/landing-45092e6c8ec7.webp",
    hecho: "Transferencias inmediatas, sin costo y a cualquier hora del día.",
    dato: "Entre cuentas Ayni y a otros bancos",
    detalle: "Disponible los 7 días, las 24 horas",
  },
  {
    foto: "/pen/landing-8e6011545297.png",
    hecho: "El interés se calcula todos los días sobre tu saldo, no a fin de mes.",
    dato: "4.50 % TREA en soles",
    detalle: "1.20 % TREA en dólares",
  },
  {
    foto: "/pen/landing-31b11e761060.jpg",
    hecho: "Abres la cuenta en 5 minutos con tu DNI, sin pisar una agencia.",
    dato: "100 % digital",
    detalle: "Verificación de identidad en línea",
  },
];

export function SeccionComoFunciona() {
  return (
    <section className="bg-azul-050">
      <div className="mx-auto max-w-[1440px] px-6 py-20 lg:px-16 lg:py-24">
        <p className="flex items-center gap-2.5">
          <span aria-hidden="true" className="h-2 w-2 rounded-full bg-dorado-500" />
          <span className="text-[12.5px] font-bold tracking-[0.14em] text-azul-600">
            CÓMO FUNCIONA
          </span>
        </p>
        <h2 className="mt-4 max-w-[560px] text-[clamp(1.9rem,4vw,50px)] leading-[1.1] font-bold text-azul-900">
          Pensada para el día a día en Perú.
        </h2>

        <ul className="mt-12 grid gap-6 lg:grid-cols-3">
          {HECHOS.map(({ foto, hecho, dato, detalle }) => (
            <li key={dato} className="overflow-hidden rounded-[20px] bg-blanco">
              <div className="relative aspect-[16/9]">
                <Image
                  src={foto}
                  alt=""
                  aria-hidden="true"
                  fill
                  sizes="(max-width: 1024px) 90vw, 420px"
                  className="object-cover"
                />
              </div>
              <div className="p-7">
                <p className="text-[17px] leading-[1.45] font-semibold text-azul-900">{hecho}</p>
                <p className="mt-5 text-[14.5px] font-bold text-azul-800">{dato}</p>
                <p className="mt-1 text-[13px] text-gris-500">{detalle}</p>
              </div>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
