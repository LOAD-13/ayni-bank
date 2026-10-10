# ADR-0030 · Recuperación de la contraseña sin oráculos

**Estado:** Aceptada
**Fecha:** 10 de octubre de 2026
**Historia:** AYNI-123 (HU-21)

---

## Contexto

HU-21 pide que quien olvidó su contraseña pueda recuperarla desde su correo, con una respuesta
idéntica exista o no la cuenta (la regla de ADR-0008), un enlace de un solo uso válido treinta
minutos y el cierre de todas las sesiones al cambiarla. También exige que el segundo factor siga
siendo obligatorio después.

Implementarla de forma ingenua abre tres puertas:

1. **El cronómetro.** Los correos de identity se envían hoy de forma síncrona con SES. Si el
   enlace se enviara dentro de la petición, la respuesta tardaría unos cientos de milisegundos
   más solo cuando la cuenta existe: el cuerpo idéntico no protegería nada.
2. **El enlace en los registros.** Un token en la *query string* queda escrito en los registros
   de acceso de Caddy y del gateway, y viaja en la cabecera `Referer`.
3. **El bombardeo.** Sin freno, cualquiera que conozca un correo puede llenarle la bandeja.

## Decisión

1. **Respuesta idéntica en cuerpo y en tiempo.** `POST /api/v1/recuperacion` responde siempre
   202 con el mismo mensaje, y el controlador la retiene hasta una **duración mínima**
   (`ayni.recuperacion.duracion-minima`, 400 ms por defecto). La auditoría registra la petición
   en los dos casos, con el usuario nulo cuando la cuenta no existe.
2. **Correo fuera del hilo de la petición.** En producción, el enlace y el aviso de cambio se
   envían desde un pool propio y acotado del notificador (no un bean `Executor`, que desactivaría
   el que Spring Boot configura para el resto de la aplicación). Cuando HU-13 lleve los correos a
   `notification-service` por el outbox, esta asincronía la dará la propia cola.
3. **Token en el fragmento.** El enlace es `/recuperar/nueva#t=<token>`. El navegador nunca envía
   el fragmento al servidor; la web lo lee y lo manda en el **cuerpo** de las peticiones.
4. **Token como el de renovación.** 256 bits aleatorios; solo se guarda su huella SHA-256. Treinta
   minutos de vigencia, un solo uso (UPDATE condicional, a prueba de dos clics simultáneos) y
   **solo vale el último**: emitir uno nuevo anula los anteriores.
5. **Freno de tres enlaces por cuenta y hora.** Los de más se ignoran en silencio, con la misma
   respuesta.
6. **Al cambiar la contraseña** se invalidan todas las familias de refresh token del titular, se
   limpia el contador de intentos fallidos, se registra `CONTRASENA_RESTABLECIDA` y se le avisa por
   correo. La cuenta bloqueada por el banco no recibe enlaces: un correo no levanta un bloqueo.
7. **El ingreso no cambia.** Recuperar la contraseña no toca el segundo factor ni el estado del
   usuario, así que el login lo sigue pidiendo.

## Riesgo residual aceptado

El **token de acceso** ya emitido sigue siendo válido hasta **quince minutos** después del cambio.
El gateway valida el JWT sin estado (ADR-0027); revocarlo exigiría consultar una lista de
revocación en cada petición. Se acepta porque las sesiones de siete días sí caen al instante, y
quien tenga ese token no puede renovarlo. Si el riesgo deja de ser aceptable, la salida es una
fecha de «credenciales cambiadas» en el usuario que el gateway compare con el `iat` del token.

## Consecuencias

- Ningún canal —cuerpo, código, tiempo ni registros— distingue una cuenta existente de una que no
  lo es.
- La duración mínima añade 400 ms a cada petición de recuperación. Es una operación rara y el
  coste es invisible para el cliente.
- Fuera de producción el notificador solo anota en el log, sin el enlace: para probar en local se
  fija la huella de un token conocido directamente en la base.
