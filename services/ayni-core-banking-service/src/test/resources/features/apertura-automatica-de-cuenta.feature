# language: es
#
# HU-05 · Apertura automática de cuenta de ahorro · AYNI-16
# Pruebas de aceptación: T-30 · AYNI-230
#
# Corren contra un PostgreSQL de Testcontainers con las migraciones de Flyway aplicadas. La
# aprobación entra por el mismo escuchador que en producción; RabbitMQ es lo único simulado,
# para poder hacerlo fallar y volver a voluntad.
#
# Los escenarios @pendiente describen un criterio que el código todavía no cumple: están
# escritos para que la brecha quede a la vista y no se ejecutan (ver PruebasDeAceptacion).

Característica: Apertura automática de cuenta de ahorro
  Como cliente recién verificado
  Quiero que se me abra automáticamente una cuenta de ahorro en soles al aprobarse mi KYC
  Para empezar a usar el banco de inmediato sin trámites adicionales

  Escenario: Apertura exitosa tras aprobación del KYC
    Dado que Marta ya posee una cuenta de ahorro activa en soles
    Y que la solicitud de onboarding de Luis pasa a estado APROBADA
    Cuando el sistema procesa la aprobación de Luis
    Entonces Luis tiene una cuenta de ahorro en soles con saldo S/ 0.00 y estado ACTIVA
    Y la cuenta de Luis está asociada al producto de ahorro en soles con su TREA vigente
    Y el número de cuenta de Luis es único y su CCI tiene 20 dígitos con control válido
    Y el evento CuentaAperturada de Luis está en la bandeja de salida, escrito en el mismo COMMIT que su cuenta

  Escenario: Intento de apertura duplicada
    Dado que Luis ya posee una cuenta de ahorro activa en soles
    Cuando se intenta abrir una segunda cuenta en soles para Luis
    Entonces el sistema rechaza la apertura
    Y Luis mantiene una única cuenta activa en soles

  # AYNI-326
  Escenario: Intento de apertura duplicada · registro en la pista de auditoría
    Dado que Luis ya posee una cuenta de ahorro activa en soles
    Cuando se intenta abrir una segunda cuenta en soles para Luis
    Entonces el intento duplicado de Luis queda registrado en la pista de auditoría

  # AYNI-327 · Aquí solo cabe la parte de core-banking. El aviso con el motivo lo prueba
  # aviso-de-solicitud-rechazada.feature, en notification.
  Escenario: KYC rechazado
    Dado que la solicitud de onboarding de Luis queda en estado RECHAZADA
    Cuando el sistema procesa el rechazo de Luis
    Entonces el sistema no crea ninguna cuenta para Luis

  Escenario: Fallo al publicar el evento
    Dado que la cuenta de Luis se creó correctamente pero RabbitMQ no está disponible
    Cuando el publicador intenta enviar el evento CuentaAperturada
    Entonces la cuenta de Luis permanece creada y consistente
    Y el evento CuentaAperturada de Luis permanece pendiente en la bandeja de salida
    Y el evento CuentaAperturada de Luis se publica en cuanto el broker se restablece
