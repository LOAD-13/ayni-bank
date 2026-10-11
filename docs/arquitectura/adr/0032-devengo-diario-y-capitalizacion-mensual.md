# ADR-0032 · Devengo diario y capitalización mensual de intereses

**Estado:** Aceptada
**Fecha:** 11 de octubre de 2026
**Historia:** AYNI-17 (HU-06)

---

## Contexto

HU-06 exige:
- devengar cada día `saldo_cierre × ((1 + TEA)^(1/360) − 1)`;
- capitalizar el último día de cada mes;
- calcular todo con `BigDecimal` y `HALF_EVEN`;
- registrar cada devengo con su saldo base y su tasa;
- conservar la tasa histórica cuando el producto cambia de tasa.

Hay que fijar cuatro cosas que la historia no dice:
- qué significa «día» y «saldo de cierre»;
- cómo se eleva a 1/360 sin `double`;
- qué se hace con los decimales que no llegan a céntimo;
- qué pasa si el proceso no corre un día.

## Decisión

1. **El día es el de Lima.** El saldo de cierre del día *D* es la suma de los asientos registrados
   antes de las 00:00 de *D + 1* en `America/Lima`. Se calcula sobre el libro mayor (ADR-0011);
   no hay un saldo guardado que pueda desincronizarse.

2. **La raíz 360 se calcula con el método de Newton en aritmética decimal de 34 dígitos**
   (`MathContext.DECIMAL128`). `BigDecimal` no admite exponentes fraccionarios y `Math.pow` usaría
   `double`. El devengo se redondea a 8 decimales con `HALF_EVEN`, que es la precisión de
   `devengo_interes.importe` (`NUMERIC(19,8)`).

3. **Sin saldo no hay devengo.** El escenario 4 admite «devengo de importe cero o ningún devengo».
   Se elige no registrar nada: un registro por cuenta vacía y por día sería ruido sin información.
   El cierre del día sí queda registrado, así que se sabe que el proceso corrió.

4. **La capitalización abona a céntimos y arrastra el residuo.** Los devengos del mes, más el
   residuo del mes anterior, se redondean a 2 decimales con `HALF_EVEN`. La diferencia (menos de
   medio céntimo, positiva o negativa) queda en `capitalizacion_interes.residuo` y entra en el mes
   siguiente. Así, en toda la vida de la cuenta, lo abonado difiere de lo devengado en menos de
   medio céntimo: ni el banco se queda con fracciones ni las regala.
   - Una restricción de la base comprueba que `devengado + residuo_anterior = abonado + residuo`.

5. **La contrapartida es una cuenta técnica de intereses** (`1e7e0000-…`, migración V5). Su saldo
   negativo es el total de intereses que ha pagado el banco, igual que el de la cuenta de fondeo
   refleja los depósitos simulados.
   - Las cuentas técnicas se marcan con `cuenta.tecnica` y no devengan.

6. **El abono se fecha en el primer instante del mes siguiente,** no en el momento en que corre el
   proceso. Si el cierre del 31 se recupera con días de retraso, el interés cuenta igual desde el
   día 1 en el saldo de cierre y empieza a rendir a su vez.

7. **El proceso es idempotente y recupera días perdidos.**
   - `cierre_diario` registra cada día cerrado.
   - Un disparo cada hora (`AYNI_DEVENGO_CRON`, por defecto a los 5 minutos de cada hora, en hora
     de Lima) cierra en orden los días terminados que falten, cada uno en su propia transacción.
   - Si un día falla, se detiene: cerrar el siguiente sin el anterior rompería la cadena.
   - `UNIQUE (cuenta_id, fecha)` y `UNIQUE (cuenta_id, periodo)` impiden duplicados aunque dos
     procesos coincidan.
   - La primera vez se empieza por el día anterior: no se devengan meses previos a la puesta en
     marcha.

8. **La tasa se toma por fecha de vigencia,** no «la actual»: `vigencia_desde ≤ D < vigencia_hasta`.
   Cada devengo copia la TEA y guarda el `tasa_producto_id`, así que un cambio posterior del
   catálogo no altera lo ya calculado (escenario 3).

9. **El cliente recibe un aviso de lo ganado.** La capitalización escribe `InteresCapitalizado` en el
   outbox (`core.interes.capitalizado`) con el importe abonado, el saldo resultante y el mes.
   notification-service lo convierte en correo, igual que los movimientos de HU-13.

## Consecuencias

- La TREA y lo ganado se consultan en `GET /api/v1/cuentas/mia/rendimiento`. Los porcentajes y los
  importes salen como texto, de modo que la interfaz no hace aritmética con dinero.
- El historial y el comprobante reconocen el abono de intereses por su contrapartida técnica
  (`TipoDeMovimiento.INTERES`).
- Los devengos con 8 decimales no tocan el libro mayor, que trabaja con céntimos. Solo la
  capitalización mensual mueve dinero.
- Con una única instancia de core-banking no hace falta un bloqueo distribuido para el proceso. Si
  se escala horizontalmente, las restricciones `UNIQUE` evitan el doble devengo, pero conviene añadir
  ShedLock para no lanzar cierres que fallarían por duplicado.
