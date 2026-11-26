"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";

import { Boton } from "@/componentes/Boton";
import { CampoDeTexto } from "@/componentes/CampoDeTexto";
import { useSesion } from "@/componentes/banca/SesionDeBanca";
import {
  consultarAuditoria,
  ErrorDeApi,
  type FiltroDeAuditoria,
  type PaginaDeAuditoria,
} from "@/lib/api";
import { formatearFecha } from "@/lib/formato";
import { permisosDelToken } from "@/lib/sesion";

const PERMISO = "auditoria:leer";
const POR_PAGINA = 20;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Los tipos que puede traer la pista (restricción `ck_evento_auditoria_tipo`). */
const TIPOS = [
  "TRANSFERENCIA_REALIZADA",
  "DEPOSITO_SIMULADO",
  "OPERACION_REPETIDA",
  "OPERACION_RECHAZADA",
  "INTERES_CAPITALIZADO",
  "APERTURA_RECHAZADA",
  "ACCESO_AJENO_DENEGADO",
  "INTERBANCARIA_ENVIADA",
  "INTERBANCARIA_COMPLETADA",
  "INTERBANCARIA_REVERTIDA",
  "TARJETA_EMITIDA",
  "PAN_REVELADO",
  "TARJETA_CONGELADA",
  "TARJETA_DESCONGELADA",
  "CONTROL_TARJETA_ACTUALIZADO",
  "CONSUMO_TARJETA_REALIZADO",
  "CONVERSION_REALIZADA",
];

/**
 * Pista de auditoría para el auditor (HU-15, AYNI-284). Es de solo lectura: no hay ningún botón
 * que escriba, y el servidor no expone ninguna operación que lo haga. Sin el permiso
 * `auditoria:leer` no se pide nada y se explica por qué.
 */
