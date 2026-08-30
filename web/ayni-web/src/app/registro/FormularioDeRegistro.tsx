"use client";

import { ChevronDown, Circle, CircleCheck, TriangleAlert } from "lucide-react";
import Link from "next/link";
import { useId, useState, type FormEvent } from "react";

import {
  cumpleLaPolitica,
  esCelularPeruano,
  esCorreoValido,
  fortalezaDe,
  REQUISITOS,
  requisitosCumplidos,
  type Fortaleza,
} from "@/dominio/politicaDeContrasena";
import { ErrorDeApi, registrar, type RespuestaDeRegistro } from "@/lib/api";

type Campo =
  | "nombres"
  | "apellidos"
  | "numeroDocumento"
  | "fechaNacimiento"
  | "celular"
  | "correo"
  | "contrasena"
  | "confirmacion"
  | "aceptaTerminos";

type Errores = Partial<Record<Campo, string>>;

const TEXTO_DE_FORTALEZA: Record<Fortaleza, string> = {
  vacia: "",
  debil: "Fortaleza: débil",
  media: "Fortaleza: media",
  fuerte: "Fortaleza: fuerte",
};

/**
 * Formulario de registro · HU-01, según el diseño aprobado en pen.dev.
 *
 * **Por qué se piden los datos de identidad si el OCR va a leerlos igualmente.**
 * Precisamente por eso. Lo que se escribe aquí es el término de comparación: en HU-02 el
 * servicio de KYC extrae del DNI el nombre, el número y la fecha de nacimiento, y los
 * contrasta con lo declarado. Un OCR que lee mal produce datos plausibles pero
 * equivocados, y sin nada contra lo que compararlos nadie se entera. Con las dos lecturas
 * la discrepancia salta y la solicitud va a revisión manual.
 */
