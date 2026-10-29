# language: es
#
# HU-07 · Transferencia entre cuentas Ayni · AYNI-18
# Pruebas de aceptación: T-31 · AYNI-231
#
# Corren contra un PostgreSQL de Testcontainers con las migraciones de Flyway aplicadas.

Característica: Transferencia entre cuentas Ayni
  Como titular de una cuenta de ahorro Ayni
  Quiero transferir dinero en soles a otra cuenta Ayni con confirmación de segundo factor e idempotencia
  Para enviar dinero al instante sin riesgo de cobros duplicados ni saldos inconsistentes

  Antecedentes:
    Dado que Carla tiene una cuenta Ayni activa con S/ 500.00
    Y que Diego tiene una cuenta Ayni activa con S/ 100.00

  Escenario: Transferencia exitosa con partida doble y comprobante
    Cuando Carla transfiere S/ 150.00 a la cuenta de Diego con el concepto "Almuerzo" y confirma con su segundo factor
    Entonces el saldo de Carla queda en S/ 350.00 y el de Diego en S/ 250.00
    Y el movimiento registra un CARGO y un ABONO cuya suma algebraica es S/ 0.00
    Y se genera un comprobante válido con la cuenta destino enmascarada

  Escenario: Rechazo por saldo insuficiente
    Cuando Carla intenta transferir S/ 600.00 a la cuenta de Diego confirmando con su segundo factor
    Entonces la transferencia se rechaza con el motivo SALDO_INSUFICIENTE
    Y el saldo de Carla sigue siendo S/ 500.00 y el de Diego S/ 100.00
    Y el rechazo queda registrado en la pista de auditoría

  Escenario: Rechazo por cuenta destino inexistente o propia
    Cuando Carla intenta transferir S/ 50.00 a su propia cuenta confirmando con su segundo factor
    Entonces la transferencia se rechaza con el motivo MISMA_CUENTA
    Cuando Carla intenta transferir S/ 50.00 a la cuenta inexistente "00111999999999" confirmando con su segundo factor
    Entonces la transferencia se rechaza con el motivo CUENTA_DESTINO_INEXISTENTE

  Escenario: Exigencia de confirmación con segundo factor
    Cuando Carla intenta transferir S/ 100.00 a la cuenta de Diego sin confirmación de segundo factor
    Entonces el sistema exige la confirmación con segundo factor antes de mover dinero
    Y el saldo de Carla sigue siendo S/ 500.00 y el de Diego S/ 100.00

  Escenario: Idempotencia ante reintentos con la misma clave
    Cuando Carla envía dos veces la misma transferencia de S/ 120.00 a Diego con la misma clave de idempotencia
    Entonces ambas respuestas devuelven el mismo comprobante y el dinero se mueve una sola vez
    Y el saldo de Carla queda en S/ 380.00 y el de Diego en S/ 220.00
