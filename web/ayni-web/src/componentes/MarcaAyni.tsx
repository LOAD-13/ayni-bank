import Image from "next/image";
import Link from "next/link";

/**
 * Logotipo con enlace al inicio.
 *
 * El `alt` dice «Ayni Bank» y no «logo de Ayni Bank»: el lector de pantalla ya anuncia
 * que es una imagen, y repetirlo obliga a escuchar la palabra dos veces.
 */
export function MarcaAyni({ className = "" }: { className?: string }) {
  return (
    <Link href="/" className={`inline-flex items-center ${className}`}>
      <Image
        src="/logo.png"
        alt="Ayni Bank"
        width={160}
        height={160}
        priority
        className="h-9 w-auto"
      />
    </Link>
  );
}