export function FormularioDeRegistro() {
  const [nombres, setNombres] = useState("");
  const [apellidos, setApellidos] = useState("");
  const [tipoDocumento, setTipoDocumento] = useState("DNI");
  const [numeroDocumento, setNumeroDocumento] = useState("");
  const [fechaNacimiento, setFechaNacimiento] = useState("");
  const [celular, setCelular] = useState("");
  const [correo, setCorreo] = useState("");
  const [contrasena, setContrasena] = useState("");
  const [confirmacion, setConfirmacion] = useState("");
  const [aceptaTerminos, setAceptaTerminos] = useState(false);

  const [errores, setErrores] = useState<Errores>({});
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [resultado, setResultado] = useState<RespuestaDeRegistro | null>(null);

  const cumplidos = requisitosCumplidos(contrasena);
  const faltantes = REQUISITOS.length - cumplidos.size;
  const fortaleza = fortalezaDe(contrasena);

  function validar(): Errores {
    const encontrados: Errores = {};
    if (!nombres.trim()) encontrados.nombres = "Escribe tus nombres.";
    if (!apellidos.trim()) encontrados.apellidos = "Escribe tus apellidos.";
    if (!/^\d{8}$/.test(numeroDocumento)) {
      encontrados.numeroDocumento = "El DNI tiene ocho dígitos.";
    }
    if (!fechaNacimiento) {
      encontrados.fechaNacimiento = "Indica tu fecha de nacimiento.";
    } else if (!esMayorDeEdad(fechaNacimiento)) {
      // El servidor lo vuelve a comprobar; esto solo evita el viaje de ida y vuelta.
      encontrados.fechaNacimiento = "Debes tener al menos 18 años para abrir una cuenta.";
    }
    if (!esCelularPeruano(celular)) {
      encontrados.celular = "El celular debe tener nueve dígitos y empezar en 9.";
    }
    if (!esCorreoValido(correo)) {
      encontrados.correo = "Escribe un correo electrónico válido.";
    }
    if (!cumpleLaPolitica(contrasena)) {
      encontrados.contrasena = `Aún faltan ${faltantes} requisitos. Complétalos y podrás continuar.`;
    }
    if (confirmacion !== contrasena) {
      encontrados.confirmacion = "Las contraseñas no coinciden.";
    }
    if (!aceptaTerminos) {
      encontrados.aceptaTerminos = "Debes autorizar el tratamiento de tus datos para continuar.";
    }
    return encontrados;
  }

  async function alEnviar(evento: FormEvent) {
    evento.preventDefault();
    setErrorGeneral(null);

    const encontrados = validar();
    setErrores(encontrados);
    if (Object.keys(encontrados).length > 0) return;

    setEnviando(true);
    try {
      setResultado(
        await registrar({
          nombres,
          apellidos,
          tipoDocumento,
          numeroDocumento,
          fechaNacimiento,
          correo,
          celular,
          contrasena,
          aceptaTerminos,
        }),
      );
    } catch (error) {
      if (error instanceof ErrorDeApi) {
        const porCampo = traducirCampos(error.porCampo());
        setErrores(porCampo);
        setErrorGeneral(
          Object.keys(porCampo).length > 0 ? null : (error.problema.detail ?? error.message),
        );
      } else {
        setErrorGeneral("No pudimos completar tu registro. Inténtalo de nuevo.");
      }
    } finally {
      setEnviando(false);
    }
  }

  if (resultado) {
    return (
      <div
        role="status"
        className="rounded-[16px] border border-azul-200 bg-azul-050 p-8 text-center"
      >
        <CircleCheck aria-hidden="true" className="mx-auto h-10 w-10 text-exito" />
        <h2 className="mt-4 text-[24px] font-bold text-azul-800">Revisa tu correo</h2>
        <p className="mt-2 text-[15px] text-gris-700">{resultado.mensaje}</p>
        <p className="mt-5 text-[12px] text-gris-500">
          Referencia de tu solicitud:{" "}
          <span className="cifra text-gris-700">{resultado.solicitudId}</span>
        </p>
      </div>
    );
  }

  return (
    <form onSubmit={alEnviar} noValidate className="flex flex-col gap-5">
      {errorGeneral && (
        <p
          role="alert"
          className="flex items-start gap-2.5 rounded-[12px] border border-error bg-blanco p-4 text-[13.5px] text-error"
        >
          <TriangleAlert aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0" />
          {errorGeneral}
        </p>
      )}

      <div className="grid gap-5 sm:grid-cols-2">
        <Campo
          etiqueta="Nombres"
          placeholder="Ana Lucía"
          autoComplete="given-name"
          value={nombres}
          onChange={setNombres}
          error={errores.nombres}
        />
        <Campo
          etiqueta="Apellidos"
          placeholder="Quispe Mendoza"
          autoComplete="family-name"
          value={apellidos}
          onChange={setApellidos}
          error={errores.apellidos}
        />
      </div>

      <div className="grid gap-5 sm:grid-cols-2">
        <Seleccion
          etiqueta="Tipo de documento"
          ayuda="Documento de identidad peruano"
          value={tipoDocumento}
          onChange={setTipoDocumento}
        />
        <Campo
          etiqueta="Número de documento"
          placeholder="••••••78"
          inputMode="numeric"
          maxLength={8}
          ayuda="Se guarda cifrado y se muestra siempre enmascarado"
          value={numeroDocumento}
          onChange={setNumeroDocumento}
          error={errores.numeroDocumento}
        />
      </div>

      <div className="grid gap-5 sm:grid-cols-2">
        <Campo
          etiqueta="Fecha de nacimiento"
          type="date"
          autoComplete="bday"
          value={fechaNacimiento}
          onChange={setFechaNacimiento}
          error={errores.fechaNacimiento}
        />
        <Campo
          etiqueta="Celular"
          type="tel"
          inputMode="numeric"
          maxLength={9}
          placeholder="987 654 321"
          autoComplete="tel-national"
          prefijo="+51"
          value={celular}
          onChange={setCelular}
          error={errores.celular}
        />
      </div>

      <Campo
        etiqueta="Correo electrónico"
        type="email"
        inputMode="email"
        autoComplete="email"
        placeholder="ana.quispe@ejemplo.pe"
        ayuda="Si el correo está disponible, te enviaremos un enlace de verificación."
        value={correo}
        onChange={setCorreo}
        error={errores.correo}
      />

      <div className="grid gap-5 sm:grid-cols-2">
        <Campo
          etiqueta="Contraseña"
          type="password"
          autoComplete="new-password"
          value={contrasena}
          onChange={setContrasena}
          error={errores.contrasena}
        />
        <Campo
          etiqueta="Confirmar contraseña"
          type="password"
          autoComplete="new-password"
          value={confirmacion}
          onChange={setConfirmacion}
          error={errores.confirmacion}
        />
      </div>

      {/* La política se muestra completa desde el primer momento, con el estado de cada
          requisito en palabras además del icono: el color por sí solo no es accesible. */}
      <div className="rounded-[14px] border border-azul-200 bg-azul-050 p-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="text-[13.5px] font-bold text-azul-800">Tu contraseña debe tener</p>
          {fortaleza !== "vacia" && (
            <p className="flex items-center gap-2 text-[12.5px] font-semibold text-gris-700">
              {TEXTO_DE_FORTALEZA[fortaleza]}
              <span aria-hidden="true" className="flex gap-1">
                {[0, 1, 2].map((tramo) => (
                  <span
                    key={tramo}
                    className={`h-1.5 w-5 rounded-full ${
                      tramo < { vacia: 0, debil: 1, media: 2, fuerte: 3 }[fortaleza]
                        ? "bg-dorado-500"
                        : "bg-gris-300"
                    }`}
                  />
                ))}
              </span>
            </p>
          )}
        </div>

        <ul className="mt-3 flex flex-col gap-2">
          {REQUISITOS.map(({ clave, texto }) => {
            const cumplido = cumplidos.has(clave);
            return (
              <li key={clave} className="flex items-center gap-2.5 text-[13.5px]">
                {cumplido ? (
                  <CircleCheck aria-hidden="true" className="h-4 w-4 shrink-0 text-exito" />
                ) : (
                  <Circle aria-hidden="true" className="h-4 w-4 shrink-0 text-gris-300" />
                )}
                <span className={cumplido ? "text-gris-700" : "text-gris-500"}>{texto}</span>
                <span
                  className={`text-[12px] font-semibold ${cumplido ? "text-exito" : "text-aviso"}`}
                >
                  {cumplido ? "Cumplido" : "Falta"}
                </span>
              </li>
            );
          })}
        </ul>
      </div>

      <div className="rounded-[14px] border border-gris-300 p-5">
        <label className="flex items-start gap-3 text-[14px] text-gris-900">
          {/* Sin marcar por defecto: un consentimiento premarcado no es consentimiento
              informado, y la Ley N.o 29733 no lo admite. */}
          <input
            type="checkbox"
            checked={aceptaTerminos}
            onChange={(e) => setAceptaTerminos(e.target.checked)}
            aria-invalid={errores.aceptaTerminos ? true : undefined}
            aria-describedby={errores.aceptaTerminos ? "error-terminos" : undefined}
            className="mt-0.5 h-5 w-5 shrink-0 rounded border-gris-300 accent-azul-700"
          />
          <span>
            Autorizo el tratamiento de mis datos personales, incluidos los datos biométricos de mi
            rostro, para verificar mi identidad.
          </span>
        </label>
        <p className="mt-2.5 pl-8 text-[12.5px] leading-[1.5] text-gris-500">
          Conforme a la Ley N.º 29733 de Protección de Datos Personales. Puedes revocar esta
          autorización y pedir la eliminación de tus imágenes cuando quieras.
        </p>
        {errores.aceptaTerminos && (
          <p
            id="error-terminos"
            role="alert"
            className="mt-2 pl-8 text-[12.5px] font-medium text-error"
          >
            {errores.aceptaTerminos}
          </p>
        )}
      </div>

      <div className="flex flex-col-reverse items-center gap-4 sm:flex-row sm:justify-between">
        <Link href="/pendiente" className="text-[14px] font-semibold text-azul-600 hover:underline">
          Ya tengo cuenta · Iniciar sesión
        </Link>

        <button
          type="submit"
          disabled={enviando}
          aria-busy={enviando}
          className="inline-flex min-h-[44px] w-full items-center justify-center gap-2.5 rounded-full bg-azul-700 px-7 text-[15px] font-bold text-blanco hover:bg-azul-800 disabled:opacity-60 sm:w-auto"
        >
          {enviando ? "Un momento…" : "Continuar con mi DNI"}
          {!enviando && <span aria-hidden="true">→</span>}
        </button>
      </div>
    </form>
  );
}

