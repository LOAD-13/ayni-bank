# ADR-0020 · Resiliencia de la llamada a kyc-service (Retry + CircuitBreaker)

- **Estado:** aceptado
- **Fecha:** 12 de septiembre de 2026
- **Historias afectadas:** HU-02 (verificación de identidad)
- **Reemplaza a:** ninguno

## Contexto

La subtarea 10 de AYNI-13 pide envolver la llamada a `kyc-service` con Resilience4j, según lo
ya fijado en `diseno-base.md` §3.4-3.5: *"La llamada va envuelta en Resilience4j; ante fallo,
el onboarding responde 'verificación en revisión manual' en lugar de romperse"*, con *"timeout,
reintento con backoff exponencial y circuit breaker"*, timeout de 10s.

El cliente generado (`VerificationApi`, subtarea 2) tiene 3 métodos síncronos y bloqueantes
(`RestClient`). Esta subtarea envuelve solo `startVerification` — `getVerificationStatus`/
`getVerificationResult` no tienen ningún llamador todavía (ningún caso de uso los invoca), así
que envolverlos ahora sería resiliencia sin uso real; se extienden con el mismo patrón cuando
llegue el polling.

**Qué decide esta subtarea vs. la 11**: cuando la resiliencia se agota (reintentos
consumidos o circuito abierto), el adaptador lanza `KycServiceNoDisponibleException`. Qué
hacer con ella — derivar la solicitud a revisión manual, actualizar
`solicitud_onboarding.estado` — es explícitamente el alcance de la subtarea 11 ("límite de
tres intentos y derivación a revisión manual"), ya anotado como pendiente en el ADR-0019.

## Decisión

**Timeout, no `@TimeLimiter`.** `@TimeLimiter` de Resilience4j exige que el método decorado
devuelva `CompletableFuture` (o un tipo reactivo); `VerificationApi` es síncrono y bloqueante.
Envolverlo en `@Async` solo para poder usar `@TimeLimiter` añadiría un pool de hilos a
gestionar sin necesidad. El timeout de 10s se fija directamente en el
`SimpleClientHttpRequestFactory` que usa el `RestClient` subyacente
(`ConfiguracionDelClienteKyc`), que ya resuelve esto de forma nativa.

**`@CircuitBreaker` + `@Retry`, con el orden fijado explícitamente.** `AdaptadorVerificadorKyc`
(`infrastructure/out/client/kyc/`) implementa `VerificadorKycPort` (dominio) envolviendo
`VerificationApi` con ambas anotaciones. `fallbackMethod` va solo en `@CircuitBreaker`.

## Hallazgo técnico importante: el orden de los aspectos no es el que documenta la intuición

Se necesita que `CircuitBreaker` decore a `Retry` desde afuera: si los 3 reintentos internos
fallan, `CircuitBreaker` debe registrar **una** llamada fallida, no tres — de lo contrario el
circuito se abriría con una fracción del tráfico real de fallos.

**Confirmado experimentalmente que el orden por defecto de `resilience4j-spring-boot3` 2.4.0 es
el contrario**: sin fijarlo, el fallback de `CircuitBreaker` absorbía la excepción en el
*primer* intento, y `Retry` nunca llegaba a reintentar (verificado con un test aislado: con
ambas anotaciones sin orden explícito, la llamada al servicio fake ocurría una sola vez, no
tres). Se corrige fijando explícitamente en `application.yml`:

```yaml
resilience4j:
  circuitbreaker:
    circuit-breaker-aspect-order: 1   # numero menor = mas afuera (Spring AOP @Order)
  retry:
    retry-aspect-order: 2
```

**Segundo hallazgo: el `fallbackMethod` se dispara para *cualquier* excepción propagada**, sin
importar `record-exceptions`/`ignore-exceptions` en el YAML — esas propiedades solo afectan las
*métricas* del circuito (si la llamada cuenta como fallo para la tasa de apertura), no si el
fallback se invoca. Confirmado también experimentalmente: con `ignore-exceptions` apuntando a
`HttpClientErrorException`, un error 4xx simulado igual terminaba envuelto en
`KycServiceNoDisponibleException` por el fallback.

**Corrección**: `alAgotarLaResiliencia` (el fallback method) inspecciona la causa. Si es un
`HttpClientErrorException` (4xx — una petición mal formada, no un problema de disponibilidad
del servicio), la relanza tal cual. Cualquier otra causa (5xx, timeout, error de conexión, o
`CallNotPermittedException` con el circuito abierto) se traduce a `KycServiceNoDisponibleException`.
`ignore-exceptions` en el YAML se mantiene igual, porque sí cumple su propósito real: que un
400 no cuente como fallo en las métricas del circuito, aunque no evite el fallback por sí solo.

## Valores fijados (sin especificación exacta en el diseño, criterio de industria)

```yaml
resilience4j:
  retry:
    instances.kycService:
      max-attempts: 3
      wait-duration: 500ms
      enable-exponential-backoff: true
      exponential-backoff-multiplier: 2
      retry-exceptions: [HttpServerErrorException, ResourceAccessException]
  circuitbreaker:
    instances.kycService:
      sliding-window-size: 10
      minimum-number-of-calls: 5
      failure-rate-threshold: 50
      wait-duration-in-open-state: 30s
      permitted-number-of-calls-in-half-open-state: 3
```

500ms con backoff x2 y 3 intentos acota el peor caso (cada intento tope 10s por el timeout del
`RestClient`, más 500ms+1000ms de espera entre intentos). Ventana de 10 llamadas con mínimo 5
evita abrir el circuito por ruido estadístico en tráfico bajo. 30s en estado abierto es
razonable para un servicio interno (no expuesto a Internet) que puede estar reiniciando, sin
necesidad de ventanas largas tipo servicio de terceros.

## Validación

Sin `@SpringBootTest` (sin datasource, Flyway, RabbitMQ): `ApplicationContextRunner` cargando
solo `AopAutoConfiguration` + `CircuitBreakerAutoConfiguration` + `RetryAutoConfiguration`, con
un `VerificationApi` fake (subclase que sobrescribe `startVerification`) registrado como bean —
confirma que las anotaciones están realmente conectadas al bean real vía proxy AOP, no solo que
la configuración de Resilience4j en sí es correcta. Cuatro casos, todos verificados
experimentalmente antes de darlos por buenos (no solo revisión de código):
- 3 intentos exactos ante un 5xx simulado, termina en `KycServiceNoDisponibleException`.
- Un 4xx no dispara reintento (1 sola llamada) y se relanza tal cual.
- El circuito se abre tras agotar el umbral de fallos: llamadas posteriores no llegan al fake.
- Una respuesta exitosa no pasa por el fallback.

## Alternativas evaluadas

**`@TimeLimiter` con el método envuelto en `@Async`.** Descartado: añade un `Executor` a
gestionar (tamaño de pool, forma de apagarlo) para resolver algo que el timeout nativo del
`RestClient` ya cubre sin overhead adicional.

**Dejar el orden de aspectos en su valor por defecto.** Descartado tras confirmar
experimentalmente que produce el comportamiento incorrecto (CircuitBreaker absorbe el fallo
antes de que Retry actúe) — no es una opción válida, es un bug si no se corrige.

## Consecuencias

**A favor**

- Comportamiento verificado experimentalmente, no solo diseñado sobre el papel: dos supuestos
  del diseño inicial (orden de aspectos, alcance de `ignore-exceptions`) resultaron incorrectos
  y se corrigieron antes de quedar en el código.
- `getVerificationStatus`/`getVerificationResult` quedan con el mismo patrón listo para
  extenderse cuando el polling tenga un llamador real.

**En contra**

- `KycServiceNoDisponibleException` no tiene todavía ningún consumidor — nada la atrapa para
  decidir "revisión manual" (eso es la subtarea 11). Es deuda explícita, igual que el
  repositorio de `documento_kyc` (ADR-0019).
- Los valores de retry/circuit breaker son heurísticos sin medición contra tráfico real (mismo
  patrón de riesgo ya señalado en ADR-0012/0013 para los umbrales de visión).

## Pendiente

- ~~Subtarea 11: atrapar `KycServiceNoDisponibleException` en el caso de uso de integración y
  derivar la solicitud a `EN_REVISION_MANUAL`.~~ Resuelto por
  [ADR-0021](0021-limite-de-intentos-y-derivacion-a-revision-manual.md):
  `GestionarFalloDeVerificacionKycUseCase.derivarPorServicioNoDisponible` deriva de inmediato,
  sin consumir el límite de tres intentos que ADR-0021 define para los fallos que sí dependen
  del usuario. Sigue pendiente que el caso de uso de integración (todavía no construido) sea
  quien la invoque en la práctica.
- Medir los valores de retry/circuit breaker contra tráfico real cuando exista, y ajustar si
  hace falta.
