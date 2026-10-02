# El modelo de datos de Ayni Bank, explicado

> Para el equipo. Si vas a tocar `core`, lee esto antes.
>
> Este documento explica **por qué** el modelo es como es. La referencia de **qué** hay exactamente
> está en las migraciones de Flyway, que son la única fuente de verdad del esquema.

---

## 1. La regla que lo explica casi todo: el saldo no se guarda

Si abres la tabla `core.cuenta` buscando una columna `saldo`, no está. **No es un olvido.** Es la
decisión que condiciona el resto del diseño, y está registrada en el
[ADR-0011](adr/0011-saldo-derivado-de-asientos.md).

El saldo de una cuenta es **el resultado de una suma**, no un dato:

```sql
SELECT COALESCE(SUM(CASE WHEN tipo = 'ABONO' THEN importe ELSE -importe END), 0)
FROM core.asiento
WHERE cuenta_id = :cuenta;
```

### Por qué, si guardarlo sería más rápido

Porque un saldo almacenado es un dato que **puede mentir**. En cuanto existe una columna `saldo`,
existe también la posibilidad de que alguien la actualice sin registrar el movimiento que la
justifica, o de que una actualización se pierda a medias y el saldo deje de cuadrar con el historial.
A partir de ese momento tienes dos verdades y ninguna forma de saber cuál es la buena.

Con el saldo derivado eso no puede pasar: **solo hay una verdad, y es el historial**. Si el saldo
está mal, es porque hay un asiento mal, y el asiento mal se ve.

El coste es real —hay que sumar— y se paga con el índice `ix_asiento_cuenta` y, más adelante, con la
desnormalización controlada del apartado 6.

---

## 2. Qué es un asiento y por qué siempre vienen de dos en dos

Un **asiento** es una anotación en el libro mayor: «en esta cuenta, este importe, en esta dirección».

```
core.asiento
├── id             UUID     identificador del asiento
├── cuenta_id      UUID     a qué cuenta afecta
├── movimiento_id  UUID     ← a qué transacción pertenece
├── tipo           CARGO | ABONO
├── importe        NUMERIC(14,2)   siempre positivo
├── moneda         CHAR(3)
├── concepto       VARCHAR(140)
└── registrado_en  TIMESTAMPTZ
```

La clave es `movimiento_id`. **Un movimiento de dinero genera dos asientos**, agrupados por ese
identificador: uno que descuenta de un sitio y otro que suma en otro.

### Ejemplo: Ana transfiere S/ 120,50 a Beto

| `movimiento_id` | `cuenta_id` | `tipo` | `importe` |
|---|---|---|---|
| `8f3a…` | cuenta de Ana | `CARGO` | 120.50 |
| `8f3a…` | cuenta de Beto | `ABONO` | 120.50 |

Dos filas. Una sola transacción de base de datos. **O entran las dos, o no entra ninguna.**

### La invariante: los asientos de un movimiento suman cero

```
suma(ABONO) − suma(CARGO) = 0   para todo movimiento_id
```

El dinero no aparece ni desaparece: **se mueve**. Si un movimiento no suma cero, hay un error, y la
consulta que lo detecta es trivial:

```sql
SELECT movimiento_id,
       SUM(CASE WHEN tipo = 'ABONO' THEN importe ELSE -importe END) AS descuadre
FROM core.asiento
GROUP BY movimiento_id
HAVING SUM(CASE WHEN tipo = 'ABONO' THEN importe ELSE -importe END) <> 0;
```

**Esta consulta debe devolver cero filas siempre.** Si algún día devuelve alguna, hay un fallo grave
y hay que parar. Esto es lo que hace el sistema *auditable*: no hace falta confiar en el código,
se puede comprobar.

### Por qué el importe es siempre positivo

`ck_asiento_importe_positivo` impide guardar importes negativos. **El signo lo lleva el `tipo`, nunca
el importe.** Si se permitieran los dos mecanismos, un importe negativo con tipo `ABONO` sería un
cargo disfrazado: cuadraría en las sumas y no lo vería nadie.

Una sola forma de expresar cada cosa. Si hay dos, alguien usará la equivocada.

---

## 3. Un asiento no se corrige: se compensa

Esto es lo que más choca al principio. **Un asiento ya escrito no se modifica ni se borra.** Nunca.

¿Y si nos equivocamos? Se escribe **otro** movimiento que deshace el efecto del primero:

| `movimiento_id` | `cuenta_id` | `tipo` | `importe` | `concepto` |
|---|---|---|---|---|
| `8f3a…` | Ana | `CARGO` | 120.50 | Transferencia a Beto |
| `8f3a…` | Beto | `ABONO` | 120.50 | Transferencia de Ana |
| `c17b…` | Ana | `ABONO` | 120.50 | **Reverso de 8f3a…** |
| `c17b…` | Beto | `CARGO` | 120.50 | **Reverso de 8f3a…** |

