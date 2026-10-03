"use client";

import { Bell, KeyRound, Mail, ShieldCheck, Smartphone, UserRound } from "lucide-react";
import { useEffect, useState, type ReactNode } from "react";

import { useSesion } from "@/componentes/banca/SesionDeBanca";
import { consultarTitular, type Titular } from "@/lib/api";

/**
 * Configuración de la banca, versión preliminar. Muestra lo que el sistema ya sabe del
 * titular (nombre, correo enmascarado, segundo factor exigido en cada ingreso) y deja
 * visibles, rotulados como «Próximamente», los ajustes que llegan en sprints siguientes:
 * cambiar la contraseña (HU-21), el método de verificación (HU-22) y los avisos por
 * correo de cada movimiento (HU-13). Ningún control finge hacer algo que no hace.
 */
export default function PaginaDeConfiguracion() {
  const { usuarioId } = useSesion();
  const [titular, setTitular] = useState<Titular | null>(null);

  useEffect(() => {
    consultarTitular(usuarioId)
      .then(setTitular)
      .catch(() => setTitular(null));
  }, [usuarioId]);

  return (
    <div className="flex flex-col gap-6">
      <header>
        <h1 className="text-h1 font-bold text-azul-800">Configuración</h1>
        <p className="text-body text-gris-700">
          Tus datos, la seguridad de tu acceso y tus avisos.
        </p>
      </header>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <Seccion Icono={UserRound} titulo="Perfil">
          <Dato etiqueta="Nombre" valor={titular?.nombreDePila ?? "—"} />
          <Dato etiqueta="Correo" valor={titular?.correo ?? "—"} />
          <Dato etiqueta="Tipo de cuenta" valor="Cuenta personal" />
        </Seccion>

        <Seccion Icono={ShieldCheck} titulo="Seguridad">
          <Ajuste
            Icono={Smartphone}
            titulo="Segundo factor"
            texto="Activo: te pedimos un código en cada ingreso."
            estado="Activo"
          />
          <Ajuste
            Icono={KeyRound}
            titulo="Cambiar contraseña"
            texto="Podrás cambiarla desde aquí sin cerrar tu sesión."
          />
          <Ajuste
            Icono={ShieldCheck}
            titulo="Método de verificación"
            texto="Elige entre la app de autenticación o un código por correo."
          />
        </Seccion>

        <Seccion Icono={Bell} titulo="Avisos">
          <Ajuste
            Icono={Mail}
            titulo="Correo por cada movimiento"
            texto="Te escribiremos cada vez que entre o salga dinero de tu cuenta."
          />
        </Seccion>
      </div>
    </div>
  );
}

function Seccion({
  Icono,
  titulo,
  children,
}: {
  Icono: typeof Bell;
  titulo: string;
  children: ReactNode;
}) {
  return (
    <section className="rounded-[20px] border border-azul-200 bg-blanco p-6">
      <h2 className="mb-4 flex items-center gap-2.5 text-h3 font-bold text-azul-900">
        <Icono aria-hidden="true" className="h-5 w-5 text-azul-600" />
        {titulo}
      </h2>
      <div className="flex flex-col divide-y divide-azul-100">{children}</div>
    </section>
  );
}

function Dato({ etiqueta, valor }: { etiqueta: string; valor: string }) {
  return (
    <div className="flex items-center justify-between gap-4 py-3">
      <span className="text-small text-gris-700">{etiqueta}</span>
      <span className="text-body font-semibold text-gris-900">{valor}</span>
    </div>
  );
}

function Ajuste({
  Icono,
  titulo,
  texto,
  estado = "Próximamente",
}: {
  Icono: typeof Bell;
  titulo: string;
  texto: string;
  estado?: string;
}) {
  const activo = estado !== "Próximamente";
  return (
    <div className="flex items-start gap-4 py-3">
      <span
        aria-hidden="true"
        className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-azul-100 text-azul-700"
      >
        <Icono className="h-5 w-5" />
      </span>
      <span className="flex-1">
        <span className="block text-body font-semibold text-gris-900">{titulo}</span>
        <span className="block text-small text-gris-700">{texto}</span>
      </span>
      <span
        className={`shrink-0 rounded-full px-3 py-1 text-caption font-semibold ${
          activo ? "bg-exito/10 text-exito" : "bg-gris-100 text-gris-700"
        }`}
      >
        {estado}
      </span>
    </div>
  );
}
