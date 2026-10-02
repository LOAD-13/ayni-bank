# language: es
#
# HU-02 · Verificación de identidad mediante fotografía del DNI · AYNI-13
#
# Los cinco escenarios aprobados en Jira, más uno para el criterio de aceptación que no
# tenía escenario propio («el solicitante puede corregir manualmente cualquier dato mal
# extraído antes de confirmar»). Se ejecutan contra los casos de uso reales
# (EvaluarCapturaDeDni, ExtraerDatosDelDni, ConfirmarDatosDelDni y
# GestionarFalloDeVerificacionKyc) con adaptadores en memoria para MinIO y kyc-service: mismo
# criterio que registro-de-usuario.feature, sin HTTP ni Resilience4j real. Que el circuito se
# abra de verdad ante un kyc-service caído lo prueba AdaptadorVerificadorKycTest.
#
# Diferencias con el texto de Jira, documentadas en vez de ocultadas:
#
# 1. Jira dice "EN_REVISION"; el estado que el sistema persiste es "EN_REVISION_MANUAL"
#    (V2__usuario_persona_y_solicitud_de_onboarding.sql). Los pasos comprueban el real.
# 2. Escenario 4: el tercer fallo del mismo lado ya deriva (ADR-0021, ADR-0026). La cuarta
#    captura que narra Jira se responde como derivada, sin evaluarse ni volver a avisar.
# 3. "kyc-vision-service" es ayni-kyc-service.

Característica: Verificación de identidad mediante fotografía del DNI
  Como solicitante
  Quiero fotografiar el anverso y el reverso de mi DNI para que el sistema extraiga mis datos automáticamente
  Para no tener que escribirlos a mano ni acudir a una agencia a acreditar mi identidad

  Antecedentes:
    Dado que "ana.quispe@example.pe" completó su registro declarando el DNI "44556677"

  Escenario: Captura correcta de ambos lados
    Dado que el solicitante accede al paso de verificación de identidad
    Cuando el solicitante captura el anverso y el reverso de su DNI con calidad suficiente
    Entonces el sistema detecta que ambas imágenes corresponden a un DNI peruano
    Y el sistema extrae los datos mediante OCR y los muestra para confirmación
    Y el sistema almacena ambas imágenes en MinIO registrando su hash SHA-256

  Escenario: La imagen no es un documento de identidad
    Dado que el solicitante captura una fotografía de un objeto que no es un DNI
    Cuando el sistema analiza la imagen
    Entonces el sistema rechaza la captura
    Y el sistema no almacena la imagen
    Y el sistema indica al solicitante que debe fotografiar su DNI

  Esquema del escenario: Calidad insuficiente
    Dado que el solicitante captura el DNI con <problema>
    Cuando el sistema evalúa la calidad de la imagen
    Entonces el sistema rechaza la captura
    Y el sistema indica el motivo concreto "<motivo>" para que el solicitante repita

    Ejemplos:
      | problema            | motivo      |
      | desenfoque          | DESENFOQUE  |
      | reflejos            | REFLEJO     |
      | encuadre incompleto | ENCUADRE    |
      | poca luz            | ILUMINACION |

  Escenario: Agotamiento de intentos
    Dado que el solicitante ha fallado 3 capturas del mismo lado del documento
    Cuando el solicitante intenta una cuarta captura
    Entonces el sistema deriva la solicitud a revisión manual con estado EN_REVISION_MANUAL
    Y el sistema notifica al solicitante que su caso será revisado por un operador

  Escenario: El servicio de visión no responde
    Dado que el servicio kyc-vision-service está caído o excede el timeout de 10 segundos
    Cuando el solicitante envía la captura de su DNI
    Entonces el sistema no falla con error técnico
    Y el sistema registra la solicitud en estado EN_REVISION_MANUAL
    Y el sistema informa al solicitante que su verificación continuará en breve

  Escenario: Corrección de un dato mal extraído antes de confirmar
    Dado que el OCR leyó del anverso el nombre "ANA LUClA" en lugar de "ANA LUCIA"
    Cuando el solicitante corrige el nombre a "Ana Lucía" y confirma sus datos
    Entonces el sistema acepta los datos confirmados
    Y el sistema conserva tanto lo que leyó el OCR como lo que confirmó el solicitante