El saldo vuelve a su sitio **y queda constancia de que hubo un error y de que se corrigió**. Si
hubiéramos editado las filas originales, el saldo también sería correcto, pero el historial diría que
la transferencia nunca ocurrió. Eso es falsear un libro contable.

Por eso la política de seguridad del apartado 5 del taller propone que el usuario de la aplicación
tenga `SELECT` e `INSERT` sobre `core.asiento`, **pero no `UPDATE` ni `DELETE`**: la regla de negocio
defendida por el motor, no por la buena voluntad del programador.

---

## 4. Los importes: `BigDecimal` y `HALF_EVEN`, nunca `double`

En la base, `NUMERIC(14,2)`. En Java, `BigDecimal`. **`double` y `float` están prohibidos en todo lo
que toque dinero.**

### Por qué

`double` es binario y no puede representar exactamente la mayoría de los decimales:

```java
0.1 + 0.2 == 0.30000000000000004   // en double
```

Un céntimo de error por operación, multiplicado por millones de operaciones, es dinero real que no
cuadra. `NUMERIC` y `BigDecimal` son decimales de verdad y no tienen ese problema.

### Y por qué `HALF_EVEN` y no el redondeo de siempre

`HALF_UP` (el que aprendimos en el colegio: 0,5 sube) **introduce un sesgo**: siempre redondea hacia
arriba en el empate, así que a lo largo de millones de operaciones el banco gana sistemáticamente
fracciones de céntimo. `HALF_EVEN` redondea al par más cercano, de modo que los empates se reparten
y el sesgo tiende a cero. Es el redondeo bancario, y por eso se llama así.

Importa sobre todo en `HU-06`, el devengo diario de intereses: son millones de redondeos al día.

---

## 5. Idempotencia: por qué existe `operacion_idempotente`

La red falla. El móvil de Ana pierde cobertura justo después de enviar la transferencia y **no sabe
si llegó**. Reintenta. Si no hacemos nada, Beto cobra dos veces.

Por eso toda operación monetaria lleva una cabecera `Idempotency-Key`, y esa clave se guarda:

```
core.operacion_idempotente
├── clave          UUID  PRIMARY KEY   ← la clave que envió el cliente
├── resultado_id   UUID                ← el movimiento que se creó
└── registrado_en  TIMESTAMPTZ
```

El flujo es:

1. Llega una petición con clave `K`.
2. ¿Existe `K` en la tabla? → **Sí**: se devuelve el resultado de la primera vez. No se hace nada más.
3. → **No**: se ejecuta la operación y se guarda `K` junto al resultado, **en la misma transacción**.

Que `clave` sea la clave primaria no es casual: **es el motor quien garantiza que no haya dos**. Si
dos peticiones idénticas llegan a la vez a dos instancias distintas, una de las dos fallará al
insertar, y eso es exactamente lo que queremos.

---

## 6. La desnormalización que viene, y por qué está justificada

El modelo está en tercera forma normal. Vamos a romperlo **una vez, a propósito y por escrito**.

Cuando llegue `HU-08` (extracto de movimientos), calcular el saldo tras cada movimiento obligaría a
sumar todos los asientos anteriores **para cada línea del extracto**. Con cientos de miles de
asientos por cuenta, eso no se sostiene.

La solución será una columna `saldo_posterior` en el movimiento: el saldo que quedó justo después de
esa operación. Es un dato redundante —se puede recalcular— pero:

- Se escribe **una sola vez**, en la misma transacción que crea el movimiento.
- Después **nunca se modifica**, igual que el asiento.
- Es verificable: si `saldo_posterior` no coincide con la suma de los asientos hasta esa fecha, hay
  un fallo, y la comprobación es automatizable.

**La regla del proyecto:** se normaliza por defecto y se desnormaliza con motivo escrito. Si añades
una columna redundante sin dejar dicho por qué, te la van a rechazar en la revisión.

---

## 7. Por qué `core.cuenta` guarda `usuario_id` sin clave foránea

En `core.cuenta` hay un `usuario_id` que apunta a `identity.usuario`… **sin `FOREIGN KEY`**. Parece
un descuido. No lo es: [ADR-0004](adr/0004-proyeccion-local-vs-fk-entre-schemas.md).

Cada schema pertenece a un servicio distinto. Si pusiéramos la clave foránea:

- `core-banking` no podría migrar su esquema sin coordinarse con `identity`.
- No se podrían separar en dos bases de datos el día que el volumen lo exija.
- Un fallo de `identity` arrastraría a `core`.

La coherencia la sostiene el **evento**, no el motor: cuando `identity` da de alta un usuario,
publica un evento; `core` lo consume y crea la cuenta. Es más trabajo y hay que asumir que la
coherencia es *eventual* —puede haber un instante en que el usuario exista y la cuenta no—, y a
cambio los servicios son de verdad independientes.

