# language: es
#
# HU-10 · Control de la tarjeta · AYNI-21
# Pruebas de aceptación: T-35 · AYNI-253
#
# Corren contra un PostgreSQL de Testcontainers con las migraciones de Flyway aplicadas.

Característica: Control de la tarjeta de débito virtual
  Como titular de una tarjeta de débito virtual
  Quiero congelarla o descongelarla con efecto inmediato y configurar su límite diario y compras por internet
  Para proteger mi dinero ante cualquier imprevisto y controlar cuánto puedo gastar cada día

  Antecedentes:
    Dado que Lucía tiene una cuenta de ahorro con S/ 1000.00 y su tarjeta de débito virtual ACTIVA

  Escenario: Congelar y descongelar la tarjeta con efecto inmediato
    Cuando Lucía congela su tarjeta desde la aplicación
    Entonces la tarjeta de Lucía pasa inmediatamente a estado CONGELADA
    Y un intento de consumo con tarjeta de S/ 80.00 es rechazado porque la tarjeta está congelada
    Y el saldo disponible de la cuenta de Lucía sigue siendo S/ 1000.00
    Cuando Lucía descongela su tarjeta
    Entonces la tarjeta de Lucía vuelve a estado ACTIVA
    Y la pista de auditoría y la bandeja de notificaciones registran el congelamiento y descongelamiento

  Escenario: Límite diario de consumo superado
    Dado que Lucía configuró un límite diario de consumo de S/ 300.00 en su tarjeta
    Y que Lucía ya realizó un consumo con tarjeta de S/ 200.00 hoy
    Cuando Lucía intenta realizar otro consumo con tarjeta de S/ 150.00 hoy
    Entonces el consumo es rechazado por superar el límite diario de la tarjeta
    Y el saldo disponible de la cuenta de Lucía permanece en S/ 800.00
    Y la pista de auditoría registra la actualización del control y el rechazo por límite diario
