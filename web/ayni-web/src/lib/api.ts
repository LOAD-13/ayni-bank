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
  /** Motivo de negocio, p. ej. SIN_CUENTA o SALDO_INSUFICIENTE. */
  codigo?: string;
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

export type TipoDeSegundoFactor = "APP_AUTENTICADORA" | "CORREO_ELECTRONICO" | "SMS";

export interface MetodoSegundoFactor {
  id: string;
  usuarioId: string;
  tipo: TipoDeSegundoFactor;
  secreto: string | null;
  confirmado: boolean;
}

export interface DesafioCodigo {
  id: string;
  usuarioId: string;
  tipoFactor: string;
  expiraEn: string;
  intentosRealizados: number;
  verificado: boolean;
}

export async function seleccionarMetodoSegundoFactor(
  usuarioId: string,
  tipo: TipoDeSegundoFactor,
): Promise<MetodoSegundoFactor> {
  return pedir<MetodoSegundoFactor>(`/api/v1/usuarios/${usuarioId}/segundo-factor/metodo`, {
    tipo,
  });
}

export async function generarDesafioCodigo(
  usuarioId: string,
  tipoFactor: TipoDeSegundoFactor,
): Promise<DesafioCodigo> {
  return pedir<DesafioCodigo>(`/api/v1/segundo-factor/desafio/usuario/${usuarioId}/generar`, {
    tipoFactor,
  });
}

export async function verificarDesafioCodigo(desafioId: string, codigo: string): Promise<void> {
  return pedir<void>(`/api/v1/segundo-factor/desafio/${desafioId}/verificar`, { codigo });
}

export async function reenviarCodigoRegistro(
  usuarioId: string,
  tipoFactor: TipoDeSegundoFactor,
): Promise<DesafioCodigo> {
  return pedir<DesafioCodigo>(`/api/v1/registro/${usuarioId}/codigo/reenviar`, { tipoFactor });
}

export async function verificarCodigoRegistro(
  usuarioId: string,
  tipoFactor: TipoDeSegundoFactor,
  codigo: string,
): Promise<void> {
  return pedir<void>(`/api/v1/registro/${usuarioId}/codigo/verificar`, { tipoFactor, codigo });
}

export async function presentarCredenciales(
  correo: string,
  contrasena: string,
): Promise<DesafioDeSegundoFactor> {
  return pedir<DesafioDeSegundoFactor>("/api/v1/sesion", { correo, contrasena });
}

