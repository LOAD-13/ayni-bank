"use client";

import { useRouter } from "next/navigation";
import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";

import { obtenerToken, usuarioDelToken } from "@/lib/sesion";

interface ContextoDeSesion {
  usuarioId: string;
  /** Devuelve un token válido, renovándolo si hace falta; si la sesión cayó, vuelve al ingreso. */
  token: () => Promise<string>;
}

const Contexto = createContext<ContextoDeSesion | null>(null);

/**
 * Guardián de la banca por internet.
 *
 * Al entrar comprueba que haya sesión: la de memoria o, tras una recarga, la que se
 * recupera con la cookie de renovación. Si no hay ninguna, manda al ingreso sin enseñar
 * ni un dato. La protección real está en el servidor —el gateway rechaza con 401 sin
 * token—; esto solo evita pintar una pantalla vacía.
 */
export function SesionDeBanca({ children }: { children: ReactNode }) {
  const router = useRouter();
  const [usuarioId, setUsuarioId] = useState<string | null>(null);

  const token = useCallback(async () => {
    const vigente = await obtenerToken();
    if (!vigente) {
      router.replace("/ingresar");
      throw new Error("Sesión caducada");
    }
    return vigente;
  }, [router]);

  useEffect(() => {
    let vivo = true;
    void obtenerToken().then((vigente) => {
      if (!vivo) return;
      const usuario = vigente ? usuarioDelToken(vigente) : null;
      if (!usuario) {
        router.replace("/ingresar");
        return;
      }
      setUsuarioId(usuario);
    });
    return () => {
      vivo = false;
    };
  }, [router]);

  if (!usuarioId) {
    return (
      <p role="status" className="p-10 text-body text-gris-700">
        Comprobando tu sesión…
      </p>
    );
  }

  return <Contexto.Provider value={{ usuarioId, token }}>{children}</Contexto.Provider>;
}

export function useSesion(): ContextoDeSesion {
  const valor = useContext(Contexto);
  if (!valor) throw new Error("useSesion fuera de <SesionDeBanca>");
  return valor;
}
