/* Cliente de la API de Ayni.
 *
 * Todo pasa por el gateway. El navegador nunca llama a un servicio directamente: la
 * validacion del JWT, el CORS y la limitacion de tasa viven ahi, y saltarselos seria
 * saltarse la seguridad entera. Ver §3.4 del documento de diseno. */

const BASE = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

/** Un campo del formulario y por qué no es válido, tal como lo devuelve el servidor. */
export interface ErrorDeCampo {
  campo: string;
  mensaje: string;
}

/** Cuerpo de error segun RFC 7807, con la extension `errores` del contrato. */
export interface Problema {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  errores?: ErrorDeCampo[];
}

export class ErrorDeApi extends Error {
  readonly problema: Problema;
  readonly estado: number;

  constructor(problema: Problema, estado: number) {
    super(problema.title ?? "No pudimos completar la operación");
    this.name = "ErrorDeApi";
    this.problema = problema;
    this.estado = estado;
  }

  /** Errores agrupados por campo, listos para pintar bajo cada input. */
  porCampo(): Record<string, string> {
    const mapa: Record<string, string> = {};
    for (const error of this.problema.errores ?? []) {
      // Se conserva el primero de cada campo. Acumular varios mensajes bajo un mismo
      // input produce un bloque de texto que nadie lee.
      mapa[error.campo] ??= error.mensaje;
    }
    return mapa;
  }
}

export interface SolicitudDeRegistro {
  nombres: string;
  apellidos: string;
  /** `DNI`, `CE` o `PASAPORTE`. */
  tipoDocumento: string;
  numeroDocumento: string;
  /** `aaaa-mm-dd`, que es lo que produce un `<input type="date">` y lo que espera Java. */
  fechaNacimiento: string;
  correo: string;
  celular: string;
  contrasena: string;
  aceptaTerminos: boolean;
}

export interface RespuestaDeRegistro {
  solicitudId: string;
  estado: string;
  mensaje: string;
}

/** Respuesta del primer paso del ingreso · HU-04. */
export interface DesafioDeSegundoFactor {
  desafioId: string;
  /** `true` la primera vez: hay que dar de alta el segundo factor antes de continuar. */
  requiereInscripcion: boolean;
  /** URI `otpauth://` con el que se pinta el QR. Solo viene si hay que inscribirse. */
  uriDeAprovisionamiento: string | null;
}

export interface Sesion {
  tokenDeAcceso: string;
  expiraEn: string;
}

export async function presentarCredenciales(
  correo: string,
  contrasena: string,
): Promise<DesafioDeSegundoFactor> {
  return pedir<DesafioDeSegundoFactor>("/api/v1/sesion", { correo, contrasena });
}

export async function verificarSegundoFactor(
  desafioId: string,
  codigo: string,
): Promise<Sesion> {
  return pedir<Sesion>("/api/v1/sesion/segundo-factor", { desafioId, codigo });
}

/** La cuenta de ahorro tal como la muestra la pantalla final del onboarding. */
export interface CuentaAbierta {
  cuentaId: string;
  numero: string;
  cci: string;
  cciFormateado: string;
  numeroFormateado: string;
  moneda: string;
  estado: string;
  /** TREA vigente del producto, en tanto por ciento. Sale del catalogo, no del codigo. */
  trea: string | null;
  comisionDeMantenimiento: string;
  /**
   * El saldo llega como texto y no como numero, a proposito: JSON no distingue enteros de
   * decimales y JavaScript representa todo con coma flotante, con lo que 12480.65 puede
   * llegar como 12480.649999999999. En texto llega exacto.
   */
  saldo: string;
}

/** Lo mínimo del titular para poder saludarle: nombre de pila y correo enmascarado. */
export interface Titular {
  nombreDePila: string | null;
  /** Enmascarado en origen. La pantalla solo tiene que recordar a dónde se envió el aviso. */
  correo: string;
  estado: string;
}

export async function consultarTitular(usuarioId: string): Promise<Titular> {
  return pedir<Titular>(`/api/v1/usuarios/${usuarioId}/resumen`);
}

/**
 * Consulta la cuenta del titular.
 *
 * Devuelve 404 mientras la cuenta se está abriendo, y eso NO es un error: el evento que la
 * crea viaja por RabbitMQ y tarda unos segundos. Quien llama vuelve a preguntar.
 */
export async function consultarCuenta(usuarioId: string): Promise<CuentaAbierta> {
  return pedir<CuentaAbierta>(`/api/v1/cuentas/titular/${usuarioId}`);
}

export async function registrar(solicitud: SolicitudDeRegistro): Promise<RespuestaDeRegistro> {
  return pedir<RespuestaDeRegistro>("/api/v1/registro", solicitud);
}

/**
 * Una petición POST con su manejo de errores, común a todo.
 *
 * `credentials: "include"` es imprescindible y fácil de olvidar: sin él el navegador no
 * guarda la cookie del token de renovación aunque el servidor la envíe, y la sesión dura
 * exactamente quince minutos sin que nada indique por qué.
 */
async function pedir<T>(ruta: string, cuerpo?: unknown): Promise<T> {
  let respuesta: Response;

  try {
    // Sin cuerpo es una consulta: GET. Mandar un POST con el cuerpo vacío para leer algo
    // funcionaría, pero rompe la caché, los reintentos y cualquier lectura del registro
    // del gateway.
    respuesta = await fetch(`${BASE}${ruta}`, {
      method: cuerpo === undefined ? "GET" : "POST",
      credentials: "include",
      headers: cuerpo === undefined ? {} : { "Content-Type": "application/json" },
      body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
    });
  } catch {
    // fetch solo rechaza por fallo de red, no por codigo de estado. El mensaje dice que
    // hacer a continuacion y no culpa a quien lo lee.
    throw new ErrorDeApi(
      {
        title: "No pudimos conectar con Ayni",
        detail: "Revisa tu conexión e inténtalo de nuevo.",
        status: 0,
      },
      0,
    );
  }

  if (respuesta.ok) {
    return (await respuesta.json()) as T;
  }

  let problema: Problema;
  try {
    problema = (await respuesta.json()) as Problema;
  } catch {
    problema = { title: "No pudimos completar la operación", status: respuesta.status };
  }

  throw new ErrorDeApi(problema, respuesta.status);
}
