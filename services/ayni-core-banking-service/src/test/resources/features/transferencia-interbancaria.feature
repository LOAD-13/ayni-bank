# language: es
#
# HU-11 · Transferencia interbancaria mediante CCI · AYNI-22
# Pruebas de aceptación: AYNI-263
#
# Corren contra un PostgreSQL de Testcontainers con las migraciones de Flyway aplicadas: los
# asientos, la bandeja de salida y la pista de auditoría son los reales. La cámara de
# compensación es lo único simulado, para poder hacerla aceptar, rechazar o callar a voluntad.
#
# CCI de prueba (válidos, de otro banco): 00219300001234567886 y 00310000009876543271.
# CCI con el dígito de control mal: 00219300001234567887.

Característica: Transferencia interbancaria mediante CCI
  Como cliente de Ayni Bank
  Quiero enviar dinero a una cuenta de otro banco con su CCI
  Para pagar a quien no tiene cuenta Ayni

  Antecedentes:
    Dado que Ana tiene una cuenta Ayni con 500.00 soles

  Escenario: Transferencia interbancaria aceptada
    Cuando Ana confirma con su segundo factor una transferencia de 120.50 soles al CCI "00219300001234567886"
    Entonces la cámara de compensación recibe la instrucción
    Y la transferencia queda en estado COMPLETADA
    Y su saldo es de 379.50 soles
    Y se anuncia el resultado COMPLETADA para avisarle por correo
    Y la pista de auditoría registra INTERBANCARIA_ENVIADA y INTERBANCARIA_COMPLETADA

  Escenario: Rechazo por la cámara de compensación
    Dado que Ana tiene una transferencia interbancaria de 80.00 soles pendiente
    Y su saldo es de 420.00 soles
    Cuando la cámara de compensación la rechaza
    Entonces la transferencia queda en estado RECHAZADA con el motivo RECHAZO_CAMARA
    Y los asientos de compensación le devuelven el importe
    Y su saldo es de 500.00 soles
    Y se anuncia el resultado RECHAZADA para avisarle por correo
    Y la pista de auditoría registra INTERBANCARIA_ENVIADA y INTERBANCARIA_REVERTIDA

  Escenario: CCI con dígito de control inválido
    Cuando Ana intenta transferir 50.00 soles al CCI "00219300001234567887"
    Entonces la operación se rechaza con el código CCI_INVALIDO
    Y su saldo es de 500.00 soles
    Y no se registra ninguna transferencia interbancaria

  Escenario: La cámara no responde en el plazo
    Dado que Ana tiene una transferencia interbancaria de 60.00 soles pendiente
    Cuando vence el plazo de espera sin respuesta de la cámara
    Entonces la transferencia queda en estado RECHAZADA con el motivo SIN_RESPUESTA_EN_PLAZO
    Y su saldo es de 500.00 soles

  Escenario: Sin confirmar con el segundo factor no se mueve el dinero
    Cuando Ana intenta transferir 50.00 soles al CCI "00219300001234567886" sin confirmar
    Entonces la operación pide confirmar con el segundo factor
    Y su saldo es de 500.00 soles

  Escenario: Reintentar con la misma clave no cobra dos veces
    Cuando Ana envía dos veces la misma transferencia de 30.00 soles al CCI "00310000009876543271"
    Entonces solo hay una transferencia interbancaria registrada
    Y su saldo es de 470.00 soles

  Escenario: Guardar un beneficiario frecuente
    Cuando Ana guarda el CCI "00219300001234567886" como "Mamá"
    Y vuelve a guardarlo como "Mamá BCP"
    Entonces sus beneficiarios frecuentes son "Mamá BCP"
