# ADR-0025 · Segundo factor a elección y verificación del contacto inicial (HU-22)

- **Estado:** aceptado
- **Fecha:** 21 de septiembre de 2026
- **Historias afectadas:** HU-22 (AYNI-124 a AYNI-136), HU-01 (registro), HU-04 (inicio de sesión)
- **Extiende a:** [ADR-0010](0010-segundo-factor-totp-y-sesion-rotativa.md)

## Contexto

Con HU-22, Ayni Bank evoluciona la autenticación de segundo factor (2FA) permitiendo a la persona usuaria elegir entre tres modalidades soportadas:
1. **App Autenticadora (TOTP RFC 6238)**
2. **Correo Electrónico (Código OTP de un solo uso)**
3. **Mensaje SMS (Código OTP de un solo uso)**

Además, se exige verificar el contacto inicial durante el proceso de registro antes de considerar completa la inscripción de dicho canal de comunicación.

## Decisión

### 1. Modelo de datos flexible para métodos 2FA
Se incorpora la tabla `metodo_segundo_factor`, que registra los métodos 2FA inscritos y su estado de confirmación por usuario, soportando `APP_AUTENTICADORA`, `CORREO_ELECTRONICO` y `SMS`.
- El secreto TOTP (si aplica) se almacena cifrado en reposo con AES-256-GCM.

### 2. Desafíos OTP por código (SHA-256 y 3 intentos)
Para canales OTP (Correo Electrónico y SMS):
- Los códigos OTP consisten en 6 dígitos numéricos aleatorios generados mediante `SecureRandom`.
- **Nunca se almacena el código en claro en la base de datos**; se guarda únicamente su resumen **SHA-256** en la tabla `desafio_por_codigo`.
- Los desafíos OTP tienen una vigencia estricta de **10 minutos** (`DesafioPorCodigo.VIGENCIA`).
- Se limita a **máximo 3 intentos fallidos** de verificación (`DesafioPorCodigo.MAX_INTENTOS`), tras lo cual el desafío queda invalidado por seguridad.

### 3. Integración en el flujo de inicio de sesión y registro
- `SesionController` e `IniciarSesionService` verifican transparentemente el código 2FA presentado, soportando tanto desafíos de App Autenticadora (TOTP) como desafíos OTP de 6 dígitos registrados en `desafio_por_codigo`.
- Se dispone de endpoints REST para la solicitud, reenvío y verificación de códigos OTP de contacto inicial durante el registro (`VerificacionContactoController`).
- En la interfaz de usuario React (`ayni-web`), el formulario de confirmación de 2FA ofrece la selección activa del método de verificación (`SelectorMetodoSegundoFactor`) y la opción de reenvío para métodos OTP.
