# ADR-0036 · Particionado mensual de `core.asiento`, retención y purga

**Estado:** Aceptada. El archivado en frío queda preparado pero **desactivado** (ver «Lo que queda
pendiente»).
**Fecha:** 26 de octubre de 2026
**Historia:** AYNI-155 (T-16, hallazgo del Taller de Diseño Físico de la semana 6)

---

## Contexto

Una fila de `core.asiento` ocupa 289,5 bytes con sus índices, medido cargando 500 000 filas. Cada
movimiento escribe dos por la partida doble. A un millón de usuarios activos:

| Escenario | Filas al día | Crecimiento | Disco de 200 GB lleno en |
|---|---|---|---|
| Central (Yape, 2,19 tx/día) | 4,37 M | 538 GiB/año | 135 días |
| Estrés (5 tx/día) | 10 M | 1 097 GiB/año | 66 días |

Ningún índice arregla un problema de volumen. Hace falta poder sacar los meses viejos de la tabla
caliente sin borrarlos, porque HU-15 exige una pista de auditoría inalterable. En PostgreSQL eso
pide particiones.

Además, `core.outbox` y `core.operacion_idempotente` crecen sin límite. Una fila de outbox ya
publicada no sirve para nada, y una clave de idempotencia solo protege de reintentos que llegan en
segundos o minutos.

## Decisión

**1. `PARTITION BY RANGE (registrado_en)`, una partición por mes** (migración V8). La migración
renombra la tabla vieja, crea la particionada con las mismas columnas y restricciones, copia las
filas y borra la vieja, todo en la transacción de Flyway. Nada referencia a `asiento` con clave
foránea, así que la reconstrucción es segura.

- **Los límites son el primer instante del mes en hora de Lima**, no en UTC. El extracto de
  octubre es el octubre del cliente: con UTC, un asiento de las 23:30 del día 31 caería en el mes
  siguiente.
- **La clave primaria pasa a ser `(id, registrado_en)`.** PostgreSQL solo puede garantizar unicidad
  dentro de cada partición, así que la clave tiene que incluir la columna de partición. El `id` es
  un UUID que genera la aplicación y sigue siendo único en la práctica. La entidad JPA no cambia.
- **Particiones con tres meses de antelación.** Las crea la función
  `core.crear_particiones_de_asiento(meses)`, que es idempotente. La llama a diario el
  mantenimiento de core-banking (`MantenimientoDeTablas`, a las 03:30 de Lima).
- **`asiento_default` como red de seguridad.** Si el mantenimiento dejara de correr, un asiento sin
  partición de su mes caería ahí en lugar de rechazarse y tumbar la transferencia. El mantenimiento
  avisa en el log si encuentra filas en ella.
- Los índices de la tabla se mantienen (`ix_asiento_cuenta`, `ix_asiento_movimiento`), ahora uno por
  partición.

**2. Los dos índices que justificó el taller:** `ix_cuenta_usuario` sobre `cuenta (usuario_id)`
(`ux_cuenta_titular_moneda` solo cubre las cuentas activas) e `ix_operacion_idempotente_registrado`
sobre `operacion_idempotente (registrado_en)`, que necesita la purga.

**3. Purga nocturna por lotes de 10 000 filas:** el outbox publicado hace más de 7 días (lo pendiente
no se toca nunca) y las claves de idempotencia de más de 30. Los lotes cortos evitan retener
bloqueos y llenar el WAL de golpe. Todo es configurable en `ayni.mantenimiento.*`.

**4. BRIN sobre `asiento (registrado_en)`: evaluado y no se crea por ahora.** Medido con 1 M de
asientos en orden cronológico en una partición de PostgreSQL 16:

| | B-tree | BRIN |
|---|---|---|
| Tamaño | 21 MB | **24 kB** |
| Suma de un día entero, todas las cuentas | 5,3 ms | 7,0 ms |

