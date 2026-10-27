# ADR-0035 · Límite de tasa en el gateway con Redis

**Estado:** Aceptada
**Fecha:** 26 de octubre de 2026
**Historia:** AYNI-160 (propuesta del taller de seguridad, semana 8)

---

## Contexto

El gateway no limitaba cuántas peticiones acepta. Nada impedía probar miles de contraseñas contra
`POST /api/v1/sesion`, adivinar un código de segundo factor de seis dígitos a fuerza de intentos,
crear registros en masa o pedir URLs de subida firmadas sin fin. El taller de seguridad de la
semana 8 propuso el `RequestRateLimiter` de Spring Cloud Gateway con Redis y fijó los límites.

Además, `/api/v1/segundo-factor/**` no tenía ruta: el desafío por correo de identity devolvía 404 a
través del gateway.

## Decisión

**1. `RequestRateLimiter` con Redis en el gateway**, con una ruta propia para cada cosa limitada,
delante de las generales (`order: -1`). El algoritmo es el cubo de fichas del limitador de serie.
Cada ruta cuenta aparte.

| Ruta | Se cuenta por | Límite | Cubo (reposición/s · capacidad · fichas por petición) |
|---|---|---|---|
| `POST /api/v1/sesion` | IP | 5 / min | 1 · 60 · 12 |
| `POST /api/v1/registro` | IP | 3 / min | 1 · 60 · 20 |
| `/api/v1/sesion/segundo-factor` y `/api/v1/segundo-factor/**` (cupo compartido) | IP | 10 / min | 1 · 60 · 6 |
| `/api/v1/sesion/renovacion` | IP | 20 / min | 1 · 60 · 3 |
| `/api/v1/solicitudes/*/documentos/url-de-subida` | IP | 10 / h | 1 · 3600 · 360 |
| `POST /api/v1/transferencias` y `POST /api/v1/cuentas/mia/depositos-simulados` | titular | 10 / min | 1 · 60 · 6 |

El limitador repone por segundo. Un límite por minuto se expresa haciendo que cada petición cueste
varias fichas: con 60 de capacidad y 12 por petición caben 5 peticiones, y el cubo tarda 12 s en
reponer una.

**2. 429 con `Retry-After`.** El limitador de serie responde 429 sin decir cuánto esperar.
`LimitadorConRetryAfter` lo extiende y calcula `⌈(fichas por petición − fichas que quedan) /
reposición⌉` a partir de las cabeceras `X-RateLimit-*` que ya devuelve. La regla de cada ruta vive
en un único sitio (`application.yml`). CORS expone `Retry-After` para que la web pueda mostrar la
espera.

**3. Por IP antes de la sesión, por titular cuando hay dinero de por medio.** Para entrar,
registrarse o verificar un código no hay identidad que contar: se cuenta la IP, y es el último valor
de `X-Forwarded-For` (el que escribe Caddy; los anteriores los puede inventar el cliente). Las
transferencias y los depósitos se cuentan por el titular que fija el filtro de autenticación
(`X-Ayni-Usuario`), que el cliente no puede falsificar. Así, varios clientes detrás de una misma IP
(una oficina, una red móvil con NAT) no comparten cupo.

**4. Redis y no la memoria del proceso.** Con dos réplicas del gateway, cada una contaría por su
lado y el límite real sería el doble. Redis corre en un contenedor propio sin disco
(`--save ""`), sin puerto publicado y con 64 MB: los contadores caducan en minutos y perderlos al
reiniciar no hace daño.

**5. Si Redis cae, se deja pasar (*fail-open*).** Es el comportamiento del limitador de serie y se
mantiene a propósito: perder el límite unos minutos es preferible a dejar a todos los clientes sin
banca. Por la misma razón, Redis no cuenta para la salud del gateway
(`management.health.redis.enabled: false`) y el gateway depende de él con `service_started` y no
con `service_healthy`.

## Consecuencias

- Fuerza bruta contra contraseña y segundo factor acotada: 5 contraseñas y 10 códigos por minuto y
  por IP. Un código de seis dígitos pasa de adivinarse en minutos a necesitar meses desde una IP.
- La ruta del desafío por correo existe y queda limitada desde el primer día.
- Un contenedor más en producción (64 MB de los 8 GB de la EC2).
- Un ataque repartido entre muchas IP no se frena con esto. Para eso hace falta un WAF delante
  (AWS WAF o el de Cloudflare). Queda fuera del alcance de esta decisión.
- En local, sin Caddy delante, un cliente puede inventarse `X-Forwarded-For` y saltarse el límite
  por IP. En producción no: Caddy reescribe la cabecera con la IP real.

## Verificación

`LimiteDeTasaTest` levanta el gateway real con un Redis de Testcontainers y un servicio falso que
siempre responde 200: cada límite de la tabla da 429 en la petición siguiente a la última permitida,
con `Retry-After`, y otra IP u otro titular conservan su cupo. `LimitadorConRetryAfterTest`
comprueba el cálculo y que con Redis caído se deja pasar. `ConfiguracionDeLimitesTest`, la clave
con la que se cuenta.
