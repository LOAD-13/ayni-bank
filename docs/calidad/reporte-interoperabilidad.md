# Reporte de interoperabilidad y pruebas de integración · ISO/IEC 25010

> Capítulo 13 del informe del APF3 · T-20 (AYNI-220).
> Compatibilidad = **interoperabilidad** (intercambiar información y usarla) + **coexistencia**
> (compartir recursos sin perjudicar a otros). La coexistencia se midió en la semana 11 (ADR-0034).
> Aquí se valida, con pruebas automatizadas, cada frontera entre Ayni Bank y los sistemas con los que habla.

## 1. Sistemas y servicios externos a integrar

| # | Sistema | Tipo | Protocolo y formato | Quién lo usa | Para qué |
|---|---|---|---|---|---|
| E1 | **Cámara de compensación interbancaria** | API REST de terceros (simulada en el entorno académico) | HTTP/JSON, contrato `contracts/camara-compensacion.openapi.yaml` (`POST /transferencias`, `GET /transferencias/{id}`) | core-banking | Enviar transferencias a otros bancos por CCI y conocer su resultado (HU-11) |
| E2 | **AWS SES** | Servicio de correo en la nube | API de AWS SDK v2 (HTTPS) | identity, notification | Códigos de verificación y avisos de cada operación (HU-13, HU-22) |
| E3 | **Amazon S3** (MinIO en local) | Almacenamiento de objetos | Protocolo S3: formulario POST firmado (SigV4) y API de objetos | navegador, identity, kyc | Fotos del DNI y la selfie del KYC (HU-02, HU-03) |
| E4 | **Amazon RDS PostgreSQL** (PostgreSQL 16 en local) | Base de datos gestionada | JDBC / SQL, migraciones Flyway | identity, core-banking, notification | Persistencia con un schema por servicio |
| E5 | **RabbitMQ** | Broker de mensajería | AMQP 0-9-1, eventos JSON del outbox | identity, core-banking, notification | Eventos de dominio entre servicios (apertura de cuenta, movimientos, rechazos) |
| E6 | **Microservicios de Ayni entre sí** | APIs REST internas | HTTP/JSON con contratos OpenAPI; JWT validado en el gateway | gateway, identity, kyc, core-banking | Enrutado, autenticación y verificación de identidad |
| E7 | **Redis** | Almacén clave-valor | RESP | gateway | Límite de tasa por IP y por titular (ADR-0035) |

Lo que hace interoperable cada frontera:
- **Contratos OpenAPI** para la cámara, identity, kyc y los controles de tarjeta, publicados en Swagger UI solo en local
  (ADR-0033).
- **Errores uniformes** `application/problem+json`.
- **Eventos idempotentes**, cuyo `messageId` es el id del outbox.
- **Timeouts explícitos**: SES con 3 s de conexión y 12 s en total; el gateway con 3 s de conexión y 30 s de
  respuesta.
- La cámara se llama con **reintentos y circuit breaker**: 3 intentos con 250 ms de espera, y el circuito se abre
  15 s si falla la mitad de las últimas 10 llamadas. Lo que no se pudo confirmar lo recoge una **conciliación** que
  revierte el cargo al vencer el plazo.

## 2. Frameworks y herramientas

| Herramienta | Uso en la suite |
|---|---|
| **JUnit 5** + AssertJ | Base de todas las pruebas Java |
| **Testcontainers** | PostgreSQL, Redis y **MinIO reales** en contenedores durante la prueba |
| **MockRestServiceServer** (Spring) | Frontera HTTP de la cámara: comprueba la petición que sale y responde según el contrato |
| **Cucumber** (Gherkin) | Escenarios de aceptación que cruzan servicios con PostgreSQL real; el envío a RabbitMQ se verifica en su frontera (`RabbitTemplate`) |
| **WebTestClient** (Spring WebFlux) | Gateway real contra servicios simulados y Redis real |
| **pytest** | Adaptadores de kyc (almacenamiento y visión) |
| **Playwright** (`request`) | Suite de API contra la pila desplegada, en local y en la nube (capítulo 11) |

## 3. Casos de prueba de interoperabilidad

