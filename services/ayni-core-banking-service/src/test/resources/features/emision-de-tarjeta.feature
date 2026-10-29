# language: es
#
# HU-09 · Emisión de tarjeta de débito virtual · AYNI-20
# Pruebas de aceptación: AYNI-245
#
# Corren contra un PostgreSQL de Testcontainers con las migraciones de Flyway aplicadas: la
# aprobación entra por el mismo escuchador que en producción, y el PAN cifrado, su huella y la
# pista de auditoría son los reales. El segundo factor lo resuelve un verificador de prueba.

Característica: Emisión de tarjeta de débito virtual
  Como cliente de Ayni Bank
  Quiero recibir una tarjeta de débito virtual al abrirse mi cuenta
  Para poder pagar por internet de inmediato

  Escenario: Emisión automática
    Dado que se aprueba el KYC de Rosa
    Cuando el sistema procesa la apertura de su cuenta
    Entonces Rosa tiene una tarjeta de débito virtual ACTIVA asociada a esa cuenta
    Y el PAN generado supera la validación de Luhn
    Y el PAN está guardado cifrado y no aparece en claro en la base
    Y la tarjeta muestra solo los últimos 4 dígitos
    Y la tarjeta vence dentro de 4 años
    Y la pista de auditoría registra la emisión de la tarjeta

  Escenario: Revelación del PAN completo
    Dado que Rosa ya tiene su tarjeta de débito virtual
    Cuando pide ver el número completo y confirma con su segundo factor desde la IP "203.0.113.25"
    Entonces ve el PAN completo durante 30 segundos
    Y la pista de auditoría registra la revelación con su fecha, hora e IP "203.0.113.25"

  Escenario: Sin segundo factor no se revela el PAN
    Dado que Rosa ya tiene su tarjeta de débito virtual
    Cuando pide ver el número completo sin confirmar
    Entonces se le pide confirmar con su segundo factor
    Y la pista de auditoría no registra ninguna revelación
