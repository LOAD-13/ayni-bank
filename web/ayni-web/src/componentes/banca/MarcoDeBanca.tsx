"use client";

import {
  ArrowLeftRight,
  CreditCard,
  LayoutGrid,
  LogOut,
  PiggyBank,
  ReceiptText,
  Settings,
  Wallet,
} from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";

import { LogotipoAyni } from "@/componentes/LogotipoAyni";
import { cerrarSesion, consultarTitular } from "@/lib/api";
import { olvidarSesion } from "@/lib/sesion";

import { useSesion } from "./SesionDeBanca";

const NAVEGACION = [
  { href: "/banca", texto: "Resumen", Icono: LayoutGrid },
  { href: "/banca/transferir", texto: "Transferir", Icono: ArrowLeftRight },
  { href: "/banca/depositar", texto: "Depositar", Icono: PiggyBank },
  { href: "/pendiente", texto: "Cuentas", Icono: Wallet },
  { href: "/pendiente", texto: "Tarjetas", Icono: CreditCard },
  { href: "/pendiente", texto: "Estados de cuenta", Icono: ReceiptText },
  { href: "/pendiente", texto: "Configuración", Icono: Settings },
];

/**
 * El marco de la banca por internet: barra lateral, cabecera con el titular y el
 * contenido. Sigue el lienzo «7 · Banca por Internet · Panel» del prototipo.
 *
 * Lo que el prototipo dibuja y aún no existe enlaza a `/pendiente`, que lo dice tal cual.
 */
export function MarcoDeBanca({ children }: { children: ReactNode }) {
  const ruta = usePathname();
  const router = useRouter();
  const { usuarioId } = useSesion();
  const [nombre, setNombre] = useState<string | null>(null);
  const [saliendo, setSaliendo] = useState(false);

  useEffect(() => {
    consultarTitular(usuarioId)
      .then((titular) => setNombre(titular.nombreDePila))
      .catch(() => setNombre(null));
  }, [usuarioId]);

  async function salir() {
    setSaliendo(true);
    try {
      await cerrarSesion();
    } finally {
      olvidarSesion();
      router.replace("/ingresar");
    }
  }

  const iniciales = (nombre ?? "A").slice(0, 1).toUpperCase();

  return (
    <div className="min-h-screen bg-azul-050">
      <header className="flex items-center justify-between border-b border-azul-100 bg-blanco px-5 py-3 sm:px-8">
        <Link href="/banca" aria-label="Ayni Bank · inicio de la banca">
          <LogotipoAyni altura={17} tono="oscuro" />
        </Link>
        <div className="flex items-center gap-3 rounded-full border border-azul-100 py-1.5 pr-4 pl-1.5">
          <span
            aria-hidden="true"
            className="flex h-9 w-9 items-center justify-center rounded-full bg-azul-700 text-small font-bold text-blanco"
          >
            {iniciales}
          </span>
          <span className="flex flex-col leading-tight">
            <span className="text-small font-semibold text-gris-900">{nombre ?? "Tu cuenta"}</span>
            <span className="text-caption text-gris-700">Cuenta personal</span>
          </span>
        </div>
      </header>

      <div className="mx-auto grid max-w-[1440px] grid-cols-1 lg:grid-cols-[248px_1fr]">
        <nav
          aria-label="Banca por internet"
          className="flex gap-1 overflow-x-auto border-b border-azul-100 bg-blanco p-3 lg:min-h-[calc(100vh-61px)] lg:flex-col lg:border-r lg:border-b-0 lg:p-4"
        >
          {NAVEGACION.map(({ href, texto, Icono }) => {
            const activa = href === ruta;
            return (
              <Link
                key={texto}
                href={href}
                aria-current={activa ? "page" : undefined}
                className={[
                  "flex min-h-[44px] shrink-0 items-center gap-3 rounded-lg px-4 text-body",
                  activa
                    ? "bg-azul-100 font-semibold text-azul-800"
                    : "text-gris-700 hover:bg-azul-050",
                ].join(" ")}
              >
                <Icono aria-hidden="true" className="h-[18px] w-[18px]" />
                {texto}
              </Link>
            );
          })}
          <button
            type="button"
            onClick={salir}
            disabled={saliendo}
            className="flex min-h-[44px] shrink-0 items-center gap-3 rounded-lg px-4 text-body text-gris-700 hover:bg-azul-050 lg:mt-auto"
          >
            <LogOut aria-hidden="true" className="h-[18px] w-[18px]" />
            {saliendo ? "Cerrando…" : "Cerrar sesión"}
          </button>
        </nav>

        <main className="px-5 py-8 sm:px-8">{children}</main>
      </div>
    </div>
  );
}
