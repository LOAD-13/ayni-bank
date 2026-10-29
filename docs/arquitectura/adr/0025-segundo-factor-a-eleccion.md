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

### 1. Modelo de datos flexible para métodos 2FA y cifrado en reposo
Se incorpora la tabla `metodo_segundo_factor` (`V10__segundo_factor_y_desafios_codigo.sql`), que registra los métodos 2FA inscritos y su estado de confirmación por usuario, soportando `APP_AUTENTICADORA`, `CORREO_ELECTRONICO` y `SMS`.
- El secreto TOTP (si aplica) se almacena cifrado en reposo con **AES-256-GCM** mediante `CifradorDeDatosPort` en `AdaptadorRepositorioDeMetodoSegundoFactor`, nunca en texto claro.
- El onboarding evoluciona de 5 a **6 pasos** (`V11__onboarding_seis_pasos.sql`, `CHECK (paso_actual BETWEEN 1 AND 6)`):
  1. Tus datos
  2. Segundo factor
  3. DNI anverso
  4. DNI reverso
  5. Prueba de vida
  6. Listo

### 2. Desafíos OTP por código (SHA-256 con sal por usuario y 3 intentos)
Para canales OTP (`CORREO_ELECTRONICO` y `SMS`):
- Los códigos OTP consisten en 6 dígitos numéricos aleatorios generados mediante `SecureRandom`.
- **Nunca se almacena el código en claro en la base de datos**; se guarda únicamente su resumen **SHA-256** con sal ligada al `usuarioId` (`usuarioId:codigo`) en la tabla `desafio_por_codigo` y se compara en tiempo constante (`MessageDigest.isEqual`).
- Los desafíos OTP tienen una vigencia estricta de **10 minutos** (`DesafioPorCodigo.VIGENCIA`).
- Se limita a **máximo 3 intentos fallidos** de verificación (`DesafioPorCodigo.MAX_INTENTOS`), tras lo cual el desafío queda invalidado por seguridad.
- En esta versión académica sin proveedor SMS contratado, la elección de `SMS` se muestra visible con la insignia **«Próximamente»** y deshabilitada en la interfaz, y el backend rechaza su selección con `400 Bad Request`.

### 3. Verificación obligatoria antes del KYC e integración con el inicio de sesión
- Ningún paso de captura o evaluación del DNI ni selfie (`GenerarUrlDeSubidaService`, `EvaluarCapturaDeDniService`, `ExtraerDatosDelDniService`, `ConfirmarDatosDelDniService`, `EntregarSelfieService`) permite avanzar si el titular no cuenta con un método de segundo factor confirmado (`409 Conflict`, problema `segundo-factor-no-verificado`).
- `SesionController` e `IniciarSesionService` verifican transparentemente el código 2FA presentado, devolviendo `tipoMetodo` en `DesafioAbierto` / `DesafioDto` y considerando únicamente métodos confirmados (`MetodoDeSegundoFactor::estaConfirmado`).
- En la interfaz de usuario React (`ayni-web`), la ruta `/registro/segundo-factor` implementa el paso 2 de 6 del onboarding con soporte completo para `APP_AUTENTICADORA` (QR + clave manual) y `CORREO_ELECTRONICO` (código de 6 dígitos y reenvío).

