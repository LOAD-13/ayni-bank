# language: es
#
# HU-22 · Segundo factor a elección y verificación del contacto en el registro · AYNI-124
#
# Los cinco escenarios siguen los criterios de aceptación de HU-22 (Sprint 2 y ADR-0025).
# Igual que HU-01 y HU-21, corren contra los casos de uso con adaptadores en memoria.

Característica: Segundo factor a elección y verificación del contacto en el registro
  Como solicitante en proceso de registro
  Quiero elegir mi método de segundo factor y verificar mi contacto antes de continuar con el DNI
  Para proteger mi cuenta desde el inicio y asegurar que el medio de contacto me pertenece

  Antecedentes:
    Dado que el reloj del segundo factor marca el 12 de octubre de 2026 a las 11:00
    Y que el solicitante "ana.quispe@example.pe" completó el paso 1 con celular "987654321"

  Escenario: Elección de App Autenticadora (TOTP)
    Cuando el solicitante elige el método de segundo factor "APP_AUTENTICADORA"
    Entonces se inscribe el método "APP_AUTENTICADORA" con un secreto TOTP único
    Y cuando confirma el contacto del registro con un código TOTP válido de 6 dígitos
    Entonces el método "APP_AUTENTICADORA" queda confirmado y puede avanzar al paso 3 del DNI

  Escenario: Elección de Correo Electrónico (OTP)
    Cuando el solicitante elige el método de segundo factor "CORREO_ELECTRONICO"
    Entonces se envía un código de verificación de 6 dígitos a "ana.quispe@example.pe"
    Y el solicitante no puede avanzar al paso 3 del DNI antes de verificar su contacto
    Cuando confirma el contacto del registro con el código enviado por correo
    Entonces el método "CORREO_ELECTRONICO" queda confirmado y puede avanzar al paso 3 del DNI

  Escenario: Opción SMS mostrada como no disponible
    Cuando el solicitante intenta elegir el método de segundo factor "SMS"
    Entonces la elección de "SMS" se rechaza explicando que el envío por SMS estará disponible próximamente

  Escenario: Código de correo incorrecto o vencido
    Dado que el solicitante eligió el método de segundo factor "CORREO_ELECTRONICO"
    Cuando intenta confirmar el contacto con el código incorrecto "000000" tres veces
    Entonces el tercer intento se rechaza por superar el máximo de 3 intentos permitidos
    Cuando solicita reenviar el código de verificación al correo
    Y transcurren 11 minutos en el reloj del segundo factor
    Y intenta confirmar el contacto con el nuevo código enviado por correo
    Entonces la confirmación se rechaza indicando que el código ha expirado

  Escenario: Uso del método elegido al iniciar sesión
    Dado que el solicitante eligió y confirmó el método "CORREO_ELECTRONICO" en el registro
    Cuando inicia sesión con el correo "ana.quispe@example.pe" y su contraseña
    Entonces el primer paso del ingreso abre un desafío de tipo "CORREO_ELECTRONICO" y envía un código al correo
    Cuando presenta ese código de 6 dígitos en el segundo paso del ingreso
    Entonces obtiene una sesión válida con su token de acceso
