# language: es
#
# HU-02 · Verificación de identidad mediante fotografía del DNI · AYNI-13
#
# Jira aprobó cinco escenarios para esta historia. Aquí solo se automatizan dos: el
# agotamiento de intentos y la caída de kyc-service. Los otros tres —captura correcta,
# documento que no es un DNI, calidad insuficiente— dependen de un caso de uso de
# integración Java↔Python que todavía no existe (ver ADR-0024 y la deuda anotada en el plan
# de AYNI-13). registro-de-usuario.feature sostiene una regla explícita: un escenario
# copiado de Jira se prueba contra código real, siempre — nunca se deja fingiendo que pasa
# ni "pendiente". Escribir aquí los otros tres sin poder cumplir esa regla sería romperla,
# así que no se escriben hasta que la integración exista.
#
# Los dos escenarios que sí se automatizan se copian de Jira con tres diferencias, todas
# documentadas en vez de ocultadas:
#
# 1. Jira describe el estado como "EN_REVISION"; el estado que el sistema persiste de
#    verdad es "EN_REVISION_MANUAL" (V2__usuario_persona_y_solicitud_de_onboarding.sql).
#    Los pasos comprueban el estado real.
# 2. Jira habla de "3 capturas del MISMO LADO" del documento. El contador que existe
#    (ADR-0021, `solicitud_onboarding.intentos_verificacion_kyc`) es uno solo por
#    solicitud, no uno por anverso y otro por reverso — cuenta intentos fallidos en total,
#    sin distinguir de qué lado vinieron.
# 3. Encontrada al ejecutar este mismo escenario contra el código real (que es exactamente
#    para lo que sirve esta prueba): Jira narra "falla 3 veces, intenta una cuarta, y esa
#    cuarta deriva". ADR-0021 implementó "el tercer fallo mismo deriva" — no hay un cuarto
#    intento que llegue a procesarse. Se prefiere no tocar ahora una lógica ya enviada,
#    probada y documentada por una ambigüedad del propio texto de Jira (su criterio de
#    aceptación en prosa dice "máximo 3 intentos... antes de derivar", que coincide con el
#    código, no con la narrativa del escenario). Los pasos de abajo reflejan el
#    comportamiento real: dos fallos previos, un tercero que deriva.
#
# Se ejecutan contra GestionarFalloDeVerificacionKycService con adaptadores en memoria, no
# contra HTTP ni Resilience4j real — mismo criterio que registro-de-usuario.feature. Por eso
# "el circuit breaker abre" del escenario 2 no se observa aquí como tal (eso ya lo prueba
# AdaptadorVerificadorKycTest, subtarea 10, que sí necesita un contexto Spring para el proxy
# AOP): lo que este escenario prueba es la reacción del sistema una vez que la resiliencia
# ya se agotó y llamó a `derivarPorServicioNoDisponible`.

Característica: Verificación de identidad mediante fotografía del DNI
  Como solicitante
  Quiero fotografiar el anverso y el reverso de mi DNI para que el sistema extraiga mis datos automáticamente
  Para no tener que escribirlos a mano ni acudir a una agencia a acreditar mi identidad

  Antecedentes:
    Dado que existe una solicitud de onboarding para "ana.quispe@example.pe"

  Escenario: Agotamiento de intentos
    Dado que el solicitante ha fallado 2 capturas del mismo lado del documento
    Cuando el solicitante intenta una tercera captura
    Entonces el sistema deriva la solicitud a revisión manual
    Y el sistema notifica al solicitante que su caso será revisado por un operador

  Escenario: El servicio de visión no responde
    Dado que el servicio kyc-vision-service está caído o excede el timeout de 10 segundos
    Cuando el solicitante envía la captura de su DNI
    Entonces el sistema no falla con error técnico
    Y el sistema registra la solicitud en revisión manual
    Y el sistema informa al solicitante que su verificación continuará en breve
