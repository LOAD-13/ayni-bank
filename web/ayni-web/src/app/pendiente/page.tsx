import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "En construcción",
  description: "Esta sección de Ayni Bank todavía no está disponible.",
};

/**
 * Destino de todo lo que el prototipo dibuja pero el producto aún no implementa.
 *
 * Existe porque un enlace que devuelve 404 le enseña al visitante que la web está rota,
 * y uno que no reacciona al clic le hace creer que su navegador falla. Esta página dice
 * la verdad: la pantalla está diseñada y su historia todavía no se ha construido.
 *
 * El inventario de qué falta y con qué historia llega está en
 * `docs/gestion/landing-funcionalidades-pendientes.md`.
 */
export default function Pendiente() {
  return (
    <main className="mx-auto flex min-h-screen max-w-xl flex-col justify-center gap-5 px-6 py-16">
      <p className="text-caption font-bold tracking-widest text-dorado-700 uppercase">Ayni Bank</p>

      <h1 className="text-h1 font-bold text-azul-700">Todavía no está lista</h1>

      <p className="text-body text-gris-700">
        Esta sección está diseñada y aprobada, pero su funcionalidad aún no se ha construido.
        Llegará en una de las próximas entregas del proyecto.
      </p>

      <p className="text-small text-gris-500">
        Mientras tanto, ya puedes abrir tu cuenta y recorrer el proceso de verificación de
        identidad.
      </p>

      <div className="mt-2 flex flex-col gap-3 sm:flex-row">
        <Link
          href="/registro"
          className="inline-flex min-h-[44px] items-center justify-center rounded-full bg-azul-700 px-6 text-body font-semibold text-blanco hover:bg-azul-800"
        >
          Abrir mi cuenta
        </Link>
        <Link
          href="/"
          className="inline-flex min-h-[44px] items-center justify-center rounded-full border border-gris-300 px-6 text-body font-semibold text-gris-700 hover:bg-gris-100"
        >
          Volver al inicio
        </Link>
      </div>
    </main>
  );
}