En el diagrama entidad-relación esta es **la línea dorada discontinua**. Es la única que cruza un
contexto.

---

## 8. El Outbox: por qué no publicamos el evento directamente

Problema: hay que guardar el movimiento en la base **y** publicar un evento en RabbitMQ. Son dos
sistemas distintos y no hay transacción que los abarque a los dos.

- Si publicas primero y la base falla → **has avisado de algo que no ocurrió**.
- Si guardas primero y RabbitMQ falla → **ocurrió algo de lo que nadie se enteró**.

La solución ([ADR-0003](adr/0003-patron-transactional-outbox.md)) es no hablar con RabbitMQ durante
la transacción. Se escribe el evento en la tabla `core.outbox`, **en la misma transacción que los
asientos**. Un proceso aparte lee lo pendiente y lo publica.

```
Transacción:  INSERT asiento (×2)  +  INSERT outbox     ← todo o nada
Después:      publicador → lee outbox → publica → marca publicado_en
```

**Consecuencia importante para quien escriba consumidores:** este patrón garantiza *al menos una*
entrega, no *exactamente una*. Si el publicador se cae justo después de publicar y antes de marcar,
el evento se publica dos veces. **Todo consumidor debe ser idempotente.** No es opcional.

El índice `ix_outbox_pendiente` es parcial —`WHERE publicado_en IS NULL`— porque el publicador solo
pregunta por lo no publicado, que es unas pocas filas. Un índice sobre toda la tabla crecería con el
histórico sin aportar nada.

---

## 9. La consecuencia que casi nadie ve venir: la volumetría se dobla

Esto es lo que salió del taller de diseño físico de la semana 6, y conviene que lo tengáis presente.

**Cada transacción escribe dos filas, no una.** Al dimensionar el sistema hay que multiplicar por dos
antes de hacer ninguna otra cuenta.

Con un millón de usuarios activos y 2,19 transacciones diarias —el dato real de Yape, 66,49 al mes
por usuario activo—:

```
1 000 000 usuarios × 2,19 tx/día × 2 asientos = 4 374 342 filas/día
```

Y midiendo lo que ocupa de verdad una fila de `asiento` (289,5 bytes con sus índices, medido
cargando 500 000 filas, no estimado):

| | |
|---|---|
| Filas al día | 4,37 M |
| Filas al año | 1 596 M |
| **Crecimiento** | **1,47 GiB/día · 538 GiB/año** |

De ahí salen tres decisiones que veréis aparecer en el Sprint 3: **particionar `core.asiento` por
mes**, **retener 3 meses en línea** y **archivar el resto en frío**. Y de ahí sale también el dato
incómodo: el disco de 200 GB que teníamos previsto se llena en unos cuatro meses.

Otro detalle que sorprende: **los índices de `asiento` pesan el 84 % de lo que pesan los datos**. Por
eso no se añaden índices «por si acaso» a esa tabla. Cada índice de más se paga 4,37 millones de
veces al día.

---

## 10. Resumen: lo que no se negocia

1. **No añadas una columna `saldo`.** El saldo se deriva de los asientos.
2. **Un movimiento son dos asientos** que suman cero, en una sola transacción.
3. **Los asientos no se editan ni se borran.** Se compensan con un movimiento nuevo.
4. **`BigDecimal` con `HALF_EVEN`.** `double` y `float` están prohibidos en todo lo que sea dinero.
5. **Toda operación monetaria es idempotente.** Con `Idempotency-Key` y su fila en la tabla.
6. **Todo consumidor de eventos es idempotente.** El Outbox entrega al menos una vez.
7. **No pongas claves foráneas entre schemas.** La coherencia la sostiene el evento.
8. **Si desnormalizas, escribe por qué.** En el código y en el ADR.
9. **No añadas índices a `asiento` sin medir.** Se pagan en cada inserción.

---

## Para seguir leyendo

| Qué | Dónde |
|---|---|
| El esquema exacto | `services/*/src/main/resources/db/migration/` |
| Diagrama entidad-relación interactivo | `Semana 06/erd.html` · fuente DBML en `ayni-bank.dbml` |
| Volumetría, índices, seguridad y respaldo | `Semana 06/Taller de Diseño Físico…docx` |
| Por qué el saldo se deriva | [ADR-0011](adr/0011-saldo-derivado-de-asientos.md) |
| Por qué no hay FK entre schemas | [ADR-0004](adr/0004-proyeccion-local-vs-fk-entre-schemas.md) |
| Por qué el Outbox | [ADR-0003](adr/0003-patron-transactional-outbox.md) |
| Especificación viva del sistema | [`diseno-base.md`](diseno-base.md) |
