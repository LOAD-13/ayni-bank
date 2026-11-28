# Plan de rama única · AYNI-23 primero, luego el resto de pendientes

Fecha: 23-nov-2026 · Complementa el análisis de [analisis-ayni-23.md](analisis-ayni-23.md), que explica el
porqué de cada decisión de AYNI-23. Este documento fija el **orden, los pasos y las comprobaciones**.

## 1. Alcance y rama

Todo se hace en **una sola rama** que sale de `develop`:

```
git switch develop && git pull origin develop
git switch -c feature/AYNI-23-cuenta-en-dolares-y-pendientes
```

Pendientes asignados a Eduardo en Jira, más las dos subtareas de AYNI-23 que no son suyas pero sin las
cuales la historia no cierra:

| Fase | Ticket | Qué | Horas Jira | Sprint |
|---|---|---|---|---|
| 1 | AYNI-264, 265 | Verificar y cerrar (ya cubiertos en `develop`) | 5 + 3 | 7 |
| 1 | AYNI-267, 268, 270 | Cotización: caché, circuit breaker, spread | 3 + 3 + 3 | 7 |
| 1 | AYNI-272 | Registro inmutable | 3 | 7 |
| 1 | AYNI-273 | Cuenta en dólares | 3 | 7 |
| 1 | AYNI-274, 275 | Pantalla y Cucumber (**confirmar quién las tiene**) | 8 + 8 | 7 |
| 2 | AYNI-281 | Cinco roles con permisos | 12 | 7 |
| 3 | AYNI-284 | Back-office de auditoría de solo lectura | 6 | 7 |
| 4 | AYNI-289 | Navegación completa por teclado | 8 | 7 |
| 5 | AYNI-304 | Pruebas de mutación con PIT | 8 | **8** (empieza el 30-nov) |

Total: 41 h de lo asignado a Eduardo + 16 h de 274/275 = **57 h**. El Sprint 7 termina el 29-nov, o sea
6 días. Las fases 1 a 4 son 49 h y caben solo si 274/275 las toma otra persona; PIT (fase 5) cae en el
Sprint 8.

**Costes de usar una sola rama** (la decisión es del equipo, solo los dejo anotados):

- `CONTRIBUTING.md` pide una rama por historia. Aquí se mezclan HU-12, HU-15, HU-17 y HU-14.
- Un solo PR de ~50 h es difícil de revisar y el DoD exige una aprobación. Para aliviarlo: un commit por
  subtarea con `Refs: AYNI-NNN`, y empujar la rama al terminar cada fase para que el pipeline corra.
- HU-15, HU-17 y HU-14 tienen más subtareas que las mías: cerrar mis subtareas no cierra esas historias.

## 2. Fase 0 · Preparación (30 min)

1. Crear la rama (arriba) y comprobar que `develop` compila y pasa antes de tocar nada:
   `./mvnw -pl services/ayni-core-banking-service -am verify` y, en `web/ayni-web`, `npm test`.
2. Confirmar en Jira quién tiene 274 y 275; si son de otra persona, acordar el contrato de §3.7 antes de
   que empiece.
3. Reservar los números de migración: core **V16** (AYNI-23). Identity no necesita migración nueva para
   los roles (ver Fase 2).

## 3. Fase 1 · AYNI-23 (lo primero)

Orden interno: 3.1 → 3.2 → 3.3 → 3.4 → 3.5 → 3.6 → 3.7 → 3.8. Cada paso termina con las pruebas de su
parte en verde y un commit.

### 3.1 Verificar 264 y 265
- Leer `DineroTest`, `ConversionTest` y `ConversionEnPostgresTest`. Añadir solo lo que falte: sumar PEN+USD
  lanza `MonedasIncompatiblesException`; ningún asiento de una conversión mezcla monedas; V13 se aplica sobre
  PostgreSQL real.
- Commit: `test(core): cubrir que Dinero y Conversion nunca mezclan monedas` · `Refs: AYNI-264, AYNI-265`.