export async function verificarSegundoFactor(desafioId: string, codigo: string): Promise<Sesion> {
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
  /** Solicitud de apertura que aún se puede continuar, si la hay. */
  solicitudPendiente?: string | null;
  /** Estado de la última solicitud de apertura (EN_REVISION_MANUAL, CADUCADA…). */
  estadoSolicitud?: string | null;
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

/** Que cara/imagen del proceso de verificacion se esta subiendo · HU-02. */
export type TipoDeDocumentoKyc = "ANVERSO" | "REVERSO" | "SELFIE";

/** Los dos lados del DNI, que son los que se evaluan y se leen. */
export type LadoDelDni = "ANVERSO" | "REVERSO";

/**
 * Formulario pre-firmado para subir un documento directo a MinIO.
 *
 * Es una politica POST y no una URL PUT: MinIO rechaza la subida si el archivo supera
 * 5 MB o no es del tipo pedido, sin depender de que el navegador lo compruebe.
 */
export interface UrlDeSubida {
  /** Destino del formulario (el bucket). */
  url: string;
  /** Campos a enviar tal cual, antes del archivo: clave, tipo, politica y firma. */
  campos: Record<string, string>;
  /** Clave con la que queda guardado; se devuelve al pedir la evaluacion. */
  claveDeObjeto: string;
  /** Momento en que el formulario deja de ser valido (5 minutos despues de emitido). */
  expiraEn: string;
}

/**
 * Pide el formulario con el que subir un documento KYC directamente a MinIO.
 *
 * No sube nada todavia: solo firma el destino. La subida real la hace
 * {@link subirDocumento}, con el archivo elegido por la persona.
 */
export async function solicitarUrlDeSubida(
  solicitudId: string,
  tipoDocumento: TipoDeDocumentoKyc,
  extension: string,
): Promise<UrlDeSubida> {
  return pedir<UrlDeSubida>(`/api/v1/solicitudes/${solicitudId}/documentos/url-de-subida`, {
    tipoDocumento,
    extension,
  });
}

/**
 * Sube el archivo directamente a MinIO con el formulario pre-firmado.
 *
 * A diferencia de {@link pedir}, esta llamada NO pasa por el gateway ni lleva
 * `credentials`: el formulario ya trae su propia autorizacion firmada. Los campos van
 * primero y el archivo al final, porque S3 ignora todo lo que llegue despues del campo
 * `file`. Ver diseno-base.md §3.4-3.5: "las imagenes no atraviesan la API".
 */
export async function subirDocumento(destino: UrlDeSubida, archivo: File): Promise<void> {
  const formulario = new FormData();
  for (const [campo, valor] of Object.entries(destino.campos)) {
    formulario.append(campo, valor);
  }
  formulario.append("file", archivo);

  let respuesta: Response;

  try {
    respuesta = await fetch(destino.url, { method: "POST", body: formulario });
  } catch {
    throw new ErrorDeApi(
      {
        title: "No pudimos subir el documento",
        detail: "Revisa tu conexión e inténtalo de nuevo.",
        status: 0,
      },
      0,
    );
  }

  if (!respuesta.ok) {
    throw new ErrorDeApi(
      {
        title: "No pudimos subir el documento",
        // La politica rechaza el archivo con 400 (EntityTooLarge, mas de 5 MB) o 403 (tipo
        // distinto del firmado). Comprobado contra MinIO en la prueba de punta a punta.
        detail:
          respuesta.status === 400 || respuesta.status === 403
            ? "El archivo no cumple los requisitos: debe ser una foto de 5 MB como máximo."
            : undefined,
        status: respuesta.status,
      },
      respuesta.status,
    );
  }
}

/** Cómo termina un paso de la verificación del DNI · HU-02. */
export type EstadoDelPasoKyc =
  "ACEPTADO" | "RECHAZADO" | "EN_REVISION_MANUAL" | "VERIFICACION_DIFERIDA";

/** Por qué se rechazó una foto, para decirle a la persona qué repetir. */
export type MotivoDeRechazo =
  "NO_ES_DNI" | "ENCUADRE" | "DESENFOQUE" | "REFLEJO" | "ILUMINACION" | "LADO_INCORRECTO";

export interface ResultadoDeCaptura {
  estado: EstadoDelPasoKyc;
  /** Solo si se rechazó. */
  motivo?: MotivoDeRechazo;
  /** Solo si se rechazó: fotos que quedan de ese lado. */
  intentosRestantes?: number;
}

/** Pide que se evalúe la foto recién subida de un lado del DNI. */
export async function evaluarCaptura(
  solicitudId: string,
  tipoDocumento: LadoDelDni,
  claveDeObjeto: string,
): Promise<ResultadoDeCaptura> {
  return pedir<ResultadoDeCaptura>(`/api/v1/solicitudes/${solicitudId}/documentos`, {
    tipoDocumento,
    claveDeObjeto,
  });
}

/** Lo que leyó el OCR, para que el titular lo confirme. El número llega enmascarado. */
export interface DatosLeidosDelDni {
  numeroEnmascarado: string;
  nombres: string;
  apellidos: string;
  /** `aaaa-mm-dd`. */
  fechaNacimiento: string;
  sexo: "M" | "F";
  /** Ausente si el OCR no la encontró. */
  fechaEmision?: string;
  /** Si vienen del MRZ con sus dígitos verificadores válidos. */
  confiable: boolean;
}

export interface ResultadoDeExtraccion {
  estado: EstadoDelPasoKyc;
  datos?: DatosLeidosDelDni;
  /** Solo si no se pudo leer: fotos del reverso que quedan. */
  intentosRestantes?: number;
}

/** Lee por OCR los datos del DNI a partir de las dos fotos aceptadas. */
export async function extraerDatosDelDni(solicitudId: string): Promise<ResultadoDeExtraccion> {
  // POST con cuerpo vacío: lanza el OCR, no es una consulta.
  return pedir<ResultadoDeExtraccion>(
    `/api/v1/solicitudes/${solicitudId}/documentos/extraccion`,
    {},
  );
}

export interface DatosConfirmados {
  /** Vacío conserva el número leído; solo se envía si el titular lo corrige. */
  numero?: string;
  nombres: string;
  apellidos: string;
  fechaNacimiento: string;
  sexo: "M" | "F";
  fechaEmision: string;
}

/** El titular confirma o corrige lo leído; se contrasta con lo que declaró al registrarse. */
export async function confirmarDatosDelDni(
  solicitudId: string,
  datos: DatosConfirmados,
): Promise<{ estado: "ACEPTADO" | "EN_REVISION_MANUAL" }> {
  return pedir<{ estado: "ACEPTADO" | "EN_REVISION_MANUAL" }>(
    `/api/v1/solicitudes/${solicitudId}/identidad/confirmacion`,
    datos,
  );
}

/**
 * Una petición POST con su manejo de errores, común a todo.
 *
 * `credentials: "include"` es imprescindible y fácil de olvidar: sin él el navegador no
 * guarda la cookie del token de renovación aunque el servidor la envíe, y la sesión dura
 * exactamente quince minutos sin que nada indique por qué.
 */
interface Opciones {
  metodo?: "GET" | "POST" | "DELETE";
  /** Token de acceso para las rutas protegidas. Viaja como `Authorization: Bearer`. */
  token?: string;
  /** Clave de idempotencia de las operaciones monetarias. */
  idempotencia?: string;
}

async function pedir<T>(ruta: string, cuerpo?: unknown, opciones: Opciones = {}): Promise<T> {
  let respuesta: Response;

  const cabeceras: Record<string, string> = {};
  if (cuerpo !== undefined) cabeceras["Content-Type"] = "application/json";
  if (opciones.token) cabeceras.Authorization = `Bearer ${opciones.token}`;
  if (opciones.idempotencia) cabeceras["Idempotency-Key"] = opciones.idempotencia;

  try {
    // Sin cuerpo es una consulta: GET. Mandar un POST con el cuerpo vacío para leer algo
    // funcionaría, pero rompe la caché, los reintentos y cualquier lectura del registro
    // del gateway.
    respuesta = await fetch(`${BASE}${ruta}`, {
      method: opciones.metodo ?? (cuerpo === undefined ? "GET" : "POST"),
      credentials: "include",
      headers: cabeceras,
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
    // 204 No Content (cerrar sesión) no trae cuerpo que leer.
    return (respuesta.status === 204 ? undefined : await respuesta.json()) as T;
  }

  let problema: Problema;
  try {
    problema = (await respuesta.json()) as Problema;
  } catch {
    problema = { title: "No pudimos completar la operación", status: respuesta.status };
  }

  throw new ErrorDeApi(problema, respuesta.status);
}

// ─── Sesión ──────────────────────────────────────────────────────────────

/** Pide un token nuevo con la cookie de renovación. Rota la cookie en cada uso. */
export async function renovarSesion(): Promise<Sesion> {
  return pedir<Sesion>("/api/v1/sesion/renovacion", {});
}

/** Invalida la familia del token de renovación y borra la cookie. */
export async function cerrarSesion(): Promise<void> {
  return pedir<void>("/api/v1/sesion", undefined, { metodo: "DELETE" });
}

// ─── Banca · HU-07 y HU-08 ───────────────────────────────────────────────

/** Un asiento de la cuenta, tal como lo lista el panel. */
export interface Movimiento {
  movimientoId: string;
  tipo: "CARGO" | "ABONO";
  /** Siempre positivo y como texto: el signo lo da `tipo`. */
  importe: string;
  moneda: string;
  concepto: string;
  registradoEn: string;
}

/** Lo que devuelve una operación terminada. Las cuentas llegan enmascaradas. */
export interface Comprobante {
  movimientoId: string;
  tipo: "TRANSFERENCIA" | "DEPOSITO_SIMULADO";
  importe: string;
  moneda: string;
  cuentaOrigen: string;
  cuentaDestino: string;
  concepto: string;
  registradoEn: string;
  saldoDisponible: string;
}

export async function consultarMiCuenta(token: string): Promise<CuentaAbierta> {
  return pedir<CuentaAbierta>("/api/v1/cuentas/mia", undefined, { token });
}

export async function consultarMovimientos(token: string, limite = 10): Promise<Movimiento[]> {
  return pedir<Movimiento[]>(`/api/v1/cuentas/mia/movimientos?limite=${limite}`, undefined, {
    token,
  });
}

/**
 * Deposita dinero de prueba en la cuenta propia.
 *
 * La clave de idempotencia la genera quien llama UNA vez por intento de operación: si la
 * red falla y se reintenta con la misma clave, el servidor devuelve el mismo comprobante
 * en lugar de depositar dos veces.
 */
export async function depositarSimulado(
  token: string,
  importe: string,
  clave: string,
): Promise<Comprobante> {
  return pedir<Comprobante>(
    "/api/v1/cuentas/mia/depositos-simulados",
    { importe },
    { token, idempotencia: clave },
  );
}

export async function transferir(
  token: string,
  cuentaDestino: string,
  importe: string,
  concepto: string,
  clave: string,
): Promise<Comprobante> {
  return pedir<Comprobante>(
    "/api/v1/transferencias",
    { cuentaDestino, importe, concepto },
    { token, idempotencia: clave },
  );
}
