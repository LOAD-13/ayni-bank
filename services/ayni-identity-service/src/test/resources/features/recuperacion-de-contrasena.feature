# language: es
#
# HU-21 · Recuperación de la contraseña · AYNI-123
#
# Los cinco escenarios siguen los criterios de aceptación de Jira. Igual que HU-01,
# corren contra el caso de uso con adaptadores en memoria: lo que la historia exige
# es comportamiento de negocio, no de transporte. La parte que sí depende de HTTP
# —que el cuerpo sea idéntico byte a byte y que la respuesta no salga antes de la
# duración mínima— la comprueba RecuperacionControllerTest.

Característica: Recuperación de la contraseña por correo
  Como persona que olvidó su contraseña
  Quiero recuperar el acceso desde mi correo
  Para volver a entrar sin depender de nadie

  Antecedentes:
    Dado que el reloj marca el 10 de octubre de 2026 a las 10:00
    Y que existe la cuenta activa "ana.quispe@example.pe" con segundo factor confirmado

  Escenario: La respuesta es idéntica exista o no la cuenta
    Cuando pido recuperar la contraseña de "ana.quispe@example.pe"
    Y pido recuperar la contraseña de "nadie@example.pe"
    Entonces las dos peticiones terminan igual, sin error
    Y solo "ana.quispe@example.pe" recibe un enlace de recuperación
    Y las dos peticiones quedan en la pista de auditoría

  Escenario: Un enlace válido permite fijar una contraseña nueva que cumple la política
    Dado que pedí recuperar la contraseña de "ana.quispe@example.pe"
    Cuando pasan 29 minutos
    Y fijo la contraseña nueva "corta" con el enlace recibido
    Entonces se rechaza por no cumplir la política de contraseñas
    Cuando fijo la contraseña nueva "Nueva!Clave2026#" con el enlace recibido
    Entonces mi contraseña queda cambiada

  Escenario: Un enlace usado o caducado no sirve
    Dado que pedí recuperar la contraseña de "ana.quispe@example.pe"
    Y que fijé la contraseña nueva "Nueva!Clave2026#" con el enlace recibido
    Cuando fijo la contraseña nueva "Otra!Clave2026#" con el enlace recibido
    Entonces se explica que el enlace ya no es válido
    Dado que pedí recuperar la contraseña de "ana.quispe@example.pe"
    Cuando pasan 30 minutos
    Y abro el enlace recibido
    Entonces se explica que el enlace ya no es válido

  Escenario: Cambiar la contraseña cierra todas las sesiones y queda auditado
    Dado que tengo dos sesiones abiertas
    Y que pedí recuperar la contraseña de "ana.quispe@example.pe"
    Cuando fijo la contraseña nueva "Nueva!Clave2026#" con el enlace recibido
    Entonces todas mis sesiones quedan invalidadas
    Y el cambio queda en la pista de auditoría
    Y recibo un aviso de que mi contraseña cambió

  Escenario: Recuperar la contraseña no es recuperar la cuenta
    Dado que pedí recuperar la contraseña de "ana.quispe@example.pe"
    Y que fijé la contraseña nueva "Nueva!Clave2026#" con el enlace recibido
    Cuando ingreso con "ana.quispe@example.pe" y la contraseña "Nueva!Clave2026#"
    Entonces el ingreso me pide el segundo factor antes de abrir la sesión
