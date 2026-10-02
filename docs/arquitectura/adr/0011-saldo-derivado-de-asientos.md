# ADR-0011 · El saldo no se guarda: se deriva de los asientos

- **Estado:** aceptado
- **Fecha:** 30 de agosto de 2026
- **Historias afectadas:** HU-05 (apertura de cuenta), HU-08 y HU-09 (transferencias), HU-11 (devengo)
- **Reemplaza a:** ninguno

## Contexto

HU-05 abre la cuenta de ahorro. La decisión de modelado que hay que tomar antes de escribir una sola
línea es dónde vive el saldo, porque todo lo que venga después —transferencias, devengo diario,
estados de cuenta— depende de esa respuesta.

Lo natural, y lo que hace la mayoría de los CRUD, es una columna `saldo` en la tabla `cuenta` que se
actualiza con cada movimiento.

## Decisión

**`cuenta` no tiene columna de saldo.** El saldo es la suma de los asientos de esa cuenta, calculada
cuando hace falta.

```
cuenta    identidad: número, CCI, moneda, estado, producto
asiento   cada apunte, por partida doble. Nunca se edita ni se borra
```

Un saldo almacenado es un número que alguien puede escribir. Tarde o temprano deja de cuadrar con los
movimientos que lo produjeron —una transacción a medias, una corrección hecha a mano, un error de
concurrencia—, y cuando eso pasa en un banco **no hay forma de saber cuál de los dos miente**.
Derivándolo, la pregunta «¿por qué tengo este saldo?» siempre tiene respuesta, y no existe ninguna
operación capaz de cambiarlo sin dejar rastro.

Las reglas que lo sostienen:

- **Los asientos no se editan ni se borran.** Corregir un error es añadir otro asiento que lo
  compense, no tocar el equivocado. Así se puede reconstruir cualquier saldo pasado.
- **El signo lo lleva el tipo, no el importe.** `CARGO` y `ABONO`, con importe siempre positivo y
  una restricción en la base que lo exige. Un cargo de −50 sería un abono disfrazado, y bastaría uno
  para que ninguna suma volviera a cuadrar.
- **`movimiento_id` agrupa los asientos de una misma operación.** En una transferencia son dos —el
  cargo en origen, el abono en destino— y su suma debe ser cero. Sin ese identificador común no
  habría forma de comprobarlo.
- **Los importes son `BigDecimal` contra `NUMERIC`, con redondeo `HALF_EVEN`.** `double` no
  representa 0.10 exactamente; sumar diez veces diez céntimos no da un sol. Y `HALF_UP` empuja
  siempre hacia arriba en los empates, lo que sobre muchas operaciones introduce un sesgo acumulado
  a favor de una de las partes. `HALF_EVEN` alterna y el sesgo tiende a cero.
- **No se convierte entre monedas de forma implícita.** Convertir exige un tipo de cambio, y un tipo
  de cambio exige saber de qué día es y con qué margen. Operar dos importes de monedas distintas
  lanza una excepción.

## Alternativas evaluadas

**Columna `saldo` actualizada con cada movimiento.** Lectura instantánea, y el problema descrito
arriba: dos fuentes de verdad que pueden divergir sin que nadie se entere.

**Columna `saldo` más los asientos, con un proceso que concilia por las noches.** Es lo que hacen
muchos bancos con sistemas heredados. Funciona, y significa que durante horas puedes estar
enseñándole a alguien un saldo que no es el suyo.

**Saldo derivado más una proyección materializada.** Es la evolución natural de esta decisión cuando
el volumen lo pida: la definición sigue siendo una sola y la proyección es una caché que se puede
reconstruir. No hace falta todavía.

## Consecuencias

**A favor**

- El saldo siempre es explicable y auditable.
- No existe ninguna operación que lo modifique sin dejar un asiento.
- Una cuenta nace en cero porque no tiene asientos, no porque alguien haya escrito un cero. No hay
  forma de crear dinero de la nada: meterlo exige un asiento, y un asiento deja rastro.

**En contra**

- **Consultar el saldo cuesta una suma.** Con dos años de movimientos son miles de filas, y cargarlos
  todos cada vez que alguien mira su saldo no se sostiene. Lo que se hace es sumar en la base, no en
  memoria; la operación vive en el dominio para que la *definición* de saldo sea una sola, pero el
  cálculo real se delega. Cuando el volumen lo pida, una proyección materializada.
- Hay que ser disciplinado: cualquier atajo futuro que escriba un saldo directamente rompe el
  invariante entero.

## Pendiente

- Consulta del saldo agregada en SQL en lugar de cargando los asientos, antes de que haya volumen
  real (HU-07).
- Proyección materializada si los tiempos de respuesta lo exigen. No antes: sería optimizar sin
  medir.