### 3.2 Persistencia de `tipo_cambio` (base de 267)
- Dominio: puerto de salida `RepositorioDeTipoDeCambioPort` con `delDia(LocalDate)`, `ultimoConocido()` y
  `guardar(TipoDeCambioOficial)`.
- Infraestructura: `TipoCambioEntity` + repositorio JPA en `infrastructure/out/persistence`. El guardado usa
  `INSERT … ON CONFLICT (fecha, moneda_origen, moneda_destino, fuente) DO NOTHING`.
- "Último conocido" = la fila más reciente por `fecha`, de cualquier fuente (en desarrollo existe la
  `SEED_DEV`, que sirve de respaldo).
- Prueba de integración con Testcontainers.

### 3.3 Cotización con caché y circuit breaker (267, 268)
- Caso de uso nuevo `CotizarConversionUseCase` y servicio `CotizarConversionService`:
  1. fila de hoy en `tipo_cambio` → se usa (caché diaria, `enCache = false`);
  2. si no, `FuenteDeTipoDeCambioPort.ultimoPublicado(hoy)` y se guarda;
  3. si la fuente falla → último conocido con `enCache = true`; si no hay ninguno →
     `FuenteDeTipoDeCambioNoDisponibleException` (503).
- `AdaptadorBcrp`: añadir `@CircuitBreaker(name = "bcrp")` y convertir `CallNotPermittedException` en
  `FuenteDeTipoDeCambioNoDisponibleException`, para que el dominio no dependa de Resilience4j. Instancia
  `bcrp` en `application.yml` con la misma forma que `camara`.
- Pruebas unitarias sin Spring: caché acierta, fuente caída con respaldo, fuente caída sin respaldo, circuito
  abierto. Con Spring: el breaker se abre tras N fallos.

### 3.4 Spread (270)
- Servicio de dominio `PoliticaDeSpread`: `venta_cliente = venta_ref × (1 + spread/2)` para PEN→USD y
  `compra_cliente = compra_ref × (1 − spread/2)` para USD→PEN.
- Resultado normalizado a **6 decimales HALF_EVEN** (la columna es `NUMERIC(19,6)`), para que el escenario 3 pueda
  recalcular el importe exacto.
- Segmento: leer `segmento_cambiario` (V1). Preferencial si el saldo promedio mensual de la cuenta en soles
  ≥ 5 000, calculado como promedio de `devengo_interes.saldo_base` del mes. Es una **decisión abierta**: si el
  equipo prefiere otra definición, solo cambia el puerto `SegmentoDelClientePort`.
- Pruebas: estándar y preferencial dan resultados distintos para el mismo importe; redondeo en empates.

### 3.5 Registro inmutable (272) · migración V16
- `V16__conversion_inmutable_y_con_referencia.sql`: columnas `tipo_referencia`, `spread`, `segmento`, `fuente`
  en `conversion`; `REVOKE UPDATE, DELETE, TRUNCATE`; trigger `BEFORE UPDATE OR DELETE` y `BEFORE TRUNCATE`
  copiando el patrón de V14. Las filas anteriores se completan en la misma migración antes de cerrarla.
- Ampliar `TipoDeCambioAplicado` con referencia, spread y segmento, y pasarlos por `ConversionEntity` y
  `AdaptadorRegistroDeConversiones`. Ajustar `ConversionTest` y `ConvertirMonedaServiceTest`.
- Prueba de integración: `UPDATE` y `DELETE` sobre `conversion` fallan con el código 42501, como
  `PistaDeAuditoriaInalterableTest`.

### 3.6 Cuenta en dólares (273)
- `POST /api/v1/cuentas/mia/dolares` con `Idempotency-Key`, que llama a
  `AbrirCuentaDeAhorroUseCase.abrirPara(usuarioId, clave, Moneda.USD)`.
- Sacar el `Moneda.PEN` fijo de `OperacionesController` (`cuentaDe`, `miCuenta`, `misMovimientos`) y de
  `OperarCuentaService.comprobante`, con parámetro `moneda` y valor por defecto PEN.
