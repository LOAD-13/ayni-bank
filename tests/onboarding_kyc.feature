Feature: Verificación de Identidad (KYC) y Onboarding Biométrico
  Como usuario nuevo del banco
  Quiero verificar mi identidad de manera segura usando mi DNI y mi rostro
  Para poder abrir mi cuenta digitalmente sin ir a una agencia

  Background:
    Given que el usuario ha iniciado su registro y proporcionado sus datos básicos
    And el usuario ha capturado su DNI exitosamente

  Scenario: Happy path - Cotejo facial exitoso y vivacidad superada
    Given el usuario acepta los términos de consentimiento biométrico
    When el usuario captura su selfie en vivo frente a la cámara
    And el sistema valida que hay exactamente un rostro en la captura
    And el sistema detecta signos de vivacidad válidos
    And se envía la selfie y el DNI al servicio de verificación
    Then el servicio calcula un porcentaje de similitud mayor o igual al 90%
    And el sistema aprueba la verificación de identidad
    And el usuario avanza al paso de creación de contraseña

  Scenario: Rechazo automático por spoofing (pantalla detectada)
    Given el usuario acepta los términos de consentimiento biométrico
    When el usuario intenta usar una fotografía impresa o mostrada en un celular
    Then el sistema de vivacidad detecta el intento de fraude
    And la captura es bloqueada
    And el sistema informa al usuario que debe tomarse la foto en vivo

  Scenario: Derivación a revisión manual por similitud dudosa
    Given el usuario captura su selfie en vivo superando las pruebas de vivacidad
    When se envía la selfie y el DNI al servicio de verificación
    But el servicio calcula un porcentaje de similitud del 82%
    Then el sistema registra el evento en la pista de auditoría
    And el estado de la verificación cambia a "PENDIENTE_REVISION_MANUAL"
    And el usuario recibe un mensaje indicando que su solicitud está en evaluación

  Scenario: Rechazo por baja similitud (no es la misma persona)
    Given el usuario captura su selfie en vivo superando las pruebas de vivacidad
    When se envía la selfie y el DNI al servicio de verificación
    But el servicio calcula un porcentaje de similitud del 45%
    Then el sistema registra el evento como rechazado en la auditoría
    And el sistema informa al usuario que no se pudo validar su identidad
