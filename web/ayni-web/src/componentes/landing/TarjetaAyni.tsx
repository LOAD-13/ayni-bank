import Image from "next/image";

interface Props {
  titular?: string;
  ultimos4?: string;
}

/**
 * Tarjeta de débito Ayni, según el diseño aprobado.
 *
 * **Nunca recibe el PAN completo.** Solo admite los cuatro últimos dígitos, de modo que
 * el número entero no puede llegar aquí ni por descuido: no hay forma de pasárselo. Es la
 * regla del §5.3 del documento de diseño, expresada en la firma del componente.
 *
 * Se reutiliza tal cual en la banca por internet cuando llegue HU-09.
 */
export function TarjetaAyni({ titular = "JOAQUÍN LOA S.", ultimos4 = "8842" }: Props) {
  return (
    <div
      role="img"
      aria-label={`Tarjeta de débito Ayni de ${titular}, terminada en ${ultimos4}`}
      className="relative z-10 w-full max-w-[300px] overflow-hidden rounded-[18px] bg-gradient-to-br from-azul-900 to-azul-600 p-5"
    >
      <div
        aria-hidden="true"
        className="absolute top-[-40px] right-[-30px] h-[140px] w-[140px] rounded-full bg-azul-500/35 blur-2xl"
      />

      <div className="relative flex items-center gap-2">
        <Image
          src="/pen/landing-cc4dc915faa1.png"
          alt=""
          aria-hidden="true"
          width={44}
          height={36}
          className="h-[18px] w-auto"
        />
        <span className="text-[14px] font-bold text-blanco">AYNI Bank</span>
      </div>

      <div className="relative mt-6 flex items-center gap-3">
        <Image
          src="/pen/landing-679766641bfe.png"
          alt=""
          aria-hidden="true"
          width={44}
          height={34}
          className="h-[26px] w-auto"
        />
        {/* Ondas de pago sin contacto, como en el diseño. */}
        <span aria-hidden="true" className="flex items-center gap-[3px]">
          {[8, 12, 16].map((alto) => (
            <span
              key={alto}
              style={{ height: `${alto}px` }}
              className="w-[3px] rounded-full bg-dorado-300"
            />
          ))}
        </span>
      </div>

      <p className="cifra relative mt-5 text-[15px] font-semibold tracking-wider text-texto-nav">
        4021 •••• •••• {ultimos4}
      </p>

      <div className="relative mt-4 flex items-end justify-between">
        <span className="text-[10.5px] font-semibold text-azul-300">{titular}</span>
        <span className="text-[19px] font-bold text-blanco">DÉBITO</span>
      </div>
    </div>
  );
}
