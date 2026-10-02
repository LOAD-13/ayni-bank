# ADR-0026 · Verificación síncrona del DNI por lado y confirmación del titular

- **Estado:** aceptado
- **Fecha:** 30 de septiembre de 2026
- **Historias afectadas:** HU-02 (AYNI-13)
- **Reemplaza en parte a:** [ADR-0020](0020-resiliencia-de-la-llamada-a-kyc-service.md) (qué se reintenta),
  [ADR-0021](0021-limite-de-intentos-y-derivacion-a-revision-manual.md) (contador único) y
  [ADR-0024](0024-feature-cucumber-de-verificacion-de-identidad.md) (escenarios pendientes)

## Contexto

Una revisión de HU-02 encontró que las piezas existían, cada una con sus pruebas, pero **nada las
conectaba**:

- kyc-service no exponía ningún endpoint de DNI: el contrato describía `POST /kyc/verify`
  (asíncrono, 202 y consulta posterior), pero en Python solo existía `/kyc/verify-match`, de HU-03.
- En identity nadie llamaba a `VerificadorKycPort` ni a `GestionarFalloDeVerificacionKycUseCase`.
- Nadie escribía en `documento_kyc`.
- El navegador subía la foto a MinIO y pasaba al paso siguiente sin verificar nada.

Además había cinco defectos concretos: las heurísticas del anverso devolvían el rótulo
(«PRIMER», «PRE») como valor; el MRZ aceptaba un número con letras si su checksum cuadraba; un
cuarto fallo violaba el `CHECK (0..3)`; los PDF se aceptaban aunque OpenCV no puede leerlos; y la
espera ante un kyc-service lento era de ~31 s, no de 10.

## Decisión

### 1. Dos operaciones síncronas en kyc-service

`diseno-base.md` §3.5 punto 5 dice «se empieza síncrono con timeout de 10 s». El contrato pasa a
`2.0.0` con:

- `POST /kyc/documentos/evaluacion` (`documentKey`, `lado`): si es un DNI y tiene calidad. Un
  rechazo responde 200 con `aceptada=false` y **un** `motivoRechazo` (`NO_ES_DNI`, `ENCUADRE`,
  `DESENFOQUE`, `REFLEJO`, `ILUMINACION`), el que conviene corregir primero. Si no se encuentra
  ningún contorno, antes de decir «no es un DNI» se mide la foto entera: una toma borrosa u
  oscura impide encontrar el documento y lo que hay que repetir es la toma.
- `POST /kyc/documentos/extraccion` (`anversoDocumentKey`, `reversoDocumentKey`): MRZ primero,
  heurísticas del anverso si no valida (ADR-0015). La **fecha de emisión** se lee siempre del
  anverso, porque el MRZ no la trae. «No se pudo leer» es `legible=false`, no un error.

Las claves deben tener la forma exacta que firma identity (`kyc/{solicitud}/{lado}-{uuid}.{ext}`).
Cada foto se descarga y se endereza una sola vez por petición.

### 2. Orquestación en identity

- **Subida:** `url-de-subida` devuelve un formulario **POST** pre-firmado, no una URL PUT. La
  política fija la clave, el tipo de contenido y `content-length-range` de 1 B a 5 MB: MinIO
  rechaza lo que no cumpla, sin depender del navegador. Se devuelve también `claveDeObjeto`.
- **Evaluación** (`POST /solicitudes/{id}/documentos`): comprueba que la clave pertenezca a esa
  solicitud y a ese lado, calcula el SHA-256 **antes** de evaluar y, si se acepta, registra
  `documento_kyc` (referencia, hash, MIME, tamaño). Si se rechaza, borra el objeto (escenario 2:
  «no almacena la imagen») y cuenta un intento de ese lado. Si kyc-service no responde, conserva
  la foto para el operador y deriva sin gastar intentos (escenario 5).
- **Lectura** (`POST /solicitudes/{id}/documentos/extraccion`): antes de leer, recalcula el
  SHA-256 de las dos fotos. La política permite reescribir la misma clave durante 5 minutos; si
  el hash cambió, la foto no es la que se evaluó y la solicitud va a revisión manual. Una lectura
  ilegible cuenta como intento del **reverso**, que es la cara del MRZ y la que hay que repetir.
- **Confirmación** (`POST /solicitudes/{id}/identidad/confirmacion`): el titular corrige lo que
  haga falta. Se contrasta con lo declarado (ADR-0009), sin distinguir mayúsculas, tildes ni
  espacios. Va a revisión manual si no coincide, **o** si el titular cambia el número o la fecha
  de nacimiento de una lectura del MRZ con checksums válidos: esos campos no se leen mal por un
  reflejo, así que cambiarlos no es corregir al OCR, es contradecir al documento.

