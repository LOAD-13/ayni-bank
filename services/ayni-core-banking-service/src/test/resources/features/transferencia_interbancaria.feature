
# language: es
@interbancarias

Característica: Transferencias interbancarias por CCI

  Como cliente de Ayni Bank
  Quiero transferir dinero a otros bancos
  Para realizar operaciones seguras

  Escenario: Transferencia interbancaria exitosa
    Dado que el cliente tiene saldo suficiente
    Y proporciona un CCI valido
    Cuando la camara acepta la transferencia
    Entonces el estado debe ser COMPLETADA

  Escenario: Transferencia interbancaria rechazada
    Dado que existe una transferencia pendiente
    Cuando la camara rechaza la transferencia
    Entonces el estado debe ser RECHAZADA
    Y se deben generar los asientos de reversion

  Escenario: Transferencia interbancaria vencida
    Dado que existe una transferencia pendiente vencida
    Cuando el sistema consulta la camara
    Y la camara confirma el rechazo
    Entonces se debe revertir el importe
    Y el estado debe ser RECHAZADA
