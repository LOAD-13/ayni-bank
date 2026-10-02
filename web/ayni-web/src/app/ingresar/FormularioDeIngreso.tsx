"use client";

import { useRouter } from "next/navigation";
import { useCallback, useState } from "react";

import { PasoDeCredenciales } from "@/componentes/ingreso/PasoDeCredenciales";
import { PasoDeSegundoFactor } from "@/componentes/ingreso/PasoDeSegundoFactor";
import { ErrorDeApi, presentarCredenciales, verificarSegundoFactor } from "@/lib/api";
import { guardarSesion } from "@/lib/sesion";

/**
 * Los dos pasos del ingreso · HU-04.
 *
 * Son dos pantallas del prototipo pero un solo componente con estado, y no dos rutas. El
 * motivo es que el desafío dura dos minutos y vive solo en memoria: si el segundo paso
 * fuera una ruta propia, recargarla o llegar a ella desde un marcador dejaría al usuario
 * ante un formulario que no puede funcionar, sin nada que explique por qué.
 */
export function FormularioDeIngreso() {
  const router = useRouter();

  const [desafioId, setDesafioId] = useState<string | null>(null);
  const [uriDeAprovisionamiento, setUri] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [esperaSegundos, setEspera] = useState<number | null>(null);

  const limpiarLaEspera = useCallback(() => setEspera(null), []);

  async function enviarCredenciales(correo: string, contrasena: string) {
    setEnviando(true);
    setError(null);
    try {
      const desafio = await presentarCredenciales(correo, contrasena);
      setDesafioId(desafio.desafioId);
      setUri(desafio.requiereInscripcion ? desafio.uriDeAprovisionamiento : null);
    } catch (fallo) {
      interpretar(fallo);
    } finally {
      setEnviando(false);
    }
  }

  async function enviarCodigo(codigo: string) {
    if (!desafioId) return;

    setEnviando(true);
    setError(null);
    try {
      // El token de acceso queda solo en memoria (nunca en localStorage): si la página se
      // recarga, se recupera con la cookie de renovación. Ver src/lib/sesion.ts.
      guardarSesion(await verificarSegundoFactor(desafioId, codigo));
      router.push("/banca");
    } catch (fallo) {
      interpretar(fallo);
    } finally {
      setEnviando(false);
    }
  }

  /**
   * Traduce el problema del servidor a lo que la pantalla tiene que mostrar.
   *
   * El caso del bloqueo es el único que cambia de estado y no solo de mensaje: el servidor
   * manda cuántos segundos quedan y con eso se pinta la cuenta atrás del diseño.
   */
  function interpretar(fallo: unknown) {
    if (!(fallo instanceof ErrorDeApi)) {
      setError("No pudimos completar el ingreso. Inténtalo de nuevo.");
      return;
    }

    const espera = fallo.problema as { esperaSegundos?: number };
    if (fallo.estado === 423 && typeof espera.esperaSegundos === "number") {
      // Vuelve al primer paso: el desafío ya no sirve de nada si el ingreso está pausado.
      setDesafioId(null);
      setEspera(Math.max(1, espera.esperaSegundos));
      return;
    }

    setError(fallo.problema.detail ?? fallo.message);
  }

  if (desafioId) {
    return (
      <PasoDeSegundoFactor
        uriDeAprovisionamiento={uriDeAprovisionamiento}
        onEnviar={enviarCodigo}
        onVolver={() => {
          setDesafioId(null);
          setError(null);
        }}
        enviando={enviando}
        error={error}
      />
    );
  }

  return (
    <PasoDeCredenciales
      onEnviar={enviarCredenciales}
      enviando={enviando}
      error={error}
      esperaSegundos={esperaSegundos}
      onExpirarLaEspera={limpiarLaEspera}
    />
  );
}