Todos los resultados de negocio responden 200 con un `estado` (`ACEPTADO`, `RECHAZADO`,
`EN_REVISION_MANUAL`, `VERIFICACION_DIFERIDA`): son pasos del flujo, no errores.

### 3. Intentos por lado

El criterio de aceptación dice «máximo 3 intentos de captura **por lado**». V8 sustituye
`intentos_verificacion_kyc` por `intentos_kyc_anverso` e `intentos_kyc_reverso`. El tercer fallo de
un lado deriva (como en ADR-0021); una cuarta captura sobre una solicitud ya derivada se responde
como derivada, sin evaluarse, sin sumar y sin repetir el aviso. `solicitud_onboarding` gana
`version` (bloqueo optimista), para que dos fallos simultáneos no se pisen.

### 4. Lo leído y lo confirmado, en filas distintas

La tabla `lectura_dni` guarda cada lectura con su `fuente` (`MRZ`, `HEURISTICA_ANVERSO`, `TITULAR`)
y el número cifrado con AES-256-GCM. Ante una reclamación siempre se puede responder «¿esto lo
leyó una máquina o lo escribió la persona?». La respuesta al navegador lleva el número
**enmascarado**: el endpoint aún no exige sesión (T-12), y el titular solo lo reescribe si está mal.

### 5. Qué se reintenta

Resilience4j combina `retry-exceptions` y `retry-exception-predicate` con un **O**: con la lista
declarada, el predicado no podía excluir nada. Ahora decide solo `ReintentoAnteKycNoDisponible`:
5xx y errores de E/S, **salvo timeouts de lectura**. El timeout de conexión baja a 2 s. Un
kyc-service caído se reintenta (falla al instante); uno lento se abandona a los 10 s.

### 6. Sin PDF y sin ruta pública a kyc-service

Se retira el PDF de la carga desde archivo (ADR-0023): OpenCV no lo lee y se rechazaría siempre
como «no es un DNI». Se retira del gateway la ruta `/api/v1/kyc/**`: kyc-service es interno.

### 7. Lo que encontró la prueba de punta a punta

Levantar el compose y recorrer el flujo en un navegador sacó cuatro defectos que ninguna prueba
unitaria podía ver, porque el OCR nunca se había cargado de verdad dentro del contenedor:

- **Faltaba `libgomp1` en la imagen** de kyc-service. PaddlePaddle no se podía importar.
- **TensorFlow y PaddlePaddle no conviven si TensorFlow se carga primero**: segfault (código 139)
  sin traza. TensorFlow entra con DeepFace (HU-03) al importar `router_kyc`; `main.py` importa
  `paddle` antes. Además, si la precarga del OCR falla, el servicio arranca igual: evaluar fotos
  y cotejar rostros no dependen del OCR.
- **`encontrar_lineas_mrz` tomaba rótulos por líneas de MRZ.** «Departamento LIMA Provincia LIMA»
  sin espacios son 29 mayúsculas; desplazaba la ventana y un MRZ bien leído caía a heurísticas.
  Ahora una línea de MRZ debe llevar `<`, y se prefiere la ventana que valida sus checksums.
- **La lectura tarda ~9-11 s** en un portátil (PP-OCRv6 en CPU, dos caras). Con el timeout de 10 s
  se cortaba. La evaluación mantiene los 10 s del diseño; la extracción tiene su propio cliente con
  `ayni.kyc.timeout-de-extraccion` (30 s por defecto, `AYNI_KYC_TIMEOUT_EXTRACCION`).

## Consecuencias

- Los cinco escenarios de Jira, más uno de corrección manual, se ejecutan en
  `verificacion-de-identidad-dni.feature` contra los casos de uso reales.
- Primera extracción y Raspberry Pi: PaddleOCR se precarga al arrancar (`KYC_PRECARGAR_OCR`,
  `start_period: 120s`). **Pendiente medir** la extracción en la Raspberry Pi 5 y ajustar
  `AYNI_KYC_TIMEOUT_EXTRACCION`; si el tiempo es inaceptable para el solicitante, evaluar los
  modelos *mobile* de PaddleOCR.
- **Pendiente T-12** (AYNI-122): los endpoints de `/api/v1/solicitudes/**` siguen sin exigir sesión.
  Lo único que lo mitiga hoy es que el identificador de solicitud es un UUID aleatorio.
- Los umbrales de calidad (incluido el nuevo de iluminación, brillo medio entre 40 y 235) siguen
  siendo heurísticos, sin conjunto de control (ADR-0013).