/**
 * En el Perú no se puede celebrar un contrato bancario a nombre propio antes de los
 * dieciocho. Se compara mes y día, no solo el año: quien los cumple mañana todavía no
 * los tiene.
 */
function esMayorDeEdad(fecha: string): boolean {
  const nacimiento = new Date(`${fecha}T00:00:00`);
  if (Number.isNaN(nacimiento.getTime())) return false;

  const limite = new Date();
  limite.setFullYear(limite.getFullYear() - 18);
  return nacimiento <= limite;
}

/**
 * Reubica los errores del servidor que no corresponden a un campo del formulario.
 *
 * La mayoría de edad se valida sobre el DTO entero y llega bajo el nombre del método que
 * la comprueba, no bajo el del campo. Sin esta traducción el mensaje se pierde: aparece
 * el error general y la fecha de nacimiento queda sin marcar, que es justo donde el
 * usuario tiene que mirar.
 */
function traducirCampos(delServidor: Record<string, string>): Errores {
  const { mayorDeEdad, ...resto } = delServidor;
  return mayorDeEdad ? { ...resto, fechaNacimiento: mayorDeEdad } : resto;
}

interface CampoProps {
  etiqueta: string;
  value: string;
  onChange: (valor: string) => void;
  type?: string;
  placeholder?: string;
  ayuda?: string;
  error?: string;
  inputMode?: "text" | "numeric" | "email" | "tel";
  autoComplete?: string;
  maxLength?: number;
  /** Prefijo fijo que se muestra dentro del campo, como el «+51» del celular. */
  prefijo?: string;
}