- **Antes de dar por buena esta pieza**: revisar `CierreDiarioService` con una cuenta en USD. Llama a
  `libro.cuentaDeIntereses(cuenta.moneda())`, que lanza `IllegalStateException` si no es PEN. O se excluye USD
  del devengo o se siembra una cuenta de intereses en dólares; decidirlo y probarlo.
- Pruebas: abre una vez, la segunda devuelve `CuentaDuplicadaException`; conviven PEN y USD.

### 3.7 API de cotización y conversión
- `GET /api/v1/tipo-cambio/cotizacion?origen=PEN&importe=370.00` (no mueve dinero) y
  `POST /api/v1/conversiones` con `Idempotency-Key` y el `tipoMostrado` que vio el cliente.
- Al confirmar, el servidor vuelve a cotizar. Si el tipo cambió, o ahora viene de caché y el cliente no lo
  sabía, responde 409 con la cotización nueva.
- Gateway: añadir `/api/v1/conversiones` a las rutas `core-banking` y `limite-dinero`.
- Documentación OpenAPI y `DocumentacionDeLaApiTest` actualizados.

### 3.8 Pantalla (274) y Cucumber (275)
- Web: `/banca/convertir`, entrada en `MarcoDeBanca.tsx`, `cotizarConversion` y `convertir` en `lib/api.ts`.
  Muestra tipo aplicado, tipo de referencia y margen antes de confirmar, con `AvisoDeTipoDeCambio` enlazado por
  `aria-describedby` al botón. Pruebas Vitest y una ruta nueva en `accesibilidad-banca.a11y.spec.ts`.
- Cucumber: `conversion-de-moneda.feature` con los tres escenarios y `PasosDeConversion`, con un doble de
  `FuenteDeTipoDeCambioPort` para el escenario 2. El escenario 3 recalcula con `TipoDeCambioAplicado.convertir`
  a partir de lo guardado.

### 3.9 Cierre de la fase
- ADR `0040-tipo-de-cambio-con-cache-spread-y-conversion-inalterable.md` (el 0039 ya era la migración a Spring Boot 4).
- Actualizar `product-backlog.md` y `sprint-6.md` si cambia el estado.
- Comprobación final: suite completa del core, `npm test`, y Playwright del flujo de conversión.

## 4. Fase 2 · AYNI-281 · Cinco roles con permisos (12 h)

Estado real en `develop`: las tablas `rol`, `permiso` y `rol_permiso` y los cinco roles ya se siembran en
identity V1, pero **nada los usa**: no hay `usuario_rol` poblado, el JWT solo lleva `estado`, y el gateway solo
reenvía `X-Ayni-Usuario`. Hay que cablear tres cosas:

1. **Identidad**: asignar `CLIENTE` al registrarse; cargar roles y permisos al emitir el token y ponerlos en un
   claim `permisos` de `EmisorDeTokensJwt`. Comprobar primero si `usuario_rol` existe en V2 (no lo confirmé).
2. **Gateway**: tabla ruta → permiso requerido y 403 si el token no lo trae. Reenviar el permiso a los
   servicios solo como información; la decisión se toma en el gateway y se vuelve a comprobar donde haya
   operaciones sensibles. `FiltroDeAutenticacion` ya limpia `X-Ayni-Usuario` entrante, hacer lo mismo con la
   cabecera nueva para que no se pueda falsificar.
3. **Pruebas**: matriz de 5 roles × operaciones (CLIENTE no lee cuentas ajenas; OPERADOR no aprueba;
   SUPERVISOR no inicia y aprueba la misma; AUDITOR no escribe; OFICIAL no mueve dinero).
- Los usuarios de personal para desarrollo van en un seed `dev`, nunca en una migración versionada.
- Riesgo: cambiar el JWT afecta a gateway, identity y a cualquier prueba que arme tokens a mano
  (`EmisorDeTokensJwtTest`, `FiltroDeAutenticacionTest`). Mantener compatibilidad con tokens sin el claim nuevo
  durante la transición.

