# language: es
#
# HU-03 · Verificación biométrica facial con selfie · AYNI-14
# Pruebas de aceptación derivadas de tests/onboarding_kyc.feature

Característica: Verificación biométrica facial con selfie
  Como solicitante de una cuenta en Ayni Bank
  Quiero entregar mi selfie en vivo tras verificar mi segundo factor para el cotejo biométrico con mi DNI
  Para completar mi onboarding digital sin acudir a una agencia física

  Antecedentes:
    Dado que Mateo completó la captura de su DNI y tiene una solicitud de onboarding abierta

  Escenario: Entrega exitosa de selfie con segundo factor verificado y aprobación posterior
    Dado que Mateo verificó su segundo factor de autenticación
    Cuando Mateo sube su selfie válida en vivo y la entrega para verificación
    Entonces el sistema guarda la selfie con su hash SHA-256 y pasa la solicitud a revisión de cotejo facial
    Cuando el cotejo facial con su DNI resulta favorable y se aprueba la solicitud
    Entonces la solicitud de Mateo pasa a estado APROBADA, el usuario queda ACTIVO y se anuncia la apertura de su cuenta

  Escenario: Bloqueo de entrega de selfie si el segundo factor aún no fue verificado
    Dado que Mateo aún no ha verificado su segundo factor de autenticación
    Cuando Mateo intenta entregar su selfie para verificación
    Entonces el sistema bloquea la entrega con SegundoFactorNoVerificadoException

  Escenario: Rechazo de selfie que supera el tamaño máximo permitido
    Dado que Mateo verificó su segundo factor de autenticación
    Cuando Mateo sube un archivo de selfie que supera los 5 MB permitidos y lo entrega
    Entonces el sistema elimina el objeto del almacén y rechaza la entrega con DocumentoNoSubidoException

  Escenario: Rechazo en el cotejo facial por no coincidencia biométrica con el DNI
    Dado que Mateo verificó su segundo factor de autenticación
    Y Mateo sube su selfie válida en vivo y la entrega para verificación
    Cuando el cotejo facial determina que la selfie no coincide con el rostro del DNI
    Entonces la solicitud de Mateo queda RECHAZADA con el motivo SELFIE_NO_COINCIDE y no se anuncia ninguna aprobación
