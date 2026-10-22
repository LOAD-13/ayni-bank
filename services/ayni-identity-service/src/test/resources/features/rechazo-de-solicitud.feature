# language: es
#
# HU-05 · Apertura automática de cuenta de ahorro · AYNI-16 · escenario 3
# Rechazo de la solicitud por un operador: AYNI-327 (T-35 · AYNI-325)
#
# La parte de identity del escenario «KYC rechazado»: la solicitud queda rechazada con su
# motivo y se anuncia, para que notification-service le cuente el motivo al solicitante.
# Que no se abra ninguna cuenta se sigue de que nunca se anuncia una aprobación, que es lo
# único que hace abrirla en core-banking.
#
# Se ejecutan contra el caso de uso con adaptadores en memoria, como el resto de features
# de identity.

Característica: Rechazo de una solicitud de onboarding
  Como operador que revisa una solicitud a mano
  Quiero rechazarla con un motivo claro
  Para que el solicitante sepa por qué no se abrió su cuenta

  Antecedentes:
    Dado que Ana se registró y su solicitud quedó en revisión manual

  Escenario: KYC rechazado
    Cuando un operador rechaza la solicitud de Ana con el motivo SELFIE_NO_COINCIDE
    Entonces la solicitud de Ana queda RECHAZADA con el motivo SELFIE_NO_COINCIDE
    Y no se anuncia ninguna aprobación, así que no se abre ninguna cuenta
    Y se anuncia el rechazo a Ana con el texto "La foto de tu rostro no coincide con la de tu DNI."
    Y Ana sigue pendiente de verificación

  Escenario: Una solicitud ya aprobada no se puede rechazar
    Dado que la solicitud de Ana ya fue aprobada
    Cuando un operador rechaza la solicitud de Ana con el motivo DATOS_NO_COINCIDEN
    Entonces el rechazo no se admite porque la solicitud ya está cerrada
    Y no se anuncia ningún rechazo
