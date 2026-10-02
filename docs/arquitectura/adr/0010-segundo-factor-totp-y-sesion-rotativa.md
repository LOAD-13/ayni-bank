# ADR-0010 · Segundo factor TOTP y sesión con tokens rotativos

- **Estado:** aceptado
- **Fecha:** 30 de agosto de 2026
- **Historias afectadas:** HU-04 (inicio de sesión), HU-06 (recuperar contraseña), HU-07 (panel)
- **Reemplaza a:** ninguno

## Contexto

HU-04 exige un segundo factor, un token de acceso de 15 minutos, un *refresh token* rotativo de
7 días en cookie `HttpOnly` y la invalidación de la familia entera cuando se detecta la
reutilización de un token. El documento de diseño ya fijaba esas cifras; lo que no fijaba es
**qué segundo factor**, ni **quién lo da de alta**, ni **por dónde viaja cada token**.

Además, los criterios de aceptación tienen un hueco: el escenario 1 dice «dado que el cliente
tiene su MFA configurado», pero **no existe ninguna historia que configure ese MFA**. Sin
resolverlo, el escenario es inalcanzable y HU-04 no se puede dar por terminada.

## Decisión

### El segundo factor es TOTP, y se da de alta en el primer ingreso

**TOTP** (RFC 6238) frente a las alternativas:

| Método | Coste | Riesgo | Dependencias |
|---|---|---|---|
| **TOTP** | cero | el móvil perdido | ninguna |
| OTP por correo | cero | el correo comprometido | `notification-service` (HU-13) |
| OTP por SMS | por mensaje | *SIM swapping* | pasarela externa |
| WebAuthn / passkey | cero | ninguno relevante | HTTPS obligatorio |

TOTP no envía nada: el servidor y el móvil calculan el mismo número de seis dígitos a partir de un
secreto compartido y la hora. Funciona sin red, no cuesta dinero y no depende de que
`notification-service` exista, cosa que hoy no ocurre.

**El alta ocurre en el primer ingreso, no en el registro.** Generar el secreto en HU-01 dejaría un
segundo factor activo en la cuenta de todo el que se registró y nunca volvió. Y ocurre *después* de
validar la contraseña, nunca antes: el URI `otpauth://` lleva el secreto dentro, y entregarlo a
quien solo ha escrito un correo permitiría llevarse el segundo factor de una cuenta ajena.

Mientras no esté confirmado, el secreto se reutiliza en cada intento en lugar de regenerarse. Quien
escanea el QR y cierra la pantalla antes de teclear el código se quedaría, si no, con una aplicación
que guarda un secreto que ya no vale.

**El algoritmo se implementa a mano sobre `javax.crypto`**, sin biblioteca. Son cuarenta líneas —un
HMAC-SHA1, un truncado dinámico y un módulo— y la prueba las contrasta contra el vector oficial del
apéndice B de la RFC. Traer una dependencia añadiría código de terceros al árbol y a los informes de
Trivy a cambio de ahorrar algo que además conviene tener a la vista, porque decide quién entra.

SHA-1 y no SHA-256 porque es lo que fija la RFC por defecto y lo único que aceptan sin configurar
Google Authenticator, Authy y el resto. Sus debilidades conocidas son de colisión, y aquí se usa
dentro de un HMAC con clave secreta, donde no aplican.

### El ingreso son dos peticiones, no una

Las pantallas aprobadas son dos —«Entra a tu banca» y «Confirma que eres tú»—, así que el ingreso se
parte igual:

1. `POST /api/v1/sesion` valida correo y contraseña y devuelve un **desafío** de dos minutos.
2. `POST /api/v1/sesion/segundo-factor` canja el desafío por la sesión.

El desafío existe para que la segunda pantalla no tenga que reenviar la contraseña, lo que
significaría guardarla en el navegador entre pantalla y pantalla.

### El reparto de los tokens

- **Token de acceso** (JWT HS256, 15 min) → en el cuerpo de la respuesta. El cliente lo necesita
  para la cabecera `Authorization`.
- **Token de renovación** (aleatorio de 256 bits, 7 días) → **solo en cookie `HttpOnly`**.

El reparto es deliberado: lo de vida larga donde un XSS no llega, lo de vida corta donde el script lo
necesita. Si el de renovación viajara en el cuerpo, cualquier XSS se llevaría siete días de sesión.

De la base solo se guarda la **huella SHA-256** del token. Basta SHA-256 y no hace falta Argon2id: es
un valor aleatorio de 256 bits, no una palabra que un diccionario pueda adivinar.

**La caducidad se hereda al rotar, no se reinicia.** Si cada renovación empezara a contar de nuevo,
una sesión renovada cada seis días no caducaría jamás y los siete días del criterio serían
decorativos.

