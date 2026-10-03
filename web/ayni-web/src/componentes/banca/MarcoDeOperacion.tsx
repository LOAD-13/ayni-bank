import { Clock, ShieldCheck, Wallet } from "lucide-react";
import type { ReactNode } from "react";

/**
 * Disposición común de las operaciones (transferir, depositar): el formulario a la izquierda,
 * en una tarjeta, y a la derecha lo que el cliente necesita saber antes de confirmar. Sigue
 * la retícula del panel del diseño: contenido flexible y columna lateral de 380 px.
 */
export function MarcoDeOperacion({
  titulo,
  subtitulo,
  limite,
  children,
}: {
  titulo: string;
  subtitulo: string;
  limite: string;
  children: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-6">
      <header>
        <h1 className="text-h1 font-bold text-azul-800">{titulo}</h1>
        <p className="text-body text-gris-700">{subtitulo}</p>
      </header>
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_380px]">
        <section className="rounded-[20px] border border-azul-200 bg-blanco p-7">
          {children}
        </section>
        <aside className="flex flex-col gap-4">
          <Dato Icono={Wallet} titulo="Límite por operación" texto={limite} />
          <Dato
            Icono={Clock}
            titulo="Al instante, 24/7"
            texto="La operación se registra en el momento, por partida doble, y recibes tu comprobante."
          />
          <Dato
            Icono={ShieldCheck}
            titulo="Sin cobros dobles"
            texto="Si la conexión falla y vuelves a confirmar, la operación no se repite: cada intento lleva su clave única."
          />
        </aside>
      </div>
    </div>
  );
}

function Dato({ Icono, titulo, texto }: { Icono: typeof Wallet; titulo: string; texto: string }) {
  return (
    <div className="flex gap-4 rounded-[16px] border border-azul-200 bg-blanco p-5">
      <span
        aria-hidden="true"
        className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-azul-100 text-azul-700"
      >
        <Icono className="h-5 w-5" />
      </span>
      <span>
        <span className="block text-body font-semibold text-gris-900">{titulo}</span>
        <span className="block text-small text-gris-700">{texto}</span>
      </span>
    </div>
  );
}