function Campo({ etiqueta, value, onChange, ayuda, error, prefijo, ...resto }: CampoProps) {
  const id = useId();
  const descritoPor = [ayuda ? `${id}-ayuda` : null, error ? `${id}-error` : null]
    .filter(Boolean)
    .join(" ");

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-[13.5px] font-semibold text-gris-700">
        {etiqueta}
      </label>
      {/* El prefijo va como texto fijo dentro del campo y no como parte del valor: el
          usuario no puede borrarlo por accidente, y el dominio sigue recibiendo los nueve
          dígitos que espera, sin tener que limpiar el «+51» después. */}
      <div
        className={`campo flex min-h-[46px] items-center rounded-[12px] border bg-blanco ${
          error ? "border-error" : "border-gris-300"
        }`}
      >
        {prefijo && (
          <span
            aria-hidden="true"
            className="border-r border-gris-300 py-2.5 pr-3 pl-4 text-[15px] font-medium text-gris-500"
          >
            {prefijo}
          </span>
        )}
        <input
          {...resto}
          id={id}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          aria-invalid={error ? true : undefined}
          aria-describedby={descritoPor || undefined}
          className="min-w-0 flex-1 bg-transparent px-4 text-[15px] text-gris-900 outline-none placeholder:text-gris-500"
        />
      </div>
      {error ? (
        <p id={`${id}-error`} role="alert" className="text-[12.5px] font-medium text-error">
          {error}
        </p>
      ) : (
        ayuda && (
          <p id={`${id}-ayuda`} className="text-[12px] text-gris-500">
            {ayuda}
          </p>
        )
      )}
    </div>
  );
}

function Seleccion({
  etiqueta,
  ayuda,
  value,
  onChange,
}: {
  etiqueta: string;
  ayuda: string;
  value: string;
  onChange: (valor: string) => void;
}) {
  const id = useId();
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-[13.5px] font-semibold text-gris-700">
        {etiqueta}
      </label>
      {/* El borde vive en el contenedor y no en el <select>, para que el foco se
          dibuje sobre el mismo marco que en los demás campos. */}
      <div className="campo relative flex min-h-[46px] items-center rounded-[12px] border border-gris-300 bg-blanco">
        <select
          id={id}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          aria-describedby={`${id}-ayuda`}
          className="w-full appearance-none bg-transparent px-4 pr-10 text-[15px] text-gris-900"
        >
          <option value="DNI">DNI</option>
          <option value="CE">Carné de extranjería</option>
          <option value="PASAPORTE">Pasaporte</option>
        </select>
        <ChevronDown
          aria-hidden="true"
          className="pointer-events-none absolute top-1/2 right-4 h-4 w-4 -translate-y-1/2 text-gris-500"
        />
      </div>
      <p id={`${id}-ayuda`} className="text-[12px] text-gris-500">
        {ayuda}
      </p>
    </div>
  );
}
