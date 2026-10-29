# language: es
#
# HU-04 · Inicio de sesión seguro con segundo factor · AYNI-15
# Pruebas de aceptación: T-29 · AYNI-229

Característica: Inicio de sesión seguro con segundo factor
  Como cliente registrado en Ayni Bank
  Quiero iniciar sesión en dos pasos con mi contraseña y mi segundo factor de autenticación
  Para acceder a mi banca con protección contra fuerza bruta, enumeración de cuentas y robo de sesión

  Antecedentes:
    Dado que la cuenta activa de "elena@example.pe" tiene la contraseña "Clave!Segura2026#" y segundo factor TOTP confirmado

  Escenario: Ingreso exitoso en dos pasos con contraseña y segundo factor
    Cuando Elena presenta sus credenciales con el correo "elena@example.pe" y la contraseña "Clave!Segura2026#"
    Entonces se abre un desafío de segundo factor sin emitir aún tokens de sesión
    Cuando Elena completa el desafío con el código TOTP válido "123456"
    Entonces el sistema emite un token de acceso JWT y un token de renovación rotativo
    Y la pista de auditoría registra el evento INGRESO_EXITOSO

  Escenario: Rechazo uniforme ante credenciales inválidas o correo inexistente
    Cuando alguien intenta ingresar con el correo "elena@example.pe" y la contraseña incorrecta "Otra!Clave9999#"
    Y alguien intenta ingresar con el correo no registrado "nadie@example.pe" y la contraseña "Otra!Clave9999#"
    Entonces ambos intentos se rechazan con CredencialesInvalidasException sin revelar si el correo existe
    Y en ambos casos se ejecuta la derivación Argon2id y se registra CREDENCIALES_INVALIDAS en la auditoría

  Escenario: Pausa del ingreso tras superar los intentos fallidos tolerados y aviso de seguridad
    Cuando se realizan 6 intentos fallidos consecutivos sobre la cuenta "elena@example.pe"
    Entonces la cuenta de "elena@example.pe" queda bloqueada temporalmente y recibe un único aviso de seguridad por correo
    Cuando Elena intenta ingresar con su contraseña correcta mientras dura el bloqueo
    Entonces el ingreso se rechaza con CuentaBloqueadaException y se registra INGRESO_BLOQUEADO

  Escenario: Detección de reutilización de Refresh Token e invalidación de la familia de sesión
    Dado que Elena inició sesión y renovó su sesión una vez rotando su Refresh Token
    Cuando un atacante presenta nuevamente el Refresh Token original ya consumido
    Entonces el sistema detecta la reutilización con ReutilizacionDeRefreshTokenException
    Y toda la familia de sesiones de Elena queda invalidada inmediatamente
    Y Elena recibe un aviso de cierre de sesión por seguridad y se registra REUTILIZACION_DE_TOKEN
