# language: es
#
# HU-05 · Apertura automática de cuenta de ahorro · AYNI-16 · escenario 3
# Aviso del rechazo al solicitante: AYNI-327 (T-35 · AYNI-325)
#
# La parte de notification del escenario «KYC rechazado»: identity publica el rechazo con el
# motivo ya redactado y aquí se convierte en un correo. Quien lo recibe no llegó a ser
# titular, así que el evento trae su correo. Comparte dobles y pasos con la feature de HU-13.

Característica: Aviso del rechazo de una solicitud de apertura
  Como solicitante cuya solicitud fue rechazada
  Quiero recibir un correo con el motivo
  Para entender por qué no se abrió mi cuenta

  Antecedentes:
    Dado que el reloj marca el 21 de octubre de 2026 a las 10:00 en Lima

  Escenario: KYC rechazado
    Cuando identity publica que rechazó la solicitud de "Luis" con el correo "luis@example.pe" porque "La foto de tu rostro no coincide con la de tu DNI."
    Y pasa el despachador
    Entonces "luis@example.pe" recibe un correo "No pudimos aprobar tu solicitud" que dice "Motivo: La foto de tu rostro no coincide con la de tu DNI."
    Y el correo de "luis@example.pe" dice "no pudimos aprobarla" y "No se abrió ninguna cuenta a tu nombre"

  Escenario: Un rechazo que llega dos veces produce un solo correo
    Cuando identity publica que rechazó la solicitud de "Luis" con el correo "luis@example.pe" porque "No pudimos validar tu DNI con las fotos que enviaste."
    Y el mismo mensaje llega otra vez
    Y pasa el despachador
    Y pasa el despachador
    Entonces "luis@example.pe" recibe exactamente 1 correo
