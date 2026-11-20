"use client";

import { Bell, KeyRound, Mail, ShieldCheck, Smartphone, UserRound } from "lucide-react";
import Link from "next/link";
import { useEffect, useState, type ReactNode } from "react";

import { useSesion } from "@/componentes/banca/SesionDeBanca";
import { consultarTitular, type Titular } from "@/lib/api";

/**
 * Configuración de la banca: lo que el sistema sabe del titular y cómo está protegido su
 * acceso. Todo lo que muestra existe: el segundo factor se exige en cada ingreso y el método
 * se elige ahí mismo; la contraseña se cambia con el enlace de un solo uso que llega al
 * correo; y notification escribe por cada movimiento. Ningún control finge hacer algo.
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
            texto="Te enviamos a tu correo un enlace de un solo uso para elegir una nueva."
            accion={{ texto: "Cambiar", href: "/recuperar" }}
          />
          <Ajuste
            Icono={ShieldCheck}
            titulo="Método de verificación"
            texto="Lo eliges cada vez que ingresas: app de autenticación o código por correo."
            estado="Activo"
          />
        </Seccion>

        <Seccion Icono={Bell} titulo="Avisos">
          <Ajuste
            Icono={Mail}
            titulo="Correo por cada movimiento"
            texto="Te escribimos cada vez que entra o sale dinero de tu cuenta."
            estado="Activo"
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
  estado,
  accion,
}: {
  Icono: typeof Bell;
  titulo: string;
  texto: string;
  estado?: string;
  accion?: { texto: string; href: string };
}) {
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
      {estado && (
        <span className="shrink-0 rounded-full bg-exito/10 px-3 py-1 text-caption font-semibold text-exito">
          {estado}
        </span>
      )}
      {accion && (
        <Link
          href={accion.href}
          className="inline-flex min-h-[44px] shrink-0 items-center rounded-full border border-azul-700 px-4 text-small font-semibold text-azul-700 hover:bg-azul-050"
        >
          {accion.texto}
        </Link>
      )}
    </div>
  );
}
