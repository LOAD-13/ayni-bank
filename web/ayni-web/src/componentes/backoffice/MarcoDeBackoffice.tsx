"use client";

import { ScrollText } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";

import { LogotipoAyni } from "@/componentes/LogotipoAyni";
import { useSesion } from "@/componentes/banca/SesionDeBanca";
import { permisosDelToken } from "@/lib/sesion";

/** Cada opción del back-office y el permiso que la hace visible. */
const OPCIONES = [
  {
    href: "/backoffice/auditoria",
    texto: "Pista de auditoría",
    permiso: "auditoria:leer",
    Icono: ScrollText,
  },
];

/**
 * El marco del back-office del banco: solo enseña las opciones cuyo permiso trae el token. Es
 * presentación y nada más: el gateway exige el permiso en cada ruta y el servicio lo vuelve a
 * comprobar, así que quien lo salte aquí solo ve una pantalla que no carga datos.
 */
export function MarcoDeBackoffice({ children }: { children: ReactNode }) {
  const ruta = usePathname();
  const { token } = useSesion();
  const [permisos, setPermisos] = useState<string[] | null>(null);

  useEffect(() => {
    token()
      .then((vigente) => setPermisos(permisosDelToken(vigente)))
      .catch(() => setPermisos([]));
  }, [token]);

  const visibles = OPCIONES.filter((o) => permisos?.includes(o.permiso));

  return (
    <div className="min-h-screen bg-azul-050">
      <header className="flex items-center justify-between border-b border-azul-100 bg-blanco px-5 py-3 sm:px-8">
        <Link href="/backoffice/auditoria" aria-label="Ayni Bank · back-office">
          <LogotipoAyni altura={17} tono="oscuro" />
        </Link>
        <span className="text-small font-semibold text-gris-700">Back-office</span>
      </header>

      <div className="grid grid-cols-1 lg:grid-cols-[248px_1fr]">
        <nav
          aria-label="Back-office"
          className="flex gap-1 overflow-x-auto border-b border-azul-100 bg-blanco p-3 lg:min-h-[calc(100vh-61px)] lg:flex-col lg:border-r lg:border-b-0 lg:p-4"
        >
          {visibles.map(({ href, texto, Icono }) => (
            <Link
              key={href}
              href={href}
              aria-current={ruta === href ? "page" : undefined}
              className={[
                "flex min-h-[44px] shrink-0 items-center gap-3 rounded-lg px-4 text-body",
                ruta === href
                  ? "bg-azul-100 font-semibold text-azul-800"
                  : "text-gris-700 hover:bg-azul-050",
              ].join(" ")}
            >
              <Icono aria-hidden="true" className="h-[18px] w-[18px]" />
              {texto}
            </Link>
          ))}
        </nav>
        <main className="px-5 py-8 sm:px-8">
          {permisos === null ? (
            <p role="status" className="text-body text-gris-700">
              Comprobando tu rol…
            </p>
          ) : (
            children
          )}
        </main>
      </div>
    </div>
  );
}
