# Análisis de ejecución · AYNI-23 · HU-12 Cuenta en dólares y tipo de cambio real

Fecha del análisis: 23-nov-2026 · Sprint 7 activo (16-nov a 29-nov) · 13 pts.

## 1. Hallazgo principal: trabajar sobre `develop`, no sobre `main`

La copia local está en `main` y **no tiene nada de HU-12**. `origin/develop` (e56f142) ya trae, en
`core-banking`, 62 archivos de diferencia. Hay que partir de `develop` (GitFlow, ver `CONTRIBUTING.md`):

```
git switch develop && git pull origin develop
git switch -c feature/AYNI-2xx-<descripcion>
```

Cada rama sale de `develop`, nunca de otra feature.

## 2. Estado real de cada subtarea (código en `origin/develop` frente a Jira)

| Ticket | Subtarea | Jira | Código en `develop` | Qué falta |
|---|---|---|---|---|
| AYNI-264 | Extender `Money` (3h→5h) | Por hacer | `Dinero` ya lanza `MonedasIncompatiblesException` al sumar o restar monedas distintas, y `Movimiento`/`Conversion` validan moneda | Probablemente solo cerrar con pruebas que lo demuestren (ver §4.1) |
| AYNI-265 | Migración `tipo_cambio` y ledger multimoneda | Por hacer | `tipo_cambio` existe desde V1; V13 añade posiciones cambiarias USD/PEN, tabla `conversion` y el evento de auditoría | Solo verificar y cerrar. Si 272 necesita columnas nuevas, va en una migración nueva (V16) |
| AYNI-266 | Adaptador SUNAT/BCRP | Listo | `AdaptadorBcrp` (series PD04639PD/PD04640PD) + puerto `FuenteDeTipoDeCambioPort` | Nada |
| AYNI-267 | Caché diaria | Por hacer | No existe. El `application.yml` dice "va aparte (AYNI-267 y 268)" | **Todo** |
| AYNI-268 | Circuit breaker + último valor conocido | Por hacer | No existe para el BCRP. Resilience4j ya está en el pom y hay un ejemplo con `@CircuitBreaker(name = "camara")` | **Todo** |
| AYNI-269 | Aviso de tipo en caché | Listo | `AvisoDeTipoDeCambio.tsx` (solo componente, aún sin pantalla que lo use) | Nada |
| AYNI-270 | Spread compra/venta | Por hacer | `segmento_cambiario` sembrada en V1 (0.50 % estándar, 0.25 % preferencial). `TipoDeCambioAplicado` llega "ya con spread" | **Todo**: calcular el tipo aplicado |
| AYNI-271 | Conversión en tres asientos | Listo | `Conversion`, `ConvertirMonedaService`, `ConvertirMonedaUseCase`, repositorio y V13 | Nada. **Pero no hay controller** (ver 274) |
| AYNI-272 | Registro inmutable del tipo y timestamp | Por hacer | `conversion` guarda `tipo_aplicado`, `tipo_obtenido_en`, `tipo_en_cache` | Faltan referencia, spread y segmento, y **la inmutabilidad** (la tabla permite UPDATE/DELETE) |
| AYNI-273 | Apertura de cuenta en dólares | Por hacer | `AbrirCuentaDeAhorroService` ya elige `AHORRO_USD` si la moneda es USD (`ayni.productos.ahorro-dolares`) | **Nadie la dispara**: `EscuchadorDeSolicitudAprobada` abre solo PEN. Falta el endpoint/camino |
| AYNI-274 | Pantalla de conversión con simulación | Por hacer | Solo `AvisoDeTipoDeCambio` | Pantalla, funciones de `api.ts`, navegación, y el **endpoint** que la alimenta |
| AYNI-275 | Feature de Cucumber (3 escenarios) | Por hacer | Hay `ConversionEnPostgresTest`, pero ninguna `.feature` de conversión | Feature + pasos |

Las horas pendientes según Jira son 5+3+3+3+3+3+3+8+8 = **39 h**. Las subtareas 274 y 275 no aparecían
en mi lista de tickets tuyos, así que probablemente están asignadas a otra persona: confirmarlo antes
de empezar.