export default function PaginaDeAuditoria() {
  const { token } = useSesion();
  const [permitido, setPermitido] = useState<boolean | null>(null);
  const [filtro, setFiltro] = useState({ usuarioId: "", tipo: "", desde: "", hasta: "" });
  // Lo que se pidió por última vez: la paginación sigue con eso y no con lo que haya a medio escribir.
  const [aplicado, setAplicado] = useState<FiltroDeAuditoria>({});
  const [errores, setErrores] = useState<Record<string, string>>({});
  const [resultado, setResultado] = useState<PaginaDeAuditoria | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(false);

  const buscar = useCallback(
    async (pagina: number, criterios: FiltroDeAuditoria) => {
      setCargando(true);
      setError(null);
      setAplicado(criterios);
      try {
        const consulta: FiltroDeAuditoria = { ...criterios, pagina, tamano: POR_PAGINA };
        setResultado(await consultarAuditoria(await token(), consulta));
      } catch (fallo) {
        if (fallo instanceof ErrorDeApi && fallo.estado === 403) {
          setPermitido(false);
        } else {
          setError(
            fallo instanceof ErrorDeApi
              ? (fallo.problema.detail ?? fallo.message)
              : "No pudimos leer la pista de auditoría. Inténtalo de nuevo.",
          );
        }
      } finally {
        setCargando(false);
      }
    },
    [token],
  );

  useEffect(() => {
    let vivo = true;
    void token().then((vigente) => {
      if (!vivo) return;
      const conPermiso = permisosDelToken(vigente).includes(PERMISO);
      setPermitido(conPermiso);
      if (conPermiso) void buscar(0, {});
    });
    return () => {
      vivo = false;
    };
  }, [token, buscar]);

  function filtrar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    const nuevos: Record<string, string> = {};
    if (filtro.usuarioId && !UUID.test(filtro.usuarioId.trim())) {
      nuevos.usuarioId = "Escribe el identificador completo del usuario, con sus guiones.";
    }
    if (filtro.desde && filtro.hasta && filtro.desde > filtro.hasta) {
      nuevos.hasta = "La fecha final no puede ser anterior a la inicial.";
    }
    setErrores(nuevos);
    if (Object.keys(nuevos).length > 0) return;
    void buscar(0, { ...filtro, usuarioId: filtro.usuarioId.trim() });
  }

  if (permitido === null) {
    return (
      <p role="status" className="text-body text-gris-700">
        Comprobando tu rol…
      </p>
    );
  }
  if (!permitido) {
    return (
      <section aria-labelledby="titulo-sin-acceso">
        <h1 id="titulo-sin-acceso" className="text-h1 font-bold text-azul-800">
          Pista de auditoría
        </h1>
        <p role="alert" className="mt-4 text-body text-gris-700">
          Tu rol no permite leer la pista de auditoría.
        </p>
      </section>
    );
  }

  const totalDePaginas = resultado ? Math.max(1, Math.ceil(resultado.total / resultado.tamano)) : 1;

  return (
    <div className="flex flex-col gap-6">
      <header>
        <h1 className="text-h1 font-bold text-azul-800">Pista de auditoría</h1>
        <p className="text-body text-gris-700">
          Quién hizo qué, desde dónde y con qué resultado. Solo lectura: lo registrado no se puede
          cambiar ni borrar.
        </p>
      </header>

      <form
        onSubmit={filtrar}
        noValidate
        aria-label="Filtrar la pista"
        className="grid grid-cols-1 gap-4 rounded-[20px] border border-azul-200 bg-blanco p-6 md:grid-cols-2"
      >
        <CampoDeTexto
          etiqueta="Usuario"
          name="usuarioId"
          autoComplete="off"
          placeholder="Identificador del usuario"
          value={filtro.usuarioId}
          onChange={(e) => setFiltro({ ...filtro, usuarioId: e.target.value })}
          error={errores.usuarioId}
        />
        <div className="flex flex-col gap-1.5">
          <label htmlFor="tipo-de-evento" className="text-small font-medium text-gris-700">
            Acción
          </label>
          <select
            id="tipo-de-evento"
            value={filtro.tipo}
            onChange={(e) => setFiltro({ ...filtro, tipo: e.target.value })}
            className="min-h-[44px] rounded-md border border-gris-300 bg-blanco px-4 text-body text-gris-900"
          >
            <option value="">Todas</option>
            {TIPOS.map((tipo) => (
              <option key={tipo} value={tipo}>
                {tipo}
              </option>
            ))}
          </select>
        </div>
        <CampoDeTexto
          etiqueta="Desde"
          name="desde"
          type="date"
          value={filtro.desde}
          onChange={(e) => setFiltro({ ...filtro, desde: e.target.value })}
        />
        <CampoDeTexto
          etiqueta="Hasta"
          name="hasta"
          type="date"
          value={filtro.hasta}
          onChange={(e) => setFiltro({ ...filtro, hasta: e.target.value })}
          error={errores.hasta}
        />
        <div className="md:col-span-2">
          <Boton type="submit" cargando={cargando}>
            Filtrar
          </Boton>
        </div>
      </form>

      {error && (
        <p
          role="alert"
          className="rounded-lg border border-error bg-blanco p-4 text-body text-error"
        >
          {error}
        </p>
      )}

      {resultado && (
        <section aria-labelledby="titulo-resultados" className="flex flex-col gap-4">
          <h2 id="titulo-resultados" className="text-h2 font-semibold text-azul-800">
            {resultado.total} {resultado.total === 1 ? "registro" : "registros"}
          </h2>
          {resultado.eventos.length === 0 ? (
            <p className="text-body text-gris-700">No hay registros con esos filtros.</p>
          ) : (
            <div
              role="region"
              aria-label="Registros de la pista de auditoría"
              // En el celular la tabla se desplaza de lado: sin foco no se recorre con teclado.
              tabIndex={0}
              className="overflow-x-auto rounded-[16px] border border-azul-200 bg-blanco"
            >
              <table className="w-full text-left text-small">
                <caption className="sr-only">Registros de la pista de auditoría</caption>
                <thead className="bg-azul-050 text-gris-700">
                  <tr>
                    {["Momento", "Usuario", "Acción", "Recurso", "IP", "Resultado", "Detalle"].map(
                      (columna) => (
                        <th key={columna} scope="col" className="px-4 py-3 font-semibold">
                          {columna}
                        </th>
                      ),
                    )}
                  </tr>
                </thead>
                <tbody>
                  {resultado.eventos.map((e) => (
                    <tr key={e.id} className="border-t border-azul-100 align-top">
                      <td className="px-4 py-3 whitespace-nowrap">
                        {formatearFecha(e.ocurridoEn)}
                      </td>
                      <td className="px-4 py-3 font-mono text-caption break-all">{e.usuarioId}</td>
                      <td className="px-4 py-3">{e.tipo}</td>
                      <td className="px-4 py-3">{e.recurso ?? "—"}</td>
                      <td className="px-4 py-3">{e.ip ?? "—"}</td>
                      <td className="px-4 py-3">
                        {e.resultado === "EXITO" ? "Éxito" : "Rechazado"}
                      </td>
                      <td className="px-4 py-3">{e.motivo ?? "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <nav aria-label="Páginas de la pista" className="flex items-center gap-4">
            <Boton
              variante="contorno"
              disabled={cargando || resultado.pagina === 0}
              onClick={() => void buscar(resultado.pagina - 1, aplicado)}
            >
              Anterior
            </Boton>
            <span className="text-small text-gris-700">
              Página {resultado.pagina + 1} de {totalDePaginas}
            </span>
            <Boton
              variante="contorno"
              disabled={cargando || resultado.pagina + 1 >= totalDePaginas}
              onClick={() => void buscar(resultado.pagina + 1, aplicado)}
            >
              Siguiente
            </Boton>
          </nav>
        </section>
      )}
    </div>
  );
}