### La antienumeración se extiende al ingreso

Correo desconocido y contraseña incorrecta devuelven **el mismo cuerpo y tardan lo mismo**: cuando el
correo no existe se deriva la contraseña igualmente. Sin esa derivación la respuesta llegaría en
milisegundos frente a las decenas que cuesta Argon2id, y quien cronometre distingue las dos
situaciones sin leer el mensaje. Es ADR-0008 aplicado al login.

Por el mismo motivo, el DTO **no valida el formato de la contraseña**: rechazar una de ocho
caracteres con un 400 la distinguiría de una bien formada pero incorrecta, que devuelve 401.

Y el estado `BLOQUEADO` se comprueba **después** de la contraseña. Antes, probar correos revelaría
cuáles corresponden a cuentas bloqueadas sin acertar ninguna.

### Quién puede entrar y quién no

- `PENDIENTE_VERIFICACION` y `EN_REVISION` **sí entran.** Quien se registró y cerró el navegador
  tiene que poder volver para terminar su alta, y quien está en revisión manual necesita ver en qué
  quedó. Lo que no pueden es **operar**, y de eso se encarga `puedeOperar()` en el dominio.
- `BLOQUEADO` no entra.

El estado viaja dentro del JWT para que el panel sepa qué mostrar sin preguntar en cada carga. No es
un permiso: quien decide si se puede operar es el servicio, no el token.

### El bloqueo es progresivo y con techo

Cinco intentos tolerados; a partir del sexto, cinco minutos que se duplican con cada fallo hasta un
**máximo de una hora**. El techo no es una comodidad: sin él, unos cuantos fallos más dejan la cuenta
inaccesible durante semanas, y eso deja de proteger al titular para convertirse en una denegación de
servicio contra él —basta que alguien falle a propósito—.

El contador **no se limpia al acertar la contraseña**, solo al completar el ingreso entero. Si se
limpiara antes, quien conoce la contraseña y no el segundo factor podría probar códigos
indefinidamente sin bloquearse nunca. Un código erróneo cuenta igual que una contraseña errónea.

El aviso al titular se manda **una sola vez**, en el fallo que provoca el bloqueo. Mandarlo en cada
intento posterior convertiría la protección en el ataque.

## Alternativas evaluadas

**OTP por correo como método principal.** Más familiar para el usuario y sin instalar nada, pero
depende de `notification-service`, que no existe hasta HU-13, y de que el correo llegue. Queda como
**alternativa** a añadir, no como predeterminado.

**SMS.** Descartado salvo petición expresa: cuesta dinero por mensaje y es el factor más débil de los
cuatro por el *SIM swapping*.

**WebAuthn / passkeys.** Es el más seguro y sustituye al login entero, no solo al segundo factor. No
funciona sobre HTTP, así que no se puede desarrollar en local sin certificados. El botón «Entrar con
la biometría del dispositivo» del prototipo apunta a esto y **hoy no tiene historia en el backlog**;
ver `docs/gestion/pendientes-sprint-2.md`.

**Un único endpoint que reciba credenciales y código a la vez.** Menos superficie, pero obliga a la
segunda pantalla a conservar la contraseña o a fundir las dos pantallas aprobadas en una.

## Consecuencias

**A favor**

- Segundo factor sin coste recurrente y sin dependencias externas.
- Un XSS no puede robar la sesión larga.
- El robo de una cookie se detecta y se corta solo, sin intervención de nadie.

**En contra**

- Quien pierde el móvil pierde el acceso hasta que exista la recuperación (HU-06). Es el riesgo
  conocido de TOTP y el motivo de que la alternativa por correo importe.
- Hay que gestionar una segunda clave, `AYNI_JWT_CLAVE`, distinta de la de cifrado. Firmar y cifrar
  son finalidades distintas, y una clave para las dos obliga a rotarlas juntas aunque solo se
  comprometa una.
- En local, sobre HTTP, la cookie no puede llevar el prefijo `__Host-`, que exige `Secure`. El
  nombre cambia según el entorno (`ayni-renovacion` / `__Host-ayni-renovacion`); si no, el navegador
  descarta la cookie **sin decir nada** y la renovación falla sin motivo aparente.

## Pendiente

- Alternativa de segundo factor por correo, cuando HU-13 traiga `notification-service`.
- Recuperación del segundo factor cuando se pierde el dispositivo (HU-06).
- Decidir si WebAuthn entra en el backlog o se retira del prototipo.
- Validación del JWT en el gateway (HU-07). Hasta entonces, los endpoints de consulta toman el
  titular de la ruta, cosa que solo es aceptable porque los servicios no están publicados fuera de
  la red interna del compose.