El BRIN cumple lo que prometía el taller: casi 900 veces más pequeño y casi igual de rápido. Pero
la consulta que se esperaba que lo usara, el devengo diario, filtra por cuenta **y** fecha
(`cuenta_id = ? AND registrado_en < ?`). Esa la resuelve `ix_asiento_cuenta` en 0,4 ms y el BRIN no
interviene. Hoy no hay ninguna consulta que recorra un rango de fechas de todas las cuentas. Un
índice que nadie usa se paga igual en cada inserción (apartado 9 de `modelo-de-datos.md`), así que
se creará cuando aparezca esa consulta: conciliación diaria, reportes regulatorios o el propio
archivado.

## Lo que queda pendiente: retención con `DETACH PARTITION` y archivado en frío

El taller pide retener 3 meses en línea, desvincular lo anterior con `DETACH PARTITION` (instantáneo)
y archivar la partición desvinculada en almacenamiento de objetos. **El procedimiento está escrito y
probado, pero no se activa**, porque choca con una regla del dominio:

> El saldo de una cuenta es la suma de **todos** sus asientos (ADR-0011).

Desvincular una partición saca sus asientos de esa suma. Cualquier cuenta con movimientos en ese mes
mostraría un saldo distinto del real, y el devengo calcularía intereses sobre una base equivocada.
No es un riesgo teórico: pasaría en el primer archivado.

**Qué hace falta para activarlo** (una historia propia, con su migración):

1. Un **asiento de saldo de apertura** por cuenta y moneda al inicio de cada mes retenido. Es una
   fila que lleva el saldo acumulado hasta el mes anterior y sustituye a los asientos que salen.
   Tiene que distinguirse de un abono real (por ejemplo, un `tipo` `APERTURA` que el extracto no
   muestra como movimiento).
2. Que las consultas de saldo, extracto y devengo lo tengan en cuenta.
3. Un cierre mensual que lo genere en la misma transacción en que se comprueba que cuadra con la
   suma de lo que sale.
4. Una prueba que archive un mes y verifique que ningún saldo cambia.

Mientras tanto, `infra/scripts/archivar-particion-de-asiento.sh` se niega a correr si no se define
`AYNI_ARCHIVADO_HABILITADO=si`. Con la variable:

1. comprueba que la partición existe y ya salió de los 3 meses de retención;
2. la vuelca con `COPY` a CSV comprimido, comprueba que el volcado tiene las mismas filas y anota su
   SHA-256;
3. la sube a S3 (o a un directorio, para pruebas) y verifica que llegó entera;
4. solo entonces la desvincula. La tabla desvinculada **se conserva** en la base: no se borra ni un
   asiento.

## Consecuencias

- Una consulta de extracto, que siempre lleva fecha, toca una o dos particiones en lugar de la tabla
  entera. El saldo sigue sumando todas, con `ix_asiento_cuenta` en cada una.
- Con el archivado desactivado, el disco sigue creciendo al mismo ritmo. El particionado no reduce
  el volumen por sí solo: prepara la tabla para que el archivado pueda hacerlo.
- La migración copia la tabla entera en una transacción. Hoy son pocas filas. Con millones habría
  que hacerla por fases o en una ventana de mantenimiento.
- `ayni_app` es dueña de la base (ver `desplegar.sh`), así que puede crear particiones. Si se aplica
  la política del apartado 5 del taller (un rol de aplicación sin `UPDATE` ni `DELETE` sobre
  `asiento`), el mantenimiento tendrá que correr con el rol dueño de la tabla.
- Cada partición nueva nace con sus índices y restricciones, porque se heredan del padre.

## Verificación

`ParticionadoDeAsientoTest` lleva una base de Testcontainers hasta V7, carga asientos de varios
meses, aplica V8 y comprueba: la tabla queda particionada por rango con clave `(id, registrado_en)`,
no se pierde ninguna fila y el saldo no cambia, cada asiento cae en su mes de Lima (incluido el de
las 23:30 del día 31), existen las particiones hasta tres meses por delante y la de reserva, siguen
vigentes las restricciones y existen los índices. También comprueba que crear particiones es
idempotente y que la purga borra solo lo publicado y viejo. Las pruebas de concurrencia y de
aceptación de core-banking corren sobre la tabla particionada. El script de archivado se probó a
mano contra un PostgreSQL 16 con V1–V8: sin la variable se niega, con un mes en retención se niega, y
con un mes viejo vuelca, verifica el SHA-256 y desvincula.