## 5. Fase 3 · AYNI-284 · Back-office de auditoría de solo lectura (6 h)

Depende de la fase 2. Hoy no hay endpoint que lea `evento_auditoria` (solo hay un repositorio JPA interno) ni
área de back-office en la web.

- Core: `GET /api/v1/auditoria/eventos` paginado, con filtros por usuario, tipo y rango de fechas; permiso
  `auditoria:leer`. Solo `GET`: la tabla ya es de solo inserción (V14).
- Gateway: ruta protegida con ese permiso.
- Web: `/backoffice/auditoria`, visible solo con el permiso, tabla paginada y sin ningún botón que escriba.
  Incluirla en las pruebas de accesibilidad.
- Pruebas: AUDITOR ve, CLIENTE recibe 403, y ningún método que no sea `GET` está expuesto.

## 6. Fase 4 · AYNI-289 · Navegación completa por teclado (8 h)

Ya existen `e2e/foco.a11y.spec.ts`, `accesibilidad-banca.a11y.spec.ts` y `aceptacion/accesibilidad.feature`
(transferencia solo con teclado). Falta ampliar a los **flujos críticos restantes**: conversión (nueva),
depósito, transferencia interbancaria, tarjeta, apertura de la cuenta en dólares y el back-office.

- Recorrer cada flujo solo con `Tab`, `Shift+Tab`, `Enter`, `Espacio` y `Esc` en Playwright; fallar si algún
  control no es alcanzable o si el foco no es visible.
- Arreglar lo que salga: orden de tabulación, trampas de foco en modales, controles que son `div` clicables.
- Va después de la fase 1 y la 3 a propósito, para cubrir de una vez las pantallas nuevas.

## 7. Fase 5 · AYNI-304 · Pruebas de mutación con PIT (8 h, Sprint 8)

PIT **no está configurado** en ningún `pom.xml` de `develop`.

- Añadir `pitest-maven` y `pitest-junit5-plugin` en el pom raíz, con un perfil `mutacion` para que no ralentice
  el build normal.
- Alcance inicial: `core.domain.*` (`Dinero`, `Movimiento`, `Conversion`, `TipoDeCambioAplicado`,
  `PoliticaDeSpread`, `CalculoDeInteres`). Es donde está el dinero y donde la cobertura del DoD (≥ 80 %) pesa más.
- Ejecutar, leer el informe, **matar los mutantes supervivientes del dominio monetario** con pruebas nuevas y
  anotar los que se aceptan, con motivo, en `docs/calidad/`.
- Va al final porque mide el código de las fases anteriores. Meta propuesta: ≥ 80 % de mutantes muertos en el
  dominio; ajustarla tras la primera ejecución.

## 8. Calendario realista

| Días | Trabajo |
|---|---|
| lun 23 – mié 25 | Fase 0 y fase 1 (3.1 a 3.7) |
| jue 26 | 3.8 (si 274/275 son mías) y cierre de fase 1; empujar y abrir PR en borrador |
| vie 27 – sáb 28 | Fase 2 |
| dom 29 | Fase 3 (apretado) |
| 30-nov en adelante | Fase 4 y fase 5, ya en el Sprint 8 |

Con 57 h y 6 días, el Sprint 7 **no alcanza** si todo es de una persona. Lo más probable es que 289 y 304 pasen
al Sprint 8; hay que hablarlo con el Scrum Master antes del jueves.

## 9. Qué no he verificado

- No ejecuté nada: ni compilé, ni corrí pruebas, ni creé la rama. Todo sale de leer `origin/develop`.
- No sé si `usuario_rol` existe en identity V2 ni cómo se asigna hoy el rol al registrarse.
- No leí el contenido de `ConversionTest`, `ConvertirMonedaServiceTest` ni `ConversionEnPostgresTest`.
- Las estimaciones de horas son las de Jira; en AYNI-281 el documento del sprint dice 5 h y Jira 12 h.
- No verifiqué quién tiene AYNI-274 y AYNI-275.