## 3. Cómo encaja en la arquitectura existente

Hexagonal verificada con ArchUnit (`ArquitecturaHexagonalTest`): el dominio no importa infraestructura.

- Puerto de salida de la fuente oficial: `FuenteDeTipoDeCambioPort` (ya existe).
- Casos de uso: `ConvertirMonedaUseCase` (existe, recibe el tipo **ya calculado**). Falta el caso de uso
  que lo calcula: la cotización.
- Persistencia: `tipo_cambio` tiene tabla desde V1 pero **ningún `@Entity` ni repositorio** en el código.
- Ledger: un asiento nunca mezcla monedas; la conversión son dos movimientos (salida y entrada), cada uno
  con su posición cambiaria del banco (`ca4b0000-…-0001` PEN y `…-0002` USD).
- Gateway: la ruta `core-banking` ya enruta `/api/v1/tipo-cambio/**`, pero **no** `/api/v1/conversiones`,
  y la ruta con límite de tasa `limite-dinero` tampoco.
- Auditoría: `CONVERSION_REALIZADA` ya está permitido por el CHECK de V13, y la pista es inalterable (V14).

## 4. Plan de ejecución propuesto

### 4.1 Rama A · Verificar lo que parece hecho (264 y 265, ~2 h reales)

1. Revisar que existan pruebas explícitas de `Dinero` sobre monedas distintas (`DineroTest`) y de
   `Conversion` sobre "nunca mezclar monedas en un asiento" (`ConversionTest`). Si falta alguna, añadirla.
2. Comprobar que `ConversionEnPostgresTest` ejercita V13 completo contra PostgreSQL.
3. Mover 264 y 265 a Listo con el enlace al PR o commit. **Decisión que debe tomar el equipo**: si el
   trabajo de estas dos ya está cubierto por 266/271, cerrarlas sin PR propio o con un PR mínimo de
   pruebas.

### 4.2 Rama B · Cotización: caché, circuito y spread (267, 268, 270, ~9 h)

Es el núcleo y desbloquea todo lo demás. Un solo caso de uso `CotizarConversionUseCase` con este flujo:

1. Buscar en la tabla `tipo_cambio` la fila de hoy con fuente `BCRP`. Si existe, es la caché diaria.
2. Si no existe, pedir `FuenteDeTipoDeCambioPort.ultimoPublicado(hoy)` y **guardar** la fila
   (`ux_tipo_cambio_dia` impide duplicados; usar `ON CONFLICT DO NOTHING` por concurrencia).
3. Si la fuente falla (`FuenteDeTipoDeCambioNoDisponibleException` o circuito abierto), usar la **última
   fila guardada** y marcar `enCache = true`. Si no hay ninguna, responder 503 con mensaje claro.
4. Aplicar el spread del segmento del cliente y devolver `TipoDeCambioAplicado`.

Decisiones de diseño:

