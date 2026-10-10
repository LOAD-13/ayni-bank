"use client";

import { CircleCheck, Eye, EyeOff, LinkIcon, TriangleAlert } from "lucide-react";
import Link from "next/link";
import { useEffect, useId, useState, type FormEvent } from "react";

import { RequisitosDeContrasena } from "@/componentes/RequisitosDeContrasena";
import { cumpleLaPolitica } from "@/dominio/politicaDeContrasena";
import { ErrorDeApi, restablecerContrasena, validarEnlaceDeRecuperacion } from "@/lib/api";
import { tokenDelFragmento } from "@/lib/recuperacion";

type Estado = "validando" | "invalido" | "formulario" | "listo";

/**
 * HU-21 · Paso 2: la contraseña nueva.
 *
 * Al cargar, lee el token del fragmento y **lo borra de la barra de direcciones**: si el
 * cliente copia la URL para pedir ayuda, o la pantalla se comparte, el enlace no viaja
 * con ella. Luego pregunta al servidor si el enlace sirve, para no pedir una contraseña
 * que después se rechazaría.
 *
 * Un enlace inventado, usado o caducado muestra el mismo mensaje en los tres casos, sin
 * decir de quién era.
 */
export function FormularioDeContrasenaNueva() {
  const idNueva = useId();
  const idRepetida = useId();

  const [estado, setEstado] = useState<Estado>("validando");
  const [token, setToken] = useState<string | null>(null);
  const [nueva, setNueva] = useState("");
  const [repetida, setRepetida] = useState("");
  const [visible, setVisible] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const leido = tokenDelFragmento(window.location.hash);
    window.history.replaceState(null, "", window.location.pathname);
    if (leido === null) {
      setEstado("invalido");
      return;
    }
    setToken(leido);
    validarEnlaceDeRecuperacion(leido)
      .then(() => setEstado("formulario"))
      .catch((fallo: unknown) => {
        if (fallo instanceof ErrorDeApi && fallo.estado === 0) {
          // Sin conexión no se sabe si el enlace sirve: se deja intentar el cambio.
          setEstado("formulario");
          return;
        }
        setEstado("invalido");
      });
  }, []);

  async function alEnviar(evento: FormEvent) {
    evento.preventDefault();
    if (!cumpleLaPolitica(nueva)) {
      setError("La contraseña nueva aún no cumple todos los requisitos.");
      return;
    }
    if (nueva !== repetida) {
      setError("Las dos contraseñas no coinciden.");
      return;
    }
    setEnviando(true);
    setError(null);
    try {
      await restablecerContrasena(token ?? "", nueva);
      setEstado("listo");
    } catch (fallo) {
      if (fallo instanceof ErrorDeApi && fallo.estado === 410) {
        setEstado("invalido");
      } else if (fallo instanceof ErrorDeApi && fallo.estado === 400) {
        setError("La contraseña nueva no cumple la política de seguridad.");
      } else {
        setError("No pudimos conectar con Ayni. Revisa tu conexión e inténtalo de nuevo.");
      }
    } finally {
      setEnviando(false);
    }
  }

  if (estado === "validando") {
    return (
      <p role="status" className="text-[14.5px] text-gris-700">
        Comprobando el enlace…
      </p>
    );
  }

  if (estado === "invalido") {
    return (
      <>
        <span className="inline-flex h-12 w-12 items-center justify-center rounded-full bg-error/10 text-error">
          <LinkIcon aria-hidden="true" className="h-6 w-6" />
        </span>
        <h2 className="mt-5 text-[28px] leading-tight font-bold text-azul-700">
          El enlace ya no es válido
        </h2>
        <p className="mt-3 text-[14.5px] leading-[1.6] text-gris-700">
          Los enlaces para cambiar la contraseña vencen a los 30 minutos, sirven una sola vez y
          dejan de valer cuando pides otro. Pide uno nuevo y usa el último que te llegue.
        </p>
        <Link
          href="/recuperar"
          className="mt-7 inline-flex min-h-[48px] w-full items-center justify-center rounded-full bg-azul-700 px-7 text-[15px] font-bold text-blanco hover:bg-azul-800"
        >
          Pedir un enlace nuevo
        </Link>
      </>
    );
  }

  if (estado === "listo") {
    return (
      <>
        <span className="inline-flex h-12 w-12 items-center justify-center rounded-full bg-exito/10 text-exito">
          <CircleCheck aria-hidden="true" className="h-6 w-6" />
        </span>
        <h2 className="mt-5 text-[28px] leading-tight font-bold text-azul-700">
          Listo, tu contraseña cambió
        </h2>
        <p role="status" className="mt-3 text-[14.5px] leading-[1.6] text-gris-700">
          Por seguridad cerramos todas tus sesiones abiertas. Para entrar usa tu contraseña nueva y
          tu segundo factor, como siempre.
        </p>
        <Link
          href="/ingresar"
          className="mt-7 inline-flex min-h-[48px] w-full items-center justify-center rounded-full bg-azul-700 px-7 text-[15px] font-bold text-blanco hover:bg-azul-800"
        >
          Ingresar
        </Link>
      </>
    );
  }

  return (
    <>
      <h2 className="text-[28px] leading-tight font-bold text-azul-700">
        Crea tu contraseña nueva
      </h2>
      <p className="mt-2 text-[14.5px] text-gris-700">
        Al guardarla cerraremos todas tus sesiones abiertas.
      </p>

      {error && (
        <p
          role="alert"
          className="mt-6 flex items-start gap-2.5 rounded-[12px] border border-error bg-blanco p-4 text-[13.5px] text-error"
        >
          <TriangleAlert aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0" />
          {error}
        </p>
      )}

      <form onSubmit={alEnviar} noValidate className="mt-7 flex flex-col gap-5">
        <div className="flex flex-col gap-1.5">
          <label htmlFor={idNueva} className="text-[13.5px] font-semibold text-gris-700">
            Contraseña nueva
          </label>
          <div className="campo flex min-h-[48px] items-center rounded-[12px] border border-gris-300 bg-blanco">
            <input
              id={idNueva}
              type={visible ? "text" : "password"}
              autoComplete="new-password"
              value={nueva}
              onChange={(e) => setNueva(e.target.value)}
              className="min-w-0 flex-1 bg-transparent px-4 text-[15px] text-gris-900 outline-none"
            />
            <button
              type="button"
              onClick={() => setVisible((v) => !v)}
              aria-label={visible ? "Ocultar la contraseña" : "Mostrar la contraseña"}
              aria-pressed={visible}
              className="px-4 text-gris-500 hover:text-gris-700"
            >
              {visible ? (
                <EyeOff aria-hidden="true" className="h-4 w-4" />
              ) : (
                <Eye aria-hidden="true" className="h-4 w-4" />
              )}
            </button>
          </div>
        </div>

        <RequisitosDeContrasena contrasena={nueva} />

        <div className="flex flex-col gap-1.5">
          <label htmlFor={idRepetida} className="text-[13.5px] font-semibold text-gris-700">
            Repite la contraseña nueva
          </label>
          <div className="campo flex min-h-[48px] items-center rounded-[12px] border border-gris-300 bg-blanco">
            <input
              id={idRepetida}
              type={visible ? "text" : "password"}
              autoComplete="new-password"
              value={repetida}
              onChange={(e) => setRepetida(e.target.value)}
              className="min-w-0 flex-1 bg-transparent px-4 text-[15px] text-gris-900 outline-none"
            />
          </div>
        </div>

        <button
          type="submit"
          disabled={enviando}
          aria-busy={enviando}
          className="inline-flex min-h-[48px] w-full items-center justify-center rounded-full bg-azul-700 px-7 text-[15px] font-bold text-blanco hover:bg-azul-800 disabled:opacity-60"
        >
          {enviando ? "Un momento…" : "Guardar contraseña"}
        </button>
      </form>
    </>
  );
}
