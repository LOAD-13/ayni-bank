# ADR-0034 · Coexistencia: límites de recursos, apagado ordenado y timeouts

**Estado:** Aceptada
**Fecha:** 22 de octubre de 2026
**Historia:** AYNI-317 (talleres de interoperabilidad y coexistencia, ISO/IEC 25010), subtareas
AYNI-333 a AYNI-336

---

## Contexto

Producción corre en una sola EC2 de 8 GB (ADR-0026) con once contenedores. Ninguno tenía techo de
memoria ni de CPU. Una fuga en un servicio, o kyc cargando TensorFlow y PaddleOCR a la vez, podía
agotar la memoria de la máquina, y el OOM killer del kernel elegía a quién matar: podía ser
PostgreSQL en local o el servicio que mueve el dinero. Es el «vecino ruidoso» de la coexistencia
(ISO/IEC 25010).

Al apagar un contenedor, Docker envía SIGTERM y, a los 10 s, SIGKILL. Spring Boot 3.5 ya espera a
las peticiones en curso, pero hasta 30 s por defecto: Docker lo mataba antes, a mitad de una
transferencia o de una verificación de DNI.

Por último, los clientes de Amazon SES no tenían timeout: una SES que no responde dejaba el hilo
esperando sin fin. Y el gateway no limitaba cuánto espera a los servicios.

## Decisión

**1. Techo de memoria y CPU para cada contenedor** (`deploy.resources.limits`, aplicado con
cgroups), en el compose local y en el de producción. El que pasa su techo muere solo, y Docker lo
reinicia (`restart: unless-stopped`); los demás no se enteran. Los servicios Java ya ajustan su heap
al techo del contenedor (`-XX:MaxRAMPercentage=70`).

| Contenedor | Memoria | CPU (prod) |
|---|---|---|
| gateway | 640 MB | 0.5 |
| identity · core-banking | 768 MB | 0.75 |
| notification | 640 MB | 0.5 |
| kyc (TensorFlow + PaddleOCR) | 2560 MB | 1.5 |
| web | 384 MB | 0.5 |
| RabbitMQ | 384 MB | 0.5 |
| Prometheus · Grafana · Caddy · node-exporter | 512 · 384 · 128 · 64 MB | 0.1–0.25 |

La suma en producción es de unos 7 GB y deja margen al sistema operativo. Los valores se validan
midiendo con `docker stats` bajo carga; si un servicio vive pegado a su techo, se sube el techo.

**2. Apagado ordenado.** `server.shutdown: graceful` y 20 s por fase de apagado en los servicios
Spring; `--timeout-graceful-shutdown 20` en uvicorn (kyc); y `stop_grace_period: 30s` en Docker,
para que el apagado del servicio termine siempre antes que la paciencia de Docker. Con SIGTERM el
servicio deja de aceptar peticiones, termina las que tiene en curso y cierra el pool de conexiones y
los consumidores de RabbitMQ.

**3. Timeouts en todas las llamadas salientes** (diseño base §3.4):

- SES: 3 s para conectar, 5 s por intento y 12 s en total con los reintentos del SDK.
- Gateway hacia los servicios: 3 s para conectar y 30 s de respuesta, que cubren los 10 s de la
  verificación del DNI más sus reintentos de conexión.
- identity hacia kyc: sin cambios, ya lo fija el ADR-0020.

**4. Respuesta 400 uniforme en core-banking.** Las peticiones mal formadas responden
`application/problem+json` con `codigo: SOLICITUD_INVALIDA` y el campo que falla, como identity.
Antes salía el 400 genérico de Spring, distinto según el error. Ningún mensaje repite el valor
recibido ni el texto interno del parser.

## Consecuencias

- Un pico real de kyc que pase de 2.5 GB reinicia kyc en lugar de degradar la máquina. Se vigila
  en Grafana con la memoria por contenedor.
- Apagar el entorno tarda unos segundos más: es el tiempo de terminar lo que estaba en curso.
- Los techos no reservan memoria: son máximos. La máquina no necesita tener libre la suma.