- **La caché es la tabla `tipo_cambio`, no memoria.** Ya existe, sobrevive a reinicios (el "último valor
  conocido" no se pierde) y no añade dependencias. El pom no tiene Caffeine ni `spring-boot-starter-cache`,
  y la regla es no añadir dependencias por unas pocas líneas.
- **Circuit breaker con Resilience4j sobre `AdaptadorBcrp`**, instancia `bcrp` en `application.yml` copiando
  los números de `camara`. La excepción del circuito abierto (`CallNotPermittedException`) debe convertirse
  en `FuenteDeTipoDeCambioNoDisponibleException` para que el dominio no conozca Resilience4j.
- **Spread** (diseño §3.7.1): `venta_cliente = venta_ref × (1 + spread/2)`, `compra_cliente = compra_ref ×
  (1 − spread/2)`. Cliente PEN→USD usa la venta; USD→PEN usa la compra. `TipoDeCambioAplicado.convertir`
  ya divide para PEN→USD y multiplica para USD→PEN, así que encaja.
- **Segmento preferencial**: saldo promedio mensual ≥ S/ 5 000 (`segmento_cambiario.saldo_promedio_minimo`).
  Hay que decidir cómo se calcula ese promedio. Opción barata: promediar `devengo_interes.saldo_base` del mes
  de la cuenta en soles (ya existe la tabla, V5). Es una decisión de negocio a confirmar.
- **Escala del tipo a 6 decimales** (`setScale(6, HALF_EVEN)`) antes de aplicarlo. `conversion.tipo_aplicado` es
  `NUMERIC(19,6)`; si el tipo usado tiene más decimales que el guardado, el escenario 3 (recalcular el importe
  desde lo registrado) puede dar un céntimo de diferencia. Es el riesgo técnico más fácil de pasar por alto.
- **Feriados y fines de semana**: `AdaptadorBcrp` ya busca hacia atrás 10 días, así que "hoy" puede devolver
  un valor de días anteriores. La fila guardada lleva la fecha real de publicación y `obtenido_en`.

Pruebas: unitarias del cotizador sin Spring (caché que acierta, fuente caída con respaldo, fuente caída sin
respaldo, spread estándar y preferencial, redondeo), e integración con Testcontainers para la tabla.

### 4.3 Rama C · Registro inmutable (272, ~3 h)

Migración nueva **V16** (V15 es la última en `develop`; si otra rama toma V16 antes, renumerar al rebasar):

- Añadir a `conversion`: `tipo_referencia`, `spread`, `segmento`, `fuente`. El diseño exige guardar "tipo de
  referencia, spread aplicado, tipo final y timestamp".
- Hacerla de solo inserción con el mismo patrón de V14: `REVOKE UPDATE, DELETE, TRUNCATE` y trigger
  `BEFORE UPDATE OR DELETE` que lance `insufficient_privilege`.
- Ampliar `TipoDeCambioAplicado` (o añadir un `Cotizacion`) con esos datos y pasarlos por `ConversionEntity` y
  `AdaptadorRegistroDeConversiones`.
- Prueba de integración que intente `UPDATE` y `DELETE` y espere el error, como `PistaDeAuditoriaInalterableTest`.

Depende de 4.2 porque necesita los datos de la cotización.

### 4.4 Rama D · Cuenta en dólares y endpoints (273 y la parte de API de 274, ~6 h)

- **Endpoint para abrir la cuenta en dólares**, iniciado por el cliente: `POST /api/v1/cuentas/mia/dolares`
  con `Idempotency-Key`, que reutiliza `AbrirCuentaDeAhorroUseCase.abrirPara(usuarioId, clave, Moneda.USD)`.
  El índice `ux_cuenta_titular_moneda` permite una activa por titular **y moneda**, así que PEN y USD conviven.
  Alternativa descartada por defecto: abrirla automáticamente en el onboarding (HU-12 dice "abrir", y obligarla
  llena la base de cuentas que nadie usa). **Confirmar con el equipo.**
- **Endpoints de conversión**: `GET /api/v1/tipo-cambio/cotizacion?origen=PEN&importe=370.00` (simulación, no
  mueve dinero) y `POST /api/v1/conversiones` con `Idempotency-Key`.
- **Coherencia cotización/confirmación** (escenario 2): al confirmar, el servidor vuelve a cotizar. Si el tipo
  cambió, o ahora viene de caché y el cliente no lo había visto, responder 409 con la cotización nueva. Así el
  cliente nunca confirma un valor que no vio.
- **Gateway**: añadir `/api/v1/conversiones` a las rutas `core-banking` y `limite-dinero`.
- **Quitar el `Moneda.PEN` fijo** de `OperacionesController` (`cuentaDe`, `miCuenta`, `misMovimientos`) y de
  `OperarCuentaService.comprobante`: hoy el comprobante de la pata en dólares devuelve vacío (404) porque solo
  busca la cuenta en soles. Aceptar un parámetro `moneda` con valor por defecto PEN para no romper a nadie.
- **Contrato OpenAPI**: el DoD lo exige si cambia una API. El core genera su OpenAPI desde el código (springdoc,
  apagado en producción); revisar `DocumentacionDeLaApiTest` y `ConfiguracionDeLaDocumentacion`.

### 4.5 Rama E · Pantalla de conversión (274, 8 h) y Cucumber (275, 8 h)

Si son de otra persona, solo coordinar el contrato del endpoint (§4.4) para que puedan trabajar en paralelo.

- **Web**: ruta `/banca/convertir`, entrada en `MarcoDeBanca.tsx`, funciones `cotizarConversion` y `convertir` en
  `lib/api.ts`, simulación previa con tipo aplicado, tipo de referencia y margen (el diseño exige mostrar los
  tres antes de confirmar), y `AvisoDeTipoDeCambio` ya hecho enlazado con `aria-describedby` al botón de
  confirmar. Pruebas con Vitest como `tarjeta.test.tsx` y axe-core sin violaciones (WCAG 2.1 AA).
- **Cucumber**: `conversion-de-moneda.feature` en `src/test/resources/features` y `PasosDeConversion` sobre
  `ContextoDeAceptacion`, con un doble de `FuenteDeTipoDeCambioPort` (como `CamaraDePrueba`) para forzar la caída
  del BCRP en el escenario 2. El escenario 3 se puede verificar leyendo la conversión por
  `RegistroDeConversionesPort.buscarPorSalida` y recalculando con `TipoDeCambioAplicado.convertir`.

## 5. Orden y dependencias

```
A (264, 265)  ── independiente, rápida
B (267, 268, 270) ──► C (272) ──► D (273 + API) ──► E (274 web, 275 Cucumber)
```

B es el camino crítico. D y E pueden avanzar en paralelo en cuanto B fije la forma de la cotización. Un PR por
rama, mensajes de commit en formato Conventional Commits en español (`feat(core): …`, `test(core): …`).

## 6. Riesgos y puntos abiertos

1. **Pérdida de un céntimo en la auditoría** si el tipo aplicado no se normaliza a 6 decimales (ver 4.2).
2. **Cierre diario sobre cuentas en dólares**: `CierreDiarioService` llama a `libro.cuentaDeIntereses(cuenta.moneda())`,
   y `AdaptadorLibroMayor.cuentaDeIntereses` lanza `IllegalStateException` para cualquier moneda que no sea PEN. No
   he verificado si el cierre selecciona cuentas en USD; si lo hace, una cuenta en dólares con saldo rompería el
   devengo diario. **Revisar antes de abrir la primera cuenta en dólares en staging.** Puede que haya que excluir
   USD o sembrar una cuenta de intereses en dólares (el catálogo ya tiene TEA para AHORRO_USD).
3. **Depósito simulado en dólares**: `cuentaDeFondeo(USD)` lanza excepción. Si el cliente solo obtiene dólares
   convirtiendo, no importa; si se quiere depositar en USD hace falta fondeo en dólares. Decidir.
4. **Segunda confirmación (segundo factor)**: las transferencias la exigen (ADR-0031). HU-12 no la menciona y la
   conversión es entre cuentas del mismo titular, pero conviene preguntarlo.
5. **Numeración de migraciones**: otras subtareas pendientes (roles AYNI-281, back-office AYNI-284) también
   crearán migraciones; coordinar quién toma V16.
6. **ADR**: el DoD pide uno si hay decisión arquitectónica. La caché en `tipo_cambio` y la inmutabilidad de
   `conversion` lo justifican (siguiente número libre al escribirlo: 0039, que luego ocupó otro ADR; quedó como 0040).
7. **Escenario 3 con rol de auditor**: la consulta por un "auditor" depende de roles y back-office (AYNI-281 y
   AYNI-284, pendientes). En esta historia se cubre a nivel de datos y de prueba, no con pantalla de auditor.

## 7. Qué no se revisó

- No hice checkout de `develop`: todo se leyó con `git show` y `git diff` contra `origin/develop`.
- No ejecuté la suite de pruebas ni compilé nada.
- No leí `ConversionEnPostgresTest`, `ConversionTest` ni `ConvertirMonedaServiceTest`: los puntos de §4.1 sobre
  su cobertura son una hipótesis a confirmar.
- No verifiqué quién tiene asignadas AYNI-274 y AYNI-275.
- La definición completa de `Dinero` y del resto del dominio viene de `main`; en `develop` esos archivos no
  cambian salvo `Movimiento` y `Comprobante`.