| ID | Sistema | Qué valida | Prueba automatizada |
|---|---|---|---|
| INT-01 | E1 Cámara | El simulador decide por la entidad del CCI: 097 rechaza, 098 no responde, el resto acepta | `CamaraDeCompensacionTest.decidePorEntidad` |
| INT-02 | E1 Cámara | Reenviar o consultar una instrucción devuelve la misma decisión (idempotencia) | `CamaraDeCompensacionTest.recuerdaLaDecision` |
| INT-03 | E1 Cámara | Una instrucción inválida responde 400 o 404, como lo haría la cámara | `CamaraDeCompensacionTest.validaLaInstruccion` |
| INT-04 | E1 Cámara | El adaptador envía la instrucción según el contrato y lee el estado | `CamaraDeCompensacionTest.adaptadorEnviaYConsulta` |
| INT-05 | E1 Cámara | Una respuesta sin estado no se toma por buena: el resultado queda desconocido | `CamaraDeCompensacionTest.respuestaSinEstado` |
| INT-06 | E1 + E4 + E5 | 7 escenarios de HU-11 de punta a punta, con PostgreSQL real: envío, rechazo, reversión por plazo, segundo factor y auditoría | `transferencia-interbancaria.feature` |
| INT-07 | E2 SES | Se envía desde el remitente, en UTF-8, con el HTML escapado y una versión de texto | `EnviadorDeCorreoSesTest` |
| INT-08 | E2 SES | El cliente SES tiene timeouts de conexión, por intento y totales | `ConfiguracionDeCorreoTest` |
| INT-09 | E3 S3 | **El navegador sube con el formulario firmado a un MinIO real y el servicio lee el mismo contenido (SHA-256)** | `AlmacenDeDocumentosEnMinioTest.subidaFirmadaYLectura` |
| INT-10 | E3 S3 | **La política firmada hace que el almacenamiento rechace con 403 un tipo distinto** | `AlmacenDeDocumentosEnMinioTest.politicaRechazaOtroTipo` |
| INT-11 | E3 S3 | Un documento eliminado deja de existir para el servicio | `AlmacenDeDocumentosEnMinioTest.eliminar` |
| INT-12 | E3 S3 | La política exige clave, tipo exacto y como máximo 5 MB, y se firma con el cliente público | `MinioAlmacenDeDocumentosTest` |
| INT-13 | E5 RabbitMQ | El evento sale con el JSON del outbox, su id como `messageId`, de forma persistente; un fallo del broker no detiene los demás | `PublicadorDeOutboxTest` |
| INT-14 | E5 RabbitMQ | identity publica `identity.titular.activado` sin dejar el correo en una traza | `RepublicadorDeTitularesTest` |
| INT-15 | E4 + E5 | El mismo evento entregado a la vez por 8 hilos produce un solo correo; los reintentos quedan registrados | `NotificacionesEnPostgresTest` |
| INT-16 | E5 identity → core | 5 escenarios: la aprobación del KYC abre la cuenta en core-banking por el evento | `apertura-automatica-de-cuenta.feature` |
| INT-17 | E4 | Transferencias concurrentes sobre PostgreSQL real no dejan saldo negativo ni asientos duplicados | `ConcurrenciaDeTransferenciasTest` |
| INT-18 | E4 | La tarjeta guarda el PAN cifrado y su huella única en PostgreSQL real | `TarjetaEnPostgresTest` |
| INT-19 | E6 identity → kyc | El cliente generado desde el contrato de kyc llama y lee la respuesta | `ClienteKycGeneradoTest` |
| INT-20 | E6 gateway | El JWT se valida en el gateway; la cabecera de usuario del cliente se descarta | `FiltroDeAutenticacionTest` |
| INT-21 | E6 gateway | Toda consulta de cuentas sin sesión responde 401, con las rutas reales del gateway | `RutasProtegidasDeLaCuentaTest` |
| INT-22 | E6 + E7 | Límite de tasa con Redis real: 5 inicios de sesión por minuto por IP, 429 con `Retry-After` | `LimiteDeTasaTest` |
| INT-23 | E6 | Un servicio caído se traduce en 503 con `Retry-After`, no en 500 | `ManejadorDeServicioNoDisponibleTest` |

## 4. Ejecución

La suite se ejecuta en cada PR y en cada merge a `develop` dentro del job «Java · calidad y pruebas» de la CI.
También se ejecuta en local:

```bash
./mvnw verify -pl services/ayni-gateway,services/ayni-identity-service,services/ayni-core-banking-service,services/ayni-notification-service -am
```

Los reportes de surefire, uno por clase, son la evidencia de ejecución. Incluyen los 30 escenarios Cucumber de core-banking, que corren con PostgreSQL real y verifican en su frontera lo que se envía a RabbitMQ. Se reúnen y resumen con
`resumir_integracion.py`, que no escribe ningún número a mano.

## 5. Reporte de resultados

Ver la sección 6 y el anexo F del informe, que es el `resumen.md` generado a partir de los XML de surefire.

## 6. Métricas, nivel de cumplimiento y observaciones

| Métrica | Fórmula | Resultado |
|---|---|---|
| Cumplimiento de la suite | casos que pasan / casos ejecutados | **100 %** (117 de 117, `mvn clean verify` local del 30-oct-2026 sobre la 2.0.0) |
| Sistemas externos cubiertos | sistemas con al menos una prueba de su frontera / sistemas identificados | **7 de 7** |
| Fronteras con el protocolo real | sistemas probados contra una instancia real (Testcontainers) | 3 de 7: PostgreSQL, Redis y S3/MinIO. RabbitMQ, SES y la cámara se prueban en su frontera |
| Tiempo de la suite | suma de los tiempos medidos | 21,3 s |

**Observaciones**

1. **SES, la cámara y RabbitMQ se prueban en su frontera, no contra el servicio real.** En RabbitMQ
   se verifica lo que sale hacia el broker (`RabbitTemplate`): mensaje, `messageId` y persistencia; el
   intercambio real entre servicios se ejerce en el compose local y en producción. SES necesitaría credenciales de AWS en
   la CI, y la cámara no existe fuera del entorno académico. Se valida que la petición que sale cumple el contrato y
   que cada respuesta posible, buena, mala o ausente, se trata bien. La prueba contra SES real se hace en la nube
   (capítulo 11), con el correo que llega al abrir una cuenta.
2. **La cámara vive dentro de core-banking.** Se comunica por HTTP y su URL es configurable (`AYNI_CAMARA_URL`), así
   que apuntar a una cámara real no cambia el código. Mejora pendiente: desplegar el simulador como un contenedor
   propio.
3. Hasta el 29-oct todas las pruebas del almacenamiento usaban un cliente simulado. Se añadió la prueba contra un
   MinIO real (INT-09 a INT-11), que valida la firma S3 de extremo a extremo.
