# Listado de casos de prueba

> Generado con `infra/scripts/listar-casos-de-prueba.py` el 26-10-2026 sobre el commit `5673235`. **No se edita a mano**: se vuelve a generar. Anexo del [plan de pruebas](plan-de-pruebas.md).

## Resumen por componente y nivel

| Componente | Unitaria | Arquitectura | Integración | Aceptación (Gherkin) | Navegador (E2E) | Usabilidad | Total |
|---|---:|---:|---:|---:|---:|---:|---:|
| `ayni-identity-service` | 449 | 5 | 19 | 18 | 0 | 0 | **491** |
| `ayni-core-banking-service` | 143 | 5 | 4 | 13 | 0 | 0 | **165** |
| `ayni-notification-service` | 21 | 5 | 6 | 5 | 0 | 0 | **37** |
| `ayni-gateway` | 23 | 0 | 1 | 0 | 0 | 0 | **24** |
| `ayni-kyc-service` | 59 | 0 | 25 | 0 | 0 | 0 | **84** |
| `ayni-web` | 138 | 0 | 0 | 0 | 32 | 17 | **187** |
| **Total** | **833** | **15** | **55** | **36** | **32** | **17** | **988** |

Los escenarios Gherkin marcados como pendientes (1) documentan un criterio que el código todavía no cumple. Se cuentan como casos especificados, pero no se ejecutan.

## `ayni-identity-service`

| ID | Nivel | Caso | Archivo |
|---|---|---|---|
| CP-IDN-001 | Unitaria | la solicitud queda aprobada y el usuario pasa a ACTIVO | `AprobarSolicitudServiceTest.java` |
| CP-IDN-002 | Unitaria | anuncia la aprobación: es lo que abre la cuenta en core-banking | `AprobarSolicitudServiceTest.java` |
| CP-IDN-003 | Unitaria | una solicitud señuelo no se puede aprobar | `AprobarSolicitudServiceTest.java` |
| CP-IDN-004 | Unitaria | Una solicitud inexistente tampoco | `AprobarSolicitudServiceTest.java` |
| CP-IDN-005 | Unitaria | si el usuario ya no existe, no se anuncia nada | `AprobarSolicitudServiceTest.java` |
| CP-IDN-006 | Unitaria | si lo leido coincide con lo declarado, la solicitud pasa a DOCUMENTO_CARGADO | `ConfirmarDatosDelDniServiceTest.java` |
| CP-IDN-007 | Unitaria | el titular corrige un nombre mal leido del anverso y se acepta | `ConfirmarDatosDelDniServiceTest.java` |
| CP-IDN-008 | Unitaria | si lo confirmado no coincide con lo declarado, va a revision manual | `ConfirmarDatosDelDniServiceTest.java` |
| CP-IDN-009 | Unitaria | cambiar el numero de una lectura del MRZ verificada deriva aunque coincida con lo declarado | `ConfirmarDatosDelDniServiceTest.java` |
| CP-IDN-010 | Unitaria | sin una lectura previa no hay nada que confirmar | `ConfirmarDatosDelDniServiceTest.java` |
| CP-IDN-011 | Unitaria | un numero corregido que no son ocho digitos se rechaza como dato invalido | `ConfirmarDatosDelDniServiceTest.java` |
| CP-IDN-012 | Unitaria | una solicitud ya derivada no admite confirmacion | `ConfirmarDatosDelDniServiceTest.java` |
| CP-IDN-013 | Unitaria | con la app autenticadora no se envia nada y el codigo correcto emite el token de esta operacion | `ConfirmarOperacionServiceTest.java` |
| CP-IDN-014 | Unitaria | con el correo como metodo se envia el codigo y se verifica contra ese desafio | `ConfirmarOperacionServiceTest.java` |
| CP-IDN-015 | Unitaria | tres codigos incorrectos agotan la confirmacion y cada fallo cuenta para la pausa del ingreso | `ConfirmarOperacionServiceTest.java` |
| CP-IDN-016 | Unitaria | abrir confirmaciones nuevas no permite seguir probando: al sexto fallo se pausa todo | `ConfirmarOperacionServiceTest.java` |
| CP-IDN-017 | Unitaria | una confirmacion se usa una vez, caduca a los 5 minutos y no sirve a otro usuario | `ConfirmarOperacionServiceTest.java` |
| CP-IDN-018 | Unitaria | un codigo mal formado cuenta como fallo, sin romper | `ConfirmarOperacionServiceTest.java` |
| CP-IDN-019 | Unitaria | sin un segundo factor confirmado no se puede confirmar nada | `ConfirmarOperacionServiceTest.java` |
| CP-IDN-020 | Unitaria | la selfie queda registrada con su hash y la solicitud pasa a revision con aviso al titular | `EntregarSelfieServiceTest.java` |
| CP-IDN-021 | Unitaria | una clave de otro lado o de otra solicitud no se acepta como selfie | `EntregarSelfieServiceTest.java` |
| CP-IDN-022 | Unitaria | una selfie que no se llego a subir no se registra | `EntregarSelfieServiceTest.java` |
| CP-IDN-023 | Unitaria | una solicitud sin titular no existe para este paso | `EntregarSelfieServiceTest.java` |
| CP-IDN-024 | Unitaria | una foto aceptada queda registrada con su referencia, hash, tipo y tamano | `EvaluarCapturaDeDniServiceTest.java` |
| CP-IDN-025 | Unitaria | escenario 2: algo que no es un DNI se rechaza, no se guarda y se borra del almacen | `EvaluarCapturaDeDniServiceTest.java` |
| CP-IDN-026 | Unitaria | escenario 3: la calidad insuficiente devuelve el motivo concreto | `EvaluarCapturaDeDniServiceTest.java` |
| CP-IDN-027 | Unitaria | escenario 4: el tercer rechazo del mismo lado deriva a revision manual | `EvaluarCapturaDeDniServiceTest.java` |
| CP-IDN-028 | Unitaria | escenario 4: una cuarta captura sobre una solicitud derivada ni siquiera se evalua | `EvaluarCapturaDeDniServiceTest.java` |
| CP-IDN-029 | Unitaria | escenario 5: con kyc-service caido, la solicitud se difiere a revision y la foto se conserva | `EvaluarCapturaDeDniServiceTest.java` |
| CP-IDN-030 | Unitaria | una clave de otra solicitud o de otro lado no se acepta como propia | `EvaluarCapturaDeDniServiceTest.java` |
| CP-IDN-031 | Unitaria | una clave que no se llego a subir no se evalua | `EvaluarCapturaDeDniServiceTest.java` |
| CP-IDN-032 | Unitaria | una foto de mas de 5 MB se borra y no se evalua, aunque haya esquivado la politica | `EvaluarCapturaDeDniServiceTest.java` |
| CP-IDN-033 | Unitaria | un senuelo o una solicitud inexistente no evaluan nada | `EvaluarCapturaDeDniServiceTest.java` |
| CP-IDN-034 | Unitaria | la selfie no es un lado del DNI | `EvaluarCapturaDeDniServiceTest.java` |
| CP-IDN-035 | Unitaria | lee los datos, los guarda como lectura del OCR y los devuelve para confirmarlos | `ExtraerDatosDelDniServiceTest.java` |
| CP-IDN-036 | Unitaria | si el OCR no lee el documento, cuenta un intento del reverso y pide repetirlo | `ExtraerDatosDelDniServiceTest.java` |
| CP-IDN-037 | Unitaria | si una foto cambio despues de evaluarse, no se lee: va a revision manual | `ExtraerDatosDelDniServiceTest.java` |
| CP-IDN-038 | Unitaria | con kyc-service caido se difiere, sin gastar intentos | `ExtraerDatosDelDniServiceTest.java` |
| CP-IDN-039 | Unitaria | sin las dos fotos aceptadas no hay nada que leer | `ExtraerDatosDelDniServiceTest.java` |
| CP-IDN-040 | Unitaria | Genera correctamente un código OTP de 6 dígitos con hash SHA-256 y lo despacha por correo | `GenerarDesafioCodigoServiceTest.java` |
| CP-IDN-041 | Unitaria | Despacha el código por SMS cuando el método elegido es SMS | `GenerarDesafioCodigoServiceTest.java` |
| CP-IDN-042 | Unitaria | No despacha nada para APP_AUTENTICADORA: el código lo genera la app, no este servicio | `GenerarDesafioCodigoServiceTest.java` |
| CP-IDN-043 | Unitaria | Invalida desafío previo no verificado al generar uno nuevo | `GenerarDesafioCodigoServiceTest.java` |
| CP-IDN-044 | Unitaria | No reinvalida un desafío previo que ya fue verificado | `GenerarDesafioCodigoServiceTest.java` |
| CP-IDN-045 | Unitaria | pide al almacen una URL con una clave de objeto que incluye la solicitud y el tipo | `GenerarUrlDeSubidaServiceTest.java` |
| CP-IDN-046 | Unitaria | cada llamada genera una clave de objeto distinta, aunque sea el mismo tipo | `GenerarUrlDeSubidaServiceTest.java` |
| CP-IDN-047 | Unitaria | una solicitud inexistente no genera ninguna URL | `GenerarUrlDeSubidaServiceTest.java` |
| CP-IDN-048 | Unitaria | una solicitud señuelo (sin titular) tampoco genera URL | `GenerarUrlDeSubidaServiceTest.java` |
| CP-IDN-049 | Unitaria | una extension nula, vacia o en blanco cae por defecto a jpg | `GenerarUrlDeSubidaServiceTest.java` |
| CP-IDN-050 | Unitaria | limpia mayusculas, espacios y caracteres invalidos antes de validar la extension | `GenerarUrlDeSubidaServiceTest.java` |
| CP-IDN-051 | Unitaria | una extension fuera del catalogo permitido se rechaza, PDF incluido | `GenerarUrlDeSubidaServiceTest.java` |
| CP-IDN-052 | Unitaria | el primer y el segundo fallo dejan reintentar, con los intentos que quedan | `GestionarFalloDeVerificacionKycServiceTest.java` |
| CP-IDN-053 | Unitaria | el tercer fallo del mismo lado agota el limite, deriva y avisa al titular | `GestionarFalloDeVerificacionKycServiceTest.java` |
| CP-IDN-054 | Unitaria | los intentos se cuentan por lado: dos del anverso y dos del reverso no derivan | `GestionarFalloDeVerificacionKycServiceTest.java` |
| CP-IDN-055 | Unitaria | un fallo sobre una solicitud ya derivada no suma ni vuelve a avisar (escenario 4) | `GestionarFalloDeVerificacionKycServiceTest.java` |
| CP-IDN-056 | Unitaria | la caida de kyc-service deriva de inmediato, sin gastar intentos, y avisa al titular | `GestionarFalloDeVerificacionKycServiceTest.java` |
| CP-IDN-057 | Unitaria | una discrepancia deriva una sola vez aunque se repita | `GestionarFalloDeVerificacionKycServiceTest.java` |
| CP-IDN-058 | Unitaria | un senuelo sin titular no genera ningun aviso | `GestionarFalloDeVerificacionKycServiceTest.java` |
| CP-IDN-059 | Unitaria | el primer paso no entrega ningún token: solo el desafío | `IniciarSesionServiceTest.java` |
| CP-IDN-060 | Unitaria | Emite un token de quince minutos yuno de renovacion de siete dias | `IniciarSesionServiceTest.java` |
| CP-IDN-061 | Unitaria | del token de renovación solo se guarda su huella | `IniciarSesionServiceTest.java` |
| CP-IDN-062 | Unitaria | Registra el acceso en la pista de auditoria | `IniciarSesionServiceTest.java` |
| CP-IDN-063 | Unitaria | el desafío se canjea una sola vez | `IniciarSesionServiceTest.java` |
| CP-IDN-064 | Unitaria | Un desafio caducado no sirve | `IniciarSesionServiceTest.java` |
| CP-IDN-065 | Unitaria | el correo desconocido y la contraseña mala dan el mismo error | `IniciarSesionServiceTest.java` |
| CP-IDN-066 | Unitaria | con un correo desconocido también se deriva la contraseña | `IniciarSesionServiceTest.java` |
| CP-IDN-067 | Unitaria | No emite ningun token | `IniciarSesionServiceTest.java` |
| CP-IDN-068 | Unitaria | Incrementa el contador de fallos | `IniciarSesionServiceTest.java` |
| CP-IDN-069 | Unitaria | el intento sobre un correo inexistente también se audita | `IniciarSesionServiceTest.java` |
| CP-IDN-070 | Unitaria | Al sexto intento se pausa el ingreso | `IniciarSesionServiceTest.java` |
| CP-IDN-071 | Unitaria | con la contraseña correcta tampoco se entra estando bloqueado | `IniciarSesionServiceTest.java` |
| CP-IDN-072 | Unitaria | Avisa al titular una sola vez | `IniciarSesionServiceTest.java` |
| CP-IDN-073 | Unitaria | Registra el bloqueo como evento de seguridad | `IniciarSesionServiceTest.java` |
| CP-IDN-074 | Unitaria | un código erróneo también cuenta como intento fallido | `IniciarSesionServiceTest.java` |
| CP-IDN-075 | Unitaria | Larenovacion rota el token | `IniciarSesionServiceTest.java` |
| CP-IDN-076 | Unitaria | usar un token ya rotado invalida la familia entera | `IniciarSesionServiceTest.java` |
| CP-IDN-077 | Unitaria | Avisa al titular del incidente | `IniciarSesionServiceTest.java` |
| CP-IDN-078 | Unitaria | Un token desconocido no abre nada | `IniciarSesionServiceTest.java` |
| CP-IDN-079 | Unitaria | cerrar la sesion invalida la familia: la cookie ya no renueva | `IniciarSesionServiceTest.java` |
| CP-IDN-080 | Unitaria | cerrar sin token, o con uno desconocido, no hace nada ni falla | `IniciarSesionServiceTest.java` |
| CP-IDN-081 | Unitaria | quien está pendiente de verificación sí puede entrar | `IniciarSesionServiceTest.java` |
| CP-IDN-082 | Unitaria | quien está en revisión manual también, para poder ver en qué quedó | `IniciarSesionServiceTest.java` |
| CP-IDN-083 | Unitaria | una cuenta bloqueada no entra aunque la contraseña sea correcta | `IniciarSesionServiceTest.java` |
| CP-IDN-084 | Unitaria | el bloqueo se comprueba después de la contraseña, no antes | `IniciarSesionServiceTest.java` |
| CP-IDN-085 | Unitaria | quien no lo tiene recibe el URI para escanear el QR | `IniciarSesionServiceTest.java` |
| CP-IDN-086 | Unitaria | no queda confirmado hasta que se teclea un código válido | `IniciarSesionServiceTest.java` |
| CP-IDN-087 | Unitaria | reintentar reutiliza el secreto en vez de generar otro | `IniciarSesionServiceTest.java` |
| CP-IDN-088 | Unitaria | una vez confirmado deja de entregarse el URI | `IniciarSesionServiceTest.java` |
| CP-IDN-089 | Unitaria | verificarSegundoFactor con OTP válido inicia sesión exitosamente | `IniciarSesionServiceTest.java` |
| CP-IDN-090 | Unitaria | verificarSegundoFactor con OTP erróneo incrementa intentos y lanza excepción | `IniciarSesionServiceTest.java` |
| CP-IDN-091 | Unitaria | verificarSegundoFactor con OTP expirado o máximo intentos lanza excepción | `IniciarSesionServiceTest.java` |
| CP-IDN-092 | Unitaria | verificarSegundoFactor con cuenta bloqueada lanza CuentaBloqueadaException | `IniciarSesionServiceTest.java` |
| CP-IDN-093 | Unitaria | con Correo Electrónico elegido, el primer paso abre un desafío OTP y lo despacha | `IniciarSesionServiceTest.java` |
| CP-IDN-094 | Unitaria | sin ningún método OTP elegido, el login sigue el camino de App Autenticadora de siempre | `IniciarSesionServiceTest.java` |
| CP-IDN-095 | Unitaria | Verificar segundo factor sin repositorio de otp usa totp | `IniciarSesionServiceTest.java` |
| CP-IDN-096 | Unitaria | la solicitud queda RECHAZADA con su motivo | `RechazarSolicitudServiceTest.java` |
| CP-IDN-097 | Unitaria | anuncia el rechazo con el correo, el nombre de pila y el motivo: notification no conoce a Ana | `RechazarSolicitudServiceTest.java` |
| CP-IDN-098 | Unitaria | el usuario no se activa ni se bloquea: sigue pendiente de verificación | `RechazarSolicitudServiceTest.java` |
| CP-IDN-099 | Unitaria | una solicitud aprobada ya abrió una cuenta: no se puede rechazar | `RechazarSolicitudServiceTest.java` |
| CP-IDN-100 | Unitaria | rechazarla dos veces no manda dos correos | `RechazarSolicitudServiceTest.java` |
| CP-IDN-101 | Unitaria | un señuelo, una solicitud inexistente o una sin usuario se tratan como inexistentes | `RechazarSolicitudServiceTest.java` |
| CP-IDN-102 | Unitaria | con una cuenta existente se emite un enlace de 30 minutos y se envia por correo | `RecuperarContrasenaServiceTest.java` |
| CP-IDN-103 | Unitaria | con un correo desconocido no se envia nada, pero el intento queda en la auditoria | `RecuperarContrasenaServiceTest.java` |
| CP-IDN-104 | Unitaria | el correo se compara sin importar mayusculas ni espacios | `RecuperarContrasenaServiceTest.java` |
| CP-IDN-105 | Unitaria | a una cuenta bloqueada por el banco no se le envia enlace | `RecuperarContrasenaServiceTest.java` |
| CP-IDN-106 | Unitaria | solo vale el ultimo enlace: pedir otro anula los anteriores | `RecuperarContrasenaServiceTest.java` |
| CP-IDN-107 | Unitaria | como maximo tres enlaces por hora: el cuarto se ignora en silencio | `RecuperarContrasenaServiceTest.java` |
| CP-IDN-108 | Unitaria | un enlace vigente se valida sin gastarse | `RecuperarContrasenaServiceTest.java` |
| CP-IDN-109 | Unitaria | un enlace caducado no sirve | `RecuperarContrasenaServiceTest.java` |
| CP-IDN-110 | Unitaria | un enlace inventado, vacio o nulo no sirve | `RecuperarContrasenaServiceTest.java` |
| CP-IDN-111 | Unitaria | un enlace ya usado no sirve una segunda vez | `RecuperarContrasenaServiceTest.java` |
| CP-IDN-112 | Unitaria | si otra peticion gasto el enlace un instante antes, esta se rechaza | `RecuperarContrasenaServiceTest.java` |
| CP-IDN-113 | Unitaria | la contrasena nueva cumple la misma politica que el registro, y el enlace sigue sirviendo | `RecuperarContrasenaServiceTest.java` |
| CP-IDN-114 | Unitaria | se guarda derivada, se cierran todas las sesiones, se limpia el contador y se audita | `RecuperarContrasenaServiceTest.java` |
| CP-IDN-115 | Unitaria | recuperar la contrasena no recupera la cuenta: el estado y el segundo factor no cambian | `RecuperarContrasenaServiceTest.java` |
| CP-IDN-116 | Unitaria | si el usuario ya no existe, el enlace no sirve | `RecuperarContrasenaServiceTest.java` |
| CP-IDN-117 | Unitaria | Crea el usuario en estado pendiente de verificacion | `RegistrarVisitanteServiceTest.java` |
| CP-IDN-118 | Unitaria | guarda la contrasena derivada, nunca en claro | `RegistrarVisitanteServiceTest.java` |
| CP-IDN-119 | Unitaria | Normaliza el correo antes de guardarlo | `RegistrarVisitanteServiceTest.java` |
| CP-IDN-120 | Unitaria | Enva el correo de bienvenida | `RegistrarVisitanteServiceTest.java` |
| CP-IDN-121 | Unitaria | abre una solicitud real ligada al usuario y la devuelve | `RegistrarVisitanteServiceTest.java` |
| CP-IDN-122 | Unitaria | registra el consentimiento con su momento y su version | `RegistrarVisitanteServiceTest.java` |
| CP-IDN-123 | Unitaria | No crea un usuario duplicado | `RegistrarVisitanteServiceTest.java` |
| CP-IDN-124 | Unitaria | la respuesta es indistinguible de un registro correcto | `RegistrarVisitanteServiceTest.java` |
| CP-IDN-125 | Unitaria | el mensaje no afirma ni niega que la cuenta exista | `RegistrarVisitanteServiceTest.java` |
| CP-IDN-126 | Unitaria | deriva la contrasena igualmente, o el cronometro delataria la cuenta | `RegistrarVisitanteServiceTest.java` |
| CP-IDN-127 | Unitaria | avisa al titular legitimo, que es el unico con derecho a saberlo | `RegistrarVisitanteServiceTest.java` |
| CP-IDN-128 | Unitaria | persiste el senuelo: un identificador inventado se delataria despues | `RegistrarVisitanteServiceTest.java` |
| CP-IDN-129 | Unitaria | No crea el usuario | `RegistrarVisitanteServiceTest.java` |
| CP-IDN-130 | Unitaria | indica que requisito concreto incumple | `RegistrarVisitanteServiceTest.java` |
| CP-IDN-131 | Unitaria | falla antes de tocar el repositorio: no gasta una consulta | `RegistrarVisitanteServiceTest.java` |
| CP-IDN-132 | Unitaria | No crea el usuario | `RegistrarVisitanteServiceTest.java` |
| CP-IDN-133 | Unitaria | Rechaza un correo mal formado | `RegistrarVisitanteServiceTest.java` |
| CP-IDN-134 | Unitaria | Rechaza un celular que no es movil peruano | `RegistrarVisitanteServiceTest.java` |
| CP-IDN-135 | Unitaria | se guarda en la solicitud, ya normalizada | `RegistrarVisitanteServiceTest.java` |
| CP-IDN-136 | Unitaria | el senuelo no guarda datos personales de una cuenta que no se creo | `RegistrarVisitanteServiceTest.java` |
| CP-IDN-137 | Unitaria | un menor de edad no puede abrir una cuenta a nombre propio | `RegistrarVisitanteServiceTest.java` |
| CP-IDN-138 | Unitaria | un documento invalido falla igual exista o no el correo | `RegistrarVisitanteServiceTest.java` |
| CP-IDN-139 | Unitaria | la excepcion de una defensa no revierte lo que la defensa escribio | `RollbackDeLasDefensasTest.java` |
| CP-IDN-140 | Unitaria | un fallo inesperado sigue revirtiendo la transaccion entera | `RollbackDeLasDefensasTest.java` |
| CP-IDN-141 | Unitaria | Seleccionar metodo CORREO_ELECTRONICO registra un nuevo metodo sin secreto TOTP | `SeleccionarSegundoFactorServiceTest.java` |
| CP-IDN-142 | Unitaria | Seleccionar metodo APP_AUTENTICADORA genera y guarda un nuevo secreto TOTP | `SeleccionarSegundoFactorServiceTest.java` |
| CP-IDN-143 | Unitaria | Seleccionar un metodo ya existente devuelve el metodo previamente registrado sin duplicarlo | `SeleccionarSegundoFactorServiceTest.java` |
| CP-IDN-144 | Unitaria | Verifica contacto exitosamente y confirma el método 2FA (nuevo método o existente) | `VerificarContactoRegistroServiceTest.java` |
| CP-IDN-145 | Unitaria | Verifica contacto cuando el método 2FA ya existía en el repositorio | `VerificarContactoRegistroServiceTest.java` |
| CP-IDN-146 | Unitaria | Lanza CodigoDesafioInvalidoException si no hay desafío pendiente | `VerificarContactoRegistroServiceTest.java` |
| CP-IDN-147 | Unitaria | Lanza DesafioExpiradoException si el desafío ya expiró | `VerificarContactoRegistroServiceTest.java` |
| CP-IDN-148 | Unitaria | Lanza MaximoIntentosDesafioExcedidoException si ya había alcanzado el máximo de intentos | `VerificarContactoRegistroServiceTest.java` |
| CP-IDN-149 | Unitaria | Lanza MaximoIntentosDesafioExcedidoException al fallar el 3er intento | `VerificarContactoRegistroServiceTest.java` |
| CP-IDN-150 | Unitaria | Lanza CodigoDesafioInvalidoException ante código erróneo | `VerificarContactoRegistroServiceTest.java` |
| CP-IDN-151 | Unitaria | Verifica exitosamente con el código correcto | `VerificarDesafioCodigoServiceTest.java` |
| CP-IDN-152 | Unitaria | Lanza CodigoDesafioInvalidoException ante un código incorrecto | `VerificarDesafioCodigoServiceTest.java` |
| CP-IDN-153 | Unitaria | Lanza MaximoIntentosDesafioExcedidoException al superar 3 intentos fallidos | `VerificarDesafioCodigoServiceTest.java` |
| CP-IDN-154 | Unitaria | Lanza DesafioExpiradoException si han pasado más de 10 minutos | `VerificarDesafioCodigoServiceTest.java` |
| CP-IDN-155 | Unitaria | Lanza CodigoDesafioInvalidoException si no existe el desafioId | `VerificarDesafioCodigoServiceTest.java` |
| CP-IDN-156 | Unitaria | Lanza MaximoIntentosDesafioExcedidoException si este fallo es el 3er intento | `VerificarDesafioCodigoServiceTest.java` |
| CP-IDN-157 | Unitaria | Lanza CodigoDesafioInvalidoException si el desafío ya fue verificado previamente | `VerificarDesafioCodigoServiceTest.java` |
| CP-IDN-158 | Arquitectura | El dominio no conoce el framework | `ArquitecturaHexagonalTest.java` |
| CP-IDN-159 | Arquitectura | El dominio no depende de la infraestructura | `ArquitecturaHexagonalTest.java` |
| CP-IDN-160 | Arquitectura | Las capas se respetan | `ArquitecturaHexagonalTest.java` |
| CP-IDN-161 | Arquitectura | Los puertos de salida se nombran port | `ArquitecturaHexagonalTest.java` |
| CP-IDN-162 | Arquitectura | Los casos de uso se nombran use case | `ArquitecturaHexagonalTest.java` |
| CP-IDN-163 | Unitaria | Acepta un movil peruano valido | `CelularTest.java` |
| CP-IDN-164 | Unitaria | normaliza los separadores que la gente escribe | `CelularTest.java` |
| CP-IDN-165 | Unitaria | Rechaza numeros que no son moviles peruanos | `CelularTest.java` |
| CP-IDN-166 | Unitaria | Rechaza nulo | `CelularTest.java` |
| CP-IDN-167 | Unitaria | el enmascarado deja los tres ultimos digitos para que la persona se reconozca | `CelularTest.java` |
| CP-IDN-168 | Unitaria | los cinco primeros fallos se cuentan pero no bloquean | `ControlDeAccesoTest.java` |
| CP-IDN-169 | Unitaria | el sexto fallo pausa el ingreso | `ControlDeAccesoTest.java` |
| CP-IDN-170 | Unitaria | cada fallo posterior duplica la espera | `ControlDeAccesoTest.java` |
| CP-IDN-171 | Unitaria | la espera tiene techo: si no, fallar aposta dejaria fuera al titular | `ControlDeAccesoTest.java` |
| CP-IDN-172 | Unitaria | El bloqueo se levanta solo | `ControlDeAccesoTest.java` |
| CP-IDN-173 | Unitaria | un acierto borra el historial: cuentan los fallos consecutivos | `ControlDeAccesoTest.java` |
| CP-IDN-174 | Unitaria | solo el fallo que provoca el bloqueo avisa al titular | `ControlDeAccesoTest.java` |
| CP-IDN-175 | Unitaria | Rechaza un contador negativo al reconstituir | `ControlDeAccesoTest.java` |
| CP-IDN-176 | Unitaria | No filtra nada en el to string | `ControlDeAccesoTest.java` |
| CP-IDN-177 | Unitaria | pasa el correo a minusculas para que la unicidad signifique algo | `CorreoElectronicoTest.java` |
| CP-IDN-178 | Unitaria | elimina los espacios que la gente pega sin darse cuenta | `CorreoElectronicoTest.java` |
| CP-IDN-179 | Unitaria | dos correos que solo difieren en mayusculas son el mismo | `CorreoElectronicoTest.java` |
| CP-IDN-180 | Unitaria | Acepta direcciones validas | `CorreoElectronicoTest.java` |
| CP-IDN-181 | Unitaria | Rechaza direcciones invalidas | `CorreoElectronicoTest.java` |
| CP-IDN-182 | Unitaria | Rechaza nulo | `CorreoElectronicoTest.java` |
| CP-IDN-183 | Unitaria | rechaza direcciones que superan el limite del RFC 5321 | `CorreoElectronicoTest.java` |
| CP-IDN-184 | Unitaria | Oculta el interior de la parte local | `CorreoElectronicoTest.java` |
| CP-IDN-185 | Unitaria | oculta la parte local entera cuando es demasiado corta para revelar nada | `CorreoElectronicoTest.java` |
| CP-IDN-186 | Unitaria | la mascara mide siempre lo mismo: la longitud tambien es informacion | `CorreoElectronicoTest.java` |
| CP-IDN-187 | Unitaria | El enmascarado nunca contiene la parte local completa | `CorreoElectronicoTest.java` |
| CP-IDN-188 | Unitaria | el numero son exactamente ocho digitos | `DatosDelDniTest.java` |
| CP-IDN-189 | Unitaria | el sexo es M o F, y la fecha de emision no puede ser anterior al nacimiento | `DatosDelDniTest.java` |
| CP-IDN-190 | Unitaria | coincide con lo declarado sin distinguir mayusculas, tildes ni espacios de mas | `DatosDelDniTest.java` |
| CP-IDN-191 | Unitaria | coincide si se declaro solo parte del nombre que figura en el DNI | `DatosDelDniTest.java` |
| CP-IDN-192 | Unitaria | no coincide si cambia el numero, el nombre, la fecha o el tipo de documento declarado | `DatosDelDniTest.java` |
| CP-IDN-193 | Unitaria | contradice al MRZ solo si cambia el numero o la fecha de nacimiento | `DatosDelDniTest.java` |
| CP-IDN-194 | Unitaria | no deja rastro en los logs: solo los cuatro ultimos digitos | `DatosDelDniTest.java` |
| CP-IDN-195 | Unitaria | confirmar sin reescribir el numero conserva el que se leyo | `DatosDelDniTest.java` |
| CP-IDN-196 | Unitaria | DesafioPorCodigo se genera con 10 minutos de vigencia y 0 intentos | `DesafioPorCodigoTest.java` |
| CP-IDN-197 | Unitaria | DesafioPorCodigo expira transcurridos 10 minutos | `DesafioPorCodigoTest.java` |
| CP-IDN-198 | Unitaria | DesafioPorCodigo registra intentos fallidos y detecta el máximo de 3 intentos | `DesafioPorCodigoTest.java` |
| CP-IDN-199 | Unitaria | DesafioPorCodigo se marca como verificado y previene modificaciones posteriores | `DesafioPorCodigoTest.java` |
| CP-IDN-200 | Unitaria | Validaciones de nulos, equals, hashCode y toString | `DesafioPorCodigoTest.java` |
| CP-IDN-201 | Unitaria | Acepta el valor en cualquier caja | `IdentidadDeclaradaTest.java` |
| CP-IDN-202 | Unitaria | el mensaje de error no menciona la clase Java: acaba en la respuesta HTTP | `IdentidadDeclaradaTest.java` |
| CP-IDN-203 | Unitaria | Rechaza el nulo | `IdentidadDeclaradaTest.java` |
| CP-IDN-204 | Unitaria | Acepta un dni de ocho digitos | `IdentidadDeclaradaTest.java` |
| CP-IDN-205 | Unitaria | limpia espacios y guiones antes de validar | `IdentidadDeclaradaTest.java` |
| CP-IDN-206 | Unitaria | Pasa amayusculas el carne de extranjeria | `IdentidadDeclaradaTest.java` |
| CP-IDN-207 | Unitaria | Rechaza lo que no es un dni | `IdentidadDeclaradaTest.java` |
| CP-IDN-208 | Unitaria | Rechaza el nulo | `IdentidadDeclaradaTest.java` |
| CP-IDN-209 | Unitaria | enmascarado deja solo los cuatro ultimos digitos | `IdentidadDeclaradaTest.java` |
| CP-IDN-210 | Unitaria | toString no filtra el numero: acaba en cualquier traza de excepcion | `IdentidadDeclaradaTest.java` |
| CP-IDN-211 | Unitaria | acepta a quien cumple dieciocho anos justo hoy | `IdentidadDeclaradaTest.java` |
| CP-IDN-212 | Unitaria | rechaza a quien los cumple manana | `IdentidadDeclaradaTest.java` |
| CP-IDN-213 | Unitaria | Rechaza una fecha futura | `IdentidadDeclaradaTest.java` |
| CP-IDN-214 | Unitaria | rechaza un ano absurdo: casi siempre es un error de tecleo | `IdentidadDeclaradaTest.java` |
| CP-IDN-215 | Unitaria | el nulo es IllegalArgumentException, para que salga un 400 y no un 500 | `IdentidadDeclaradaTest.java` |
| CP-IDN-216 | Unitaria | Dos fechas iguales son el mismo valor | `IdentidadDeclaradaTest.java` |
| CP-IDN-217 | Unitaria | No filtra la fecha | `IdentidadDeclaradaTest.java` |
| CP-IDN-218 | Unitaria | colapsa los espacios interiores, o la comparacion con el OCR fallaria | `IdentidadDeclaradaTest.java` |
| CP-IDN-219 | Unitaria | Rechaza nombres vacios | `IdentidadDeclaradaTest.java` |
| CP-IDN-220 | Unitaria | Rechaza apellidos vacios | `IdentidadDeclaradaTest.java` |
| CP-IDN-221 | Unitaria | Rechaza nombres demasiado largos | `IdentidadDeclaradaTest.java` |
| CP-IDN-222 | Unitaria | Exige documento yfecha | `IdentidadDeclaradaTest.java` |
| CP-IDN-223 | Unitaria | toString no filtra ningun dato personal | `IdentidadDeclaradaTest.java` |
| CP-IDN-224 | Unitaria | MetodoDeSegundoFactor se inscribe correctamente sin estar confirmado | `MetodoDeSegundoFactorTest.java` |
| CP-IDN-225 | Unitaria | MetodoDeSegundoFactor se confirma correctamente | `MetodoDeSegundoFactorTest.java` |
| CP-IDN-226 | Unitaria | MetodoDeSegundoFactor valida nulos en el constructor | `MetodoDeSegundoFactorTest.java` |
| CP-IDN-227 | Unitaria | equals, hashCode, toString y reconstituir funcionan | `MetodoDeSegundoFactorTest.java` |
| CP-IDN-228 | Unitaria | la huella coincide con la que calcula core-banking | `OperacionAConfirmarTest.java` |
| CP-IDN-229 | Unitaria | rechaza importes no positivos y monedas mal escritas | `OperacionAConfirmarTest.java` |
| CP-IDN-230 | Unitaria | Un token reciennacido esta vigente | `RefreshTokenTest.java` |
| CP-IDN-231 | Unitaria | rotar consume el actual y devuelve un sucesor de la misma familia | `RefreshTokenTest.java` |
| CP-IDN-232 | Unitaria | el sucesor hereda la caducidad en lugar de reiniciarla | `RefreshTokenTest.java` |
| CP-IDN-233 | Unitaria | presentar un token ya consumido delata que existe una copia | `RefreshTokenTest.java` |
| CP-IDN-234 | Unitaria | Un token caducado no se puede rotar | `RefreshTokenTest.java` |
| CP-IDN-235 | Unitaria | la caducidad se comprueba antes que nada excepto la reutilizacion | `RefreshTokenTest.java` |
| CP-IDN-236 | Unitaria | Dos instancias con el mismo id son el mismo token | `RefreshTokenTest.java` |
| CP-IDN-237 | Unitaria | toString no expone la huella: identifica una sesion activa | `RefreshTokenTest.java` |
| CP-IDN-238 | Unitaria | reconoce png y webp sin importar mayusculas o minusculas | `TipoDeDocumentoKycTest.java` |
| CP-IDN-239 | Unitaria | cualquier extension fuera del catalogo propio cae en image/jpeg | `TipoDeDocumentoKycTest.java` |
| CP-IDN-240 | Unitaria | el prefijo del objeto es el tipo en minusculas, que es lo que comprueba kyc-service | `TipoDeDocumentoKycTest.java` |
| CP-IDN-241 | Unitaria | vence a los 30 minutos de emitirse, como exige HU-21 | `TokenDeRecuperacionTest.java` |
| CP-IDN-242 | Unitaria | usarlo devuelve el token marcado con la hora de uso | `TokenDeRecuperacionTest.java` |
| CP-IDN-243 | Unitaria | un token ya usado no se puede volver a usar | `TokenDeRecuperacionTest.java` |
| CP-IDN-244 | Unitaria | un token caducado no se puede usar | `TokenDeRecuperacionTest.java` |
| CP-IDN-245 | Unitaria | se reconstituye tal cual y no muestra la huella en las trazas | `TokenDeRecuperacionTest.java` |
| CP-IDN-246 | Unitaria | un usuario recien registrado queda PENDIENTE_VERIFICACION | `UsuarioTest.java` |
| CP-IDN-247 | Unitaria | un usuario recien registrado no puede operar: existir no es poder mover dinero | `UsuarioTest.java` |
| CP-IDN-248 | Unitaria | Conserva los datos con los que se registro | `UsuarioTest.java` |
| CP-IDN-249 | Unitaria | HU-21: cambiar la contrasena sustituye la credencial y conserva todo lo demas | `UsuarioTest.java` |
| CP-IDN-250 | Unitaria | Activar lo deja operativo | `UsuarioTest.java` |
| CP-IDN-251 | Unitaria | un usuario en revision manual puede acabar activandose | `UsuarioTest.java` |
| CP-IDN-252 | Unitaria | no se puede reactivar a un bloqueado: seria desbloquear sin pasar por nadie | `UsuarioTest.java` |
| CP-IDN-253 | Unitaria | No se puede derivar arevision aquien ya esta activo | `UsuarioTest.java` |
| CP-IDN-254 | Unitaria | la transicion devuelve una instancia nueva y no muta la anterior | `UsuarioTest.java` |
| CP-IDN-255 | Unitaria | La excepcion dice de donde adonde se intento ir | `UsuarioTest.java` |
| CP-IDN-256 | Unitaria | dos instancias con el mismo id son la misma persona aunque difiera el estado | `UsuarioTest.java` |
| CP-IDN-257 | Unitaria | Usuarios con id distinto no son iguales | `UsuarioTest.java` |
| CP-IDN-258 | Unitaria | No es igual anada que no sea un usuario | `UsuarioTest.java` |
| CP-IDN-259 | Unitaria | toString no expone correo, celular ni derivacion de la contrasena | `UsuarioTest.java` |
| CP-IDN-260 | Unitaria | admite cualquier estado, porque la base de datos ya tiene usuarios ACTIVOS | `UsuarioTest.java` |
| CP-IDN-261 | Unitaria | Acepta una derivacion de argon2 | `ValoresDeIdentidadTest.java` |
| CP-IDN-262 | Unitaria | rechaza lo que no sea una derivacion de Argon2: ahi se detecta el fallo grave | `ValoresDeIdentidadTest.java` |
| CP-IDN-263 | Unitaria | Rechaza vacia ynula | `ValoresDeIdentidadTest.java` |
| CP-IDN-264 | Unitaria | toString oculta la derivacion, que es material para un ataque fuera de linea | `ValoresDeIdentidadTest.java` |
| CP-IDN-265 | Unitaria | guarda el momento, que es lo que exige demostrar la Ley N.o 29733 | `ValoresDeIdentidadTest.java` |
| CP-IDN-266 | Unitaria | no acepta significa no hay registro: escenario 4 de HU-01 | `ValoresDeIdentidadTest.java` |
| CP-IDN-267 | Unitaria | Exige momento yversion | `ValoresDeIdentidadTest.java` |
| CP-IDN-268 | Unitaria | solo ACTIVO puede operar | `ValoresDeIdentidadTest.java` |
| CP-IDN-269 | Unitaria | Enumera los requisitos incumplidos sin revelar la contrasena | `ValoresDeIdentidadTest.java` |
| CP-IDN-270 | Unitaria | no tiene sentido lanzarla sin incumplimientos | `ValoresDeIdentidadTest.java` |
| CP-IDN-271 | Unitaria | el comando lleva la contrasena en claro y su toString no la imprime | `ValoresDeIdentidadTest.java` |
| CP-IDN-272 | Unitaria | El comando exige sus tres campos de credencial | `ValoresDeIdentidadTest.java` |
| CP-IDN-273 | Unitaria | el resultado usa el mensaje neutro que no revela si el correo existe | `ValoresDeIdentidadTest.java` |
| CP-IDN-274 | Unitaria | El resultado exige sus tres campos | `ValoresDeIdentidadTest.java` |
| CP-IDN-275 | Unitaria | acepta una contrasena que cumple los cinco requisitos | `PoliticaDeContrasenaTest.java` |
| CP-IDN-276 | Unitaria | senala exactamente el requisito que falta | `PoliticaDeContrasenaTest.java` |
| CP-IDN-277 | Unitaria | devuelve todos los incumplimientos a la vez, no solo el primero | `PoliticaDeContrasenaTest.java` |
| CP-IDN-278 | Unitaria | una contrasena nula o vacia incumple todos los requisitos | `PoliticaDeContrasenaTest.java` |
| CP-IDN-279 | Unitaria | acepta exactamente doce caracteres: el limite es inclusivo | `PoliticaDeContrasenaTest.java` |
| CP-IDN-280 | Unitaria | el espacio no cuenta como simbolo | `PoliticaDeContrasenaTest.java` |
| CP-IDN-281 | Unitaria | la lista devuelta es inmutable: nadie la altera despues | `PoliticaDeContrasenaTest.java` |
| CP-IDN-282 | Unitaria | es una clase de utilidad y no se puede instanciar | `PoliticaDeContrasenaTest.java` |
| CP-IDN-283 | Unitaria | Cada requisito tiene un mensaje para la persona | `PoliticaDeContrasenaTest.java` |
| CP-IDN-284 | Unitaria | El cliente de SES tiene limite por intento y limite total | `ConfiguracionDeCorreoTest.java` |
| CP-IDN-285 | Unitaria | El contrato se presenta con su nombre y apunta al gateway, no a la red interna | `ConfiguracionDeLaDocumentacionTest.java` |
| CP-IDN-286 | Unitaria | Sin AYNI_API_DOCS, /v3/api-docs y Swagger UI estan apagados | `DocumentacionDeLaApiTest.java` |
| CP-IDN-287 | Unitaria | iniciar: 201 con el metodo; la operacion llega con la misma huella que calcula core | `ConfirmacionControllerTest.java` |
| CP-IDN-288 | Unitaria | sin la cabecera de usuario: 401; con un importe mal escrito: 400 | `ConfirmacionControllerTest.java` |
| CP-IDN-289 | Unitaria | verificar: 200 con el token; 422 con los intentos que quedan; 410 si ya no sirve; 409 sin 2FA | `ConfirmacionControllerTest.java` |
| CP-IDN-290 | Unitaria | los DTO no dejan el codigo ni el token en una traza | `ConfirmacionControllerTest.java` |
| CP-IDN-291 | Unitaria | responde 202 y no 201: el alta no termina aquí | `ControladoresDeIdentidadTest.java` |
| CP-IDN-292 | Unitaria | traslada los nueve campos al dominio, sin perder los de identidad | `ControladoresDeIdentidadTest.java` |
| CP-IDN-293 | Unitaria | el DTO no imprime ni un dato personal | `ControladoresDeIdentidadTest.java` |
| CP-IDN-294 | Unitaria | la mayoría de edad se comprueba también en el DTO | `ControladoresDeIdentidadTest.java` |
| CP-IDN-295 | Unitaria | una fecha ausente no duplica el mensaje de error | `ControladoresDeIdentidadTest.java` |
| CP-IDN-296 | Unitaria | devuelve el nombre de pila y el correo enmascarado, y nada más | `ControladoresDeIdentidadTest.java` |
| CP-IDN-297 | Unitaria | sin identidad declarada se saluda sin nombre, no se falla | `ControladoresDeIdentidadTest.java` |
| CP-IDN-298 | Unitaria | Un usuario desconocido es404 | `ControladoresDeIdentidadTest.java` |
| CP-IDN-299 | Unitaria | HU-03: la selfie entregada pasa a revision de un operador | `ControladoresDeIdentidadTest.java` |
| CP-IDN-300 | Unitaria | traslada la solicitud al caso de uso y devuelve el formulario firmado con su clave | `ControladoresDeIdentidadTest.java` |
| CP-IDN-301 | Unitaria | una solicitud inexistente se traduce a 404 | `ControladoresDeIdentidadTest.java` |
| CP-IDN-302 | Unitaria | una foto rechazada responde 200 con el motivo concreto y los intentos que quedan | `ControladoresDeIdentidadTest.java` |
| CP-IDN-303 | Unitaria | una foto aceptada no lleva motivo ni intentos | `ControladoresDeIdentidadTest.java` |
| CP-IDN-304 | Unitaria | la lectura muestra el numero enmascarado: la respuesta no exige sesion todavia | `ControladoresDeIdentidadTest.java` |
| CP-IDN-305 | Unitaria | una lectura diferida no lleva datos | `ControladoresDeIdentidadTest.java` |
| CP-IDN-306 | Unitaria | la confirmacion traslada los datos, con el numero vacio si no se corrigio | `ControladoresDeIdentidadTest.java` |
| CP-IDN-307 | Integración | POST /api/v1/segundo-factor/desafio/usuario/{usuarioId}/generar genera un nuevo desafío | `DesafioCodigoControllerTest.java` |
| CP-IDN-308 | Integración | POST /api/v1/segundo-factor/desafio/{desafioId}/verificar verifica código exitosamente | `DesafioCodigoControllerTest.java` |
| CP-IDN-309 | Integración | POST /api/v1/segundo-factor/desafio/{desafioId}/verificar retorna 400 cuando código es inválido | `DesafioCodigoControllerTest.java` |
| CP-IDN-310 | Unitaria | La validacion devuelve los errores ordenados ypor campo | `ManejadorDeErroresTest.java` |
| CP-IDN-311 | Unitaria | La politica de contrasena detalla cada requisito incumplido | `ManejadorDeErroresTest.java` |
| CP-IDN-312 | Unitaria | El consentimiento que falta se marca sobre su casilla | `ManejadorDeErroresTest.java` |
| CP-IDN-313 | Unitaria | las credenciales invalidas son 401 con el mensaje neutro | `ManejadorDeErroresTest.java` |
| CP-IDN-314 | Unitaria | nada de lo que se responde dice si el correo existe | `ManejadorDeErroresTest.java` |
| CP-IDN-315 | Unitaria | el bloqueo es 423 y lleva la espera restante para la cuenta atras | `ManejadorDeErroresTest.java` |
| CP-IDN-316 | Unitaria | la cuenta inhabilitada es 403: no se levanta sola | `ManejadorDeErroresTest.java` |
| CP-IDN-317 | Unitaria | la reutilizacion de un token responde igual que una sesion caducada | `ManejadorDeErroresTest.java` |
| CP-IDN-318 | Unitaria | Los objetos de valor que rechazan su entrada son400 | `ManejadorDeErroresTest.java` |
| CP-IDN-319 | Unitaria | Excepcion de codigo desafio invalido devuelve400 | `ManejadorDeErroresTest.java` |
| CP-IDN-320 | Unitaria | Excepcion de desafio expirado devuelve400 | `ManejadorDeErroresTest.java` |
| CP-IDN-321 | Unitaria | Excepcion de maximo intentos excedido devuelve400 | `ManejadorDeErroresTest.java` |
| CP-IDN-322 | Unitaria | un fallo no previsto no filtra el mensaje de la excepcion | `ManejadorDeErroresTest.java` |
| CP-IDN-323 | Unitaria | un cuerpo ilegible es 400, no 500, y no repite el mensaje interno de Jackson | `ManejadorDeErroresTest.java` |
| CP-IDN-324 | Unitaria | Toda respuesta lleva su tipo yla ruta que la provoco | `ManejadorDeErroresTest.java` |
| CP-IDN-325 | Integración | POST /api/v1/usuarios/{usuarioId}/segundo-factor/metodo devuelve 200 con el metodo seleccionado | `MetodoSegundoFactorControllerTest.java` |
| CP-IDN-326 | Unitaria | con la clave correcta aprueba la solicitud | `OperadorControllerTest.java` |
| CP-IDN-327 | Unitaria | con otra clave, sin clave o sin clave configurada responde 404 y no aprueba | `OperadorControllerTest.java` |
| CP-IDN-328 | Unitaria | AYNI-327 · con la clave correcta rechaza la solicitud con su motivo | `OperadorControllerTest.java` |
| CP-IDN-329 | Unitaria | AYNI-327 · sin la clave correcta responde 404 y no rechaza | `OperadorControllerTest.java` |
| CP-IDN-330 | Unitaria | AYNI-327 · una solicitud ya cerrada responde 409; una inexistente o señuelo, 404 | `OperadorControllerTest.java` |
| CP-IDN-331 | Unitaria | AYNI-327 · por HTTP: 202 con un motivo de la lista; 400 sin motivo o con uno inventado; 409 si ya se cerro | `OperadorControllerTest.java` |
| CP-IDN-332 | Unitaria | escenario 1: la misma respuesta, byte a byte, exista o no la cuenta | `RecuperacionControllerTest.java` |
| CP-IDN-333 | Unitaria | la respuesta no sale antes de la duracion minima | `RecuperacionControllerTest.java` |
| CP-IDN-334 | Unitaria | un correo vacio es un 400 de validacion | `RecuperacionControllerTest.java` |
| CP-IDN-335 | Unitaria | validar un enlace vigente responde 204 y uno que no sirve 410 | `RecuperacionControllerTest.java` |
| CP-IDN-336 | Unitaria | un token absurdamente largo se rechaza sin llegar al caso de uso | `RecuperacionControllerTest.java` |
| CP-IDN-337 | Unitaria | confirmar responde 204, 400 si la contrasena incumple la politica y 410 si el enlace no sirve | `RecuperacionControllerTest.java` |
| CP-IDN-338 | Unitaria | los DTO no dejan el token ni la contrasena en una traza | `RecuperacionControllerTest.java` |
| CP-IDN-339 | Integración | Flujo de integración de selección de 2FA por correo electrónico | `SeleccionSegundoFactorIntegrationTest.java` |
| CP-IDN-340 | Unitaria | no entrega ningún token: el ingreso no termina aquí | `SesionControllerTest.java` |
| CP-IDN-341 | Unitaria | la primera vez entrega el URI para dar de alta el segundo factor | `SesionControllerTest.java` |
| CP-IDN-342 | Unitaria | se toma de X-Forwarded-For, no la del gateway | `SesionControllerTest.java` |
| CP-IDN-343 | Unitaria | sin esa cabecera cae en la dirección directa | `SesionControllerTest.java` |
| CP-IDN-344 | Unitaria | Un agente ausente no rompe nada | `SesionControllerTest.java` |
| CP-IDN-345 | Unitaria | el token de acceso va en el cuerpo y el de renovación NO | `SesionControllerTest.java` |
| CP-IDN-346 | Unitaria | la cookie es HttpOnly y SameSite=Strict | `SesionControllerTest.java` |
| CP-IDN-347 | Unitaria | el nombre y el atributo Secure dependen del entorno | `SesionControllerTest.java` |
| CP-IDN-348 | Unitaria | Renueva con la cookie del entorno | `SesionControllerTest.java` |
| CP-IDN-349 | Unitaria | sin cookie la sesión se da por caducada, sin llamar al caso de uso | `SesionControllerTest.java` |
| CP-IDN-350 | Unitaria | una cookie con otro nombre se ignora | `SesionControllerTest.java` |
| CP-IDN-351 | Unitaria | invalida el token de la cookie y la borra con Max-Age=0 | `SesionControllerTest.java` |
| CP-IDN-352 | Unitaria | sin cookie responde igual: no revela si habia sesion | `SesionControllerTest.java` |
| CP-IDN-353 | Integración | POST /api/v1/registro/{usuarioId}/codigo/reenviar reenvía un nuevo código OTP | `VerificacionContactoControllerTest.java` |
| CP-IDN-354 | Integración | POST /api/v1/registro/{usuarioId}/codigo/verificar verifica el código enviado | `VerificacionContactoControllerTest.java` |
| CP-IDN-355 | Integración | Flujo de integración: solicita reenvío de código y lo verifica exitosamente | `VerificacionContactoRegistroIntegrationTest.java` |
| CP-IDN-356 | Integración | Flujo de integración: falla la verificación con un código inválido respondiendo 400 | `VerificacionContactoRegistroIntegrationTest.java` |
| CP-IDN-357 | Integración | reintenta 3 veces ante un error 5xx y termina en KycServiceNoDisponibleException | `AdaptadorVerificadorKycTest.java` |
| CP-IDN-358 | Integración | un timeout de lectura NO se reintenta: el solicitante no espera 3 x 10 s | `AdaptadorVerificadorKycTest.java` |
| CP-IDN-359 | Integración | una conexion rechazada si se reintenta: falla al instante | `AdaptadorVerificadorKycTest.java` |
| CP-IDN-360 | Integración | un error 4xx no dispara reintento ni se convierte en 'no disponible' | `AdaptadorVerificadorKycTest.java` |
| CP-IDN-361 | Integración | el circuito se abre tras agotar el umbral de fallos y deja de llamar al servicio | `AdaptadorVerificadorKycTest.java` |
| CP-IDN-362 | Integración | traduce una evaluacion rechazada al motivo del dominio | `AdaptadorVerificadorKycTest.java` |
| CP-IDN-363 | Integración | una evaluacion aceptada llega sin pasar por el fallback | `AdaptadorVerificadorKycTest.java` |
| CP-IDN-364 | Integración | traduce los datos extraidos a una lectura del dominio | `AdaptadorVerificadorKycTest.java` |
| CP-IDN-365 | Integración | un documento ilegible, o datos que violan las reglas del dominio, son una lectura vacia | `AdaptadorVerificadorKycTest.java` |
| CP-IDN-366 | Integración | la lectura por OCR va por su propio cliente (timeout largo); la evaluacion, por el de 10 s | `AdaptadorVerificadorKycTest.java` |
| CP-IDN-367 | Unitaria | El cliente openapi del kyc service se genera en tiempo de build | `ClienteKycGeneradoTest.java` |
| CP-IDN-368 | Unitaria | el enlace lleva el token en el fragmento y no se envia en el hilo de la peticion | `NotificadorDeRecuperacionPorCorreoTest.java` |
| CP-IDN-369 | Unitaria | el aviso de cambio de contrasena lleva a la pantalla de ingreso | `NotificadorDeRecuperacionPorCorreoTest.java` |
| CP-IDN-370 | Unitaria | un fallo de SES no se propaga: la contrasena ya cambio | `NotificadorDeRecuperacionPorCorreoTest.java` |
| CP-IDN-371 | Unitaria | con su pool propio envia en otro hilo y se apaga ordenadamente | `NotificadorDeRecuperacionPorCorreoTest.java` |
| CP-IDN-372 | Unitaria | fuera de produccion solo se anota, sin enlace | `NotificadorDeRecuperacionPorCorreoTest.java` |
| CP-IDN-373 | Unitaria | la bienvenida lleva el enlace para continuar el registro con su solicitud | `NotificadorPorCorreoTest.java` |
| CP-IDN-374 | Unitaria | el codigo por correo va destacado en el cuerpo, sin boton | `NotificadorPorCorreoTest.java` |
| CP-IDN-375 | Unitaria | los avisos de seguridad, de intento de registro y de revision llevan su plantilla | `NotificadorPorCorreoTest.java` |
| CP-IDN-376 | Unitaria | el SMS no tiene canal: no envia nada ni falla | `NotificadorPorCorreoTest.java` |
| CP-IDN-377 | Unitaria | si SES falla, la operacion que origino el correo no se rompe | `NotificadorPorCorreoTest.java` |
| CP-IDN-378 | Unitaria | el dato se escapa: un enlace con HTML no inyecta marcado | `NotificadorPorCorreoTest.java` |
| CP-IDN-379 | Unitaria | SES recibe remitente, destinatario, asunto y las dos versiones del cuerpo | `NotificadorPorCorreoTest.java` |
| CP-IDN-380 | Unitaria | la configuracion arma el cliente de SES y el enviador | `NotificadorPorCorreoTest.java` |
| CP-IDN-381 | Unitaria | Lo que se cifra se recupera | `CifradorAes256GcmTest.java` |
| CP-IDN-382 | Unitaria | el criptograma no contiene el dato en claro | `CifradorAes256GcmTest.java` |
| CP-IDN-383 | Unitaria | cifrar dos veces el mismo dato da resultados distintos: el IV es aleatorio | `CifradorAes256GcmTest.java` |
| CP-IDN-384 | Unitaria | Declara el esquema para poder rotar la clave despues | `CifradorAes256GcmTest.java` |
| CP-IDN-385 | Unitaria | un criptograma manipulado falla en lugar de devolver basura | `CifradorAes256GcmTest.java` |
| CP-IDN-386 | Unitaria | Rechaza un criptograma sin esquema conocido | `CifradorAes256GcmTest.java` |
| CP-IDN-387 | Unitaria | una clave del tamano equivocado impide arrancar, no falla en el primer alta | `CifradorAes256GcmTest.java` |
| CP-IDN-388 | Unitaria | El nulo atraviesa sin romper | `CifradorAes256GcmTest.java` |
| CP-IDN-389 | Unitaria | la derivacion declara ser Argon2id, no otra familia | `CifradorArgon2idTest.java` |
| CP-IDN-390 | Unitaria | la derivacion no contiene la contrasena | `CifradorArgon2idTest.java` |
| CP-IDN-391 | Unitaria | dos derivaciones de la misma contrasena son distintas: la sal es aleatoria | `CifradorArgon2idTest.java` |
| CP-IDN-392 | Unitaria | verifica correctamente pese a que las sales difieran | `CifradorArgon2idTest.java` |
| CP-IDN-393 | Unitaria | Rechaza una contrasena distinta | `CifradorArgon2idTest.java` |
| CP-IDN-394 | Unitaria | la derivacion lleva dentro los parametros de OWASP | `CifradorArgon2idTest.java` |
| CP-IDN-395 | Unitaria | firma HS256 con emisor, audiencia, tipo, sujeto, huella, caducidad e id | `EmisorDeConfirmacionesJwtTest.java` |
| CP-IDN-396 | Unitaria | no arranca con una clave de menos de 32 bytes | `EmisorDeConfirmacionesJwtTest.java` |
| CP-IDN-397 | Unitaria | el token va firmado y la firma se verifica con la misma clave | `EmisorDeTokensJwtTest.java` |
| CP-IDN-398 | Unitaria | un token firmado con otra clave no se acepta | `EmisorDeTokensJwtTest.java` |
| CP-IDN-399 | Unitaria | Lleva el usuario su estado yla caducidad | `EmisorDeTokensJwtTest.java` |
| CP-IDN-400 | Unitaria | no lleva ni correo ni celular: un JWT va firmado, no cifrado | `EmisorDeTokensJwtTest.java` |
| CP-IDN-401 | Unitaria | cada token lleva su identificador, para poder revocarlo uno a uno | `EmisorDeTokensJwtTest.java` |
| CP-IDN-402 | Unitaria | el token de renovación es aleatorio y su huella no lo revela | `EmisorDeTokensJwtTest.java` |
| CP-IDN-403 | Unitaria | la misma entrada da siempre la misma huella | `EmisorDeTokensJwtTest.java` |
| CP-IDN-404 | Unitaria | una clave corta impide arrancar, no falla en el primer ingreso | `EmisorDeTokensJwtTest.java` |
| CP-IDN-405 | Unitaria | El token de renovacion no se imprime | `EmisorDeTokensJwtTest.java` |
| CP-IDN-406 | Unitaria | reproduce el vector de prueba oficial de la RFC 6238 | `GeneradorTotpRfc6238Test.java` |
| CP-IDN-407 | Unitaria | Rechaza un codigo que no corresponde | `GeneradorTotpRfc6238Test.java` |
| CP-IDN-408 | Unitaria | acepta la ventana anterior y la siguiente, no la de mas alla | `GeneradorTotpRfc6238Test.java` |
| CP-IDN-409 | Unitaria | Genera secretos base32 de la longitud correcta | `GeneradorTotpRfc6238Test.java` |
| CP-IDN-410 | Unitaria | Dos secretos seguidos no se parecen | `GeneradorTotpRfc6238Test.java` |
| CP-IDN-411 | Unitaria | el URI de aprovisionamiento es el que entienden las aplicaciones | `GeneradorTotpRfc6238Test.java` |
| CP-IDN-412 | Unitaria | un codigo generado con un secreto no vale con otro | `GeneradorTotpRfc6238Test.java` |
| CP-IDN-413 | Unitaria | Envia el correo con el codigo | `NotificadorDeSegundoFactorPorCorreoDePruebaTest.java` |
| CP-IDN-414 | Unitaria | Enviar por sms no falla aunque no haya canal de prueba | `NotificadorDeSegundoFactorPorCorreoDePruebaTest.java` |
| CP-IDN-415 | Unitaria | Envia por correo sin fallar | `NotificadorDeSegundoFactorPorEventosTest.java` |
| CP-IDN-416 | Unitaria | Envia por sms sin fallar | `NotificadorDeSegundoFactorPorEventosTest.java` |
| CP-IDN-417 | Unitaria | Avisa sin fallar | `NotificadorDeVerificacionKycPorEventosTest.java` |
| CP-IDN-418 | Unitaria | el secreto se cifra al guardar: en claro seria como guardar la contraseña | `AdaptadoresDePersistenciaTest.java` |
| CP-IDN-419 | Unitaria | Descifra al leer | `AdaptadoresDePersistenciaTest.java` |
| CP-IDN-420 | Unitaria | Un usuario sin segundo factor devuelve vacio | `AdaptadoresDePersistenciaTest.java` |
| CP-IDN-421 | Unitaria | quien nunca ha fallado no tiene fila, y aun asi se carga limpio | `AdaptadoresDePersistenciaTest.java` |
| CP-IDN-422 | Unitaria | Reconstruye el bloqueo guardado | `AdaptadoresDePersistenciaTest.java` |
| CP-IDN-423 | Unitaria | Guarda los fallos yel bloqueo | `AdaptadoresDePersistenciaTest.java` |
| CP-IDN-424 | Unitaria | la solicitud vigente es la ultima iniciada y sin caducar | `AdaptadoresDePersistenciaTest.java` |
| CP-IDN-425 | Unitaria | registrar un fallo suma un intento y lo devuelve, sin tocar el estado | `AdaptadoresDePersistenciaTest.java` |
| CP-IDN-426 | Unitaria | cada lado del DNI lleva su propio contador | `AdaptadoresDePersistenciaTest.java` |
| CP-IDN-427 | Unitaria | el contador nunca pasa de 3: la restriccion de la tabla lo prohibe | `AdaptadoresDePersistenciaTest.java` |
| CP-IDN-428 | Unitaria | sabe si la solicitud ya esta en revision manual, y una inexistente no lo esta | `AdaptadoresDePersistenciaTest.java` |
| CP-IDN-429 | Unitaria | los datos declarados vuelven con el documento descifrado; un senuelo no tiene | `AdaptadoresDePersistenciaTest.java` |
| CP-IDN-430 | Unitaria | marcar el documento cargado cambia el estado | `AdaptadoresDePersistenciaTest.java` |
| CP-IDN-431 | Unitaria | marcar en revision manual cambia el estado | `AdaptadoresDePersistenciaTest.java` |
| CP-IDN-432 | Unitaria | registrar un fallo sobre una solicitud inexistente falla en vez de crear una fila | `AdaptadoresDePersistenciaTest.java` |
| CP-IDN-433 | Unitaria | la solicitud pasa a RECHAZADA con el motivo y la fecha del rechazo | `AdaptadoresDePersistenciaTest.java` |
| CP-IDN-434 | Unitaria | el estado de una solicitud inexistente es vacio, y rechazarla es un error | `AdaptadoresDePersistenciaTest.java` |
| CP-IDN-435 | Unitaria | guarda la referencia, el hash, el tipo y el tamano del documento, nunca la imagen | `AdaptadoresDePersistenciaTest.java` |
| CP-IDN-436 | Unitaria | el ultimo documento de un lado vuelve como objeto de dominio | `AdaptadoresDePersistenciaTest.java` |
| CP-IDN-437 | Unitaria | el numero del DNI se cifra al guardar la lectura; solo quedan en claro los 4 ultimos | `AdaptadoresDePersistenciaTest.java` |
| CP-IDN-438 | Unitaria | la ultima lectura del OCR se busca solo entre MRZ y anverso, y vuelve descifrada | `AdaptadoresDePersistenciaTest.java` |
| CP-IDN-439 | Unitaria | registra el tipo, la IP y el agente que exige HU-04 | `AdaptadoresDePersistenciaTest.java` |
| CP-IDN-440 | Unitaria | un intento sobre un correo desconocido tambien se anota, sin usuario | `AdaptadoresDePersistenciaTest.java` |
| CP-IDN-441 | Unitaria | un desafio guardado se lee con su usuario y su caducidad | `AdaptadoresDeSesionYUsuarioTest.java` |
| CP-IDN-442 | Unitaria | consumir un desafio lo marca con la hora del reloj; uno inexistente no falla | `AdaptadoresDeSesionYUsuarioTest.java` |
| CP-IDN-443 | Unitaria | un token de renovacion vuelve con su familia, huella y fechas | `AdaptadoresDeSesionYUsuarioTest.java` |
| CP-IDN-444 | Unitaria | invalidar una familia corta todas sus sesiones en el instante del reloj | `AdaptadoresDeSesionYUsuarioTest.java` |
| CP-IDN-445 | Unitaria | HU-21: cerrar las sesiones de un usuario invalida todas sus familias de un golpe | `AdaptadoresDeSesionYUsuarioTest.java` |
| CP-IDN-446 | Unitaria | guardar y releer devuelve el mismo usuario, con todos sus datos | `AdaptadoresDeSesionYUsuarioTest.java` |
| CP-IDN-447 | Unitaria | busca por correo y por id, y responde si un correo ya existe | `AdaptadoresDeSesionYUsuarioTest.java` |
| CP-IDN-448 | Unitaria | una fila con un estado que no existe falla al leerse, no se propaga | `AdaptadoresDeSesionYUsuarioTest.java` |
| CP-IDN-449 | Unitaria | una confirmacion guardada vuelve igual, con su metodo, desafio e intentos | `AdaptadorRepositorioDeConfirmacionesTest.java` |
| CP-IDN-450 | Unitaria | Guarda un desafío por código convirtiéndolo a entidad JPA | `AdaptadorRepositorioDeDesafioPorCodigoTest.java` |
| CP-IDN-451 | Unitaria | Busca por id devolviendo el modelo de dominio reconstituido | `AdaptadorRepositorioDeDesafioPorCodigoTest.java` |
| CP-IDN-452 | Unitaria | Busca el último pendiente ordenado por fecha de creación desc | `AdaptadorRepositorioDeDesafioPorCodigoTest.java` |
| CP-IDN-453 | Unitaria | buscarPorUsuarioYTipo convierte la entidad JPA a dominio | `AdaptadorRepositorioDeMetodoSegundoFactorTest.java` |
| CP-IDN-454 | Unitaria | listarPorUsuario devuelve la lista de métodos de dominio | `AdaptadorRepositorioDeMetodoSegundoFactorTest.java` |
| CP-IDN-455 | Unitaria | guardar persiste la entidad en el repositorio JPA | `AdaptadorRepositorioDeMetodoSegundoFactorTest.java` |
| CP-IDN-456 | Unitaria | un token guardado se lee con su usuario, huella y fechas | `AdaptadorRepositorioDeTokensDeRecuperacionTest.java` |
| CP-IDN-457 | Unitaria | contar, anular y marcar delegan en consultas de una sola sentencia | `AdaptadorRepositorioDeTokensDeRecuperacionTest.java` |
| CP-IDN-458 | Unitaria | Convierte correctamente de dominio a entidad y viceversa | `DesafioPorCodigoEntityTest.java` |
| CP-IDN-459 | Unitaria | Setters y getters modifican las propiedades de la entidad | `DesafioPorCodigoEntityTest.java` |
| CP-IDN-460 | Unitaria | republica a cada titular activo con su correo y su nombre de pila | `RepublicadorDeTitularesTest.java` |
| CP-IDN-461 | Unitaria | el evento sale a identity.titular.activado y no deja el correo en una traza | `RepublicadorDeTitularesTest.java` |
| CP-IDN-462 | Unitaria | el formulario apunta al bucket publico y lleva la clave, el tipo y la firma | `MinioAlmacenDeDocumentosTest.java` |
| CP-IDN-463 | Unitaria | la politica exige la clave, el tipo exacto y como maximo 5 MB: MinIO rechaza lo demas | `MinioAlmacenDeDocumentosTest.java` |
| CP-IDN-464 | Unitaria | la vigencia declarada es de 5 minutos, contados desde el reloj inyectado | `MinioAlmacenDeDocumentosTest.java` |
| CP-IDN-465 | Unitaria | firma con el cliente publico, no con el interno — el navegador no resuelve el host de Docker | `MinioAlmacenDeDocumentosTest.java` |
| CP-IDN-466 | Unitaria | calcula el SHA-256 en hexadecimal del contenido descargado | `MinioAlmacenDeDocumentosTest.java` |
| CP-IDN-467 | Unitaria | el mismo contenido produce siempre el mismo hash | `MinioAlmacenDeDocumentosTest.java` |
| CP-IDN-468 | Unitaria | un fallo de MinIO al firmar se traduce a IllegalStateException | `MinioAlmacenDeDocumentosTest.java` |
| CP-IDN-469 | Unitaria | un fallo de MinIO al descargar se traduce a IllegalStateException | `MinioAlmacenDeDocumentosTest.java` |
| CP-IDN-470 | Unitaria | describe el tamano y el tipo del objeto sin descargarlo | `MinioAlmacenDeDocumentosTest.java` |
| CP-IDN-471 | Unitaria | una clave que no existe se describe como vacia, no como error | `MinioAlmacenDeDocumentosTest.java` |
| CP-IDN-472 | Unitaria | cualquier otro fallo al describir se traduce a IllegalStateException | `MinioAlmacenDeDocumentosTest.java` |
| CP-IDN-473 | Unitaria | eliminar borra el objeto del bucket de KYC | `MinioAlmacenDeDocumentosTest.java` |
| CP-IDN-474 | Aceptación (Gherkin) | KYC rechazado | `rechazo-de-solicitud.feature` |
| CP-IDN-475 | Aceptación (Gherkin) | Una solicitud ya aprobada no se puede rechazar | `rechazo-de-solicitud.feature` |
| CP-IDN-476 | Aceptación (Gherkin) | La respuesta es idéntica exista o no la cuenta | `recuperacion-de-contrasena.feature` |
| CP-IDN-477 | Aceptación (Gherkin) | Un enlace válido permite fijar una contraseña nueva que cumple la política | `recuperacion-de-contrasena.feature` |
| CP-IDN-478 | Aceptación (Gherkin) | Un enlace usado o caducado no sirve | `recuperacion-de-contrasena.feature` |
| CP-IDN-479 | Aceptación (Gherkin) | Cambiar la contraseña cierra todas las sesiones y queda auditado | `recuperacion-de-contrasena.feature` |
| CP-IDN-480 | Aceptación (Gherkin) | Recuperar la contraseña no es recuperar la cuenta | `recuperacion-de-contrasena.feature` |
| CP-IDN-481 | Aceptación (Gherkin) | Registro exitoso con datos válidos | `registro-de-usuario.feature` |
| CP-IDN-482 | Aceptación (Gherkin) | El correo ya está registrado | `registro-de-usuario.feature` |
| CP-IDN-483 | Aceptación (Gherkin) | La contraseña no cumple la política | `registro-de-usuario.feature` |
| CP-IDN-484 | Aceptación (Gherkin) | No se aceptan los términos | `registro-de-usuario.feature` |
| CP-IDN-485 | Aceptación (Gherkin) | Un menor de edad no puede abrir una cuenta | `registro-de-usuario.feature` |
| CP-IDN-486 | Aceptación (Gherkin) | Captura correcta de ambos lados | `verificacion-de-identidad-dni.feature` |
| CP-IDN-487 | Aceptación (Gherkin) | La imagen no es un documento de identidad | `verificacion-de-identidad-dni.feature` |
| CP-IDN-488 | Aceptación (Gherkin) | Calidad insuficiente | `verificacion-de-identidad-dni.feature` |
| CP-IDN-489 | Aceptación (Gherkin) | Agotamiento de intentos | `verificacion-de-identidad-dni.feature` |
| CP-IDN-490 | Aceptación (Gherkin) | El servicio de visión no responde | `verificacion-de-identidad-dni.feature` |
| CP-IDN-491 | Aceptación (Gherkin) | Corrección de un dato mal extraído antes de confirmar | `verificacion-de-identidad-dni.feature` |

## `ayni-core-banking-service`

| ID | Nivel | Caso | Archivo |
|---|---|---|---|
| CP-CORE-001 | Unitaria | crea la cuenta en soles, ACTIVA y con saldo cero | `AbrirCuentaDeAhorroServiceTest.java` |
| CP-CORE-002 | Unitaria | genera un número único y su CCI de 20 dígitos | `AbrirCuentaDeAhorroServiceTest.java` |
| CP-CORE-003 | Unitaria | escribe CuentaAperturada en la bandeja de salida | `AbrirCuentaDeAhorroServiceTest.java` |
| CP-CORE-004 | Unitaria | la cuenta usa el producto de ahorro en soles del catálogo | `AbrirCuentaDeAhorroServiceTest.java` |
| CP-CORE-005 | Unitaria | Rechaza una segunda cuenta en la misma moneda | `AbrirCuentaDeAhorroServiceTest.java` |
| CP-CORE-006 | Unitaria | no escribe ningún evento si no llegó a abrir nada | `AbrirCuentaDeAhorroServiceTest.java` |
| CP-CORE-007 | Unitaria | registra el intento en la pista de auditoría · AYNI-326 | `AbrirCuentaDeAhorroServiceTest.java` |
| CP-CORE-008 | Unitaria | una apertura que sale bien no deja ningún rechazo en la pista | `AbrirCuentaDeAhorroServiceTest.java` |
| CP-CORE-009 | Unitaria | el evento repetido devuelve la cuenta existente, no crea otra | `AbrirCuentaDeAhorroServiceTest.java` |
| CP-CORE-010 | Unitaria | tampoco duplica el evento en la bandeja de salida | `AbrirCuentaDeAhorroServiceTest.java` |
| CP-CORE-011 | Unitaria | una reentrega no es un intento de duplicar: no se audita como rechazo | `AbrirCuentaDeAhorroServiceTest.java` |
| CP-CORE-012 | Unitaria | recuerda la operación con la solicitud como clave | `AbrirCuentaDeAhorroServiceTest.java` |
| CP-CORE-013 | Unitaria | sin cierres previos empieza por ayer en hora de Lima; con cierres, recupera los dias que faltan | `CierreDiarioServiceTest.java` |
| CP-CORE-014 | Unitaria | un dia ya cerrado no se vuelve a procesar | `CierreDiarioServiceTest.java` |
| CP-CORE-015 | Unitaria | una cuenta sin tasa vigente no devenga, pero el dia se cierra | `CierreDiarioServiceTest.java` |
| CP-CORE-016 | Unitaria | el ultimo dia del mes capitaliza, publica el evento y deja rastro en la auditoria | `CierreDiarioServiceTest.java` |
| CP-CORE-017 | Unitaria | la pista registra depositos, transferencias, repetidas y rechazos | `OperarCuentaServiceTest.java` |
| CP-CORE-018 | Unitaria | depositar, transferir y que los saldos cuadren | `OperarCuentaServiceTest.java` |
| CP-CORE-019 | Unitaria | HU-13: el evento de la transferencia lleva a los dos titulares y sus saldos posteriores | `OperarCuentaServiceTest.java` |
| CP-CORE-020 | Unitaria | HU-06: el abono de intereses se reconoce por su contrapartida tecnica | `OperarCuentaServiceTest.java` |
| CP-CORE-021 | Unitaria | HU-07: el comprobante se consulta despues; quien no participa no lo ve | `OperarCuentaServiceTest.java` |
| CP-CORE-022 | Unitaria | HU-08 · AYNI-328: pedir el comprobante de otra persona queda como evento de seguridad | `OperarCuentaServiceTest.java` |
| CP-CORE-023 | Unitaria | HU-08 · AYNI-328: el propio movimiento y un identificador inexistente no son accesos ajenos | `OperarCuentaServiceTest.java` |
| CP-CORE-024 | Unitaria | HU-08 · AYNI-328: el titular de una cuenta bloqueada no ve su movimiento, pero no es un acceso ajeno | `OperarCuentaServiceTest.java` |
| CP-CORE-025 | Unitaria | repetir con la misma clave devuelve el mismo comprobante y no mueve dinero | `OperarCuentaServiceTest.java` |
| CP-CORE-026 | Unitaria | HU-07: sin confirmacion con segundo factor no se mueve dinero | `OperarCuentaServiceTest.java` |
| CP-CORE-027 | Unitaria | HU-07: un reintento con la misma clave no vuelve a pedir la confirmacion | `OperarCuentaServiceTest.java` |
| CP-CORE-028 | Unitaria | el deposito simulado no pide segundo factor | `OperarCuentaServiceTest.java` |
| CP-CORE-029 | Unitaria | un deposito repetido tampoco se duplica | `OperarCuentaServiceTest.java` |
| CP-CORE-030 | Unitaria | del lado del beneficiario, el comprobante muestra al ordenante enmascarado | `OperarCuentaServiceTest.java` |
| CP-CORE-031 | Unitaria | una clave usada por otro titular no sirve para leer su operacion | `OperarCuentaServiceTest.java` |
| CP-CORE-032 | Unitaria | sin saldo suficiente no se mueve nada | `OperarCuentaServiceTest.java` |
| CP-CORE-033 | Unitaria | un destino que no existe, mal escrito o nulo se rechaza igual | `OperarCuentaServiceTest.java` |
| CP-CORE-034 | Unitaria | quien no tiene cuenta no puede operar | `OperarCuentaServiceTest.java` |
| CP-CORE-035 | Arquitectura | El dominio no conoce el framework | `ArquitecturaHexagonalTest.java` |
| CP-CORE-036 | Arquitectura | El dominio no depende de la infraestructura | `ArquitecturaHexagonalTest.java` |
| CP-CORE-037 | Arquitectura | Las capas se respetan | `ArquitecturaHexagonalTest.java` |
| CP-CORE-038 | Arquitectura | Los puertos de salida se nombran port | `ArquitecturaHexagonalTest.java` |
| CP-CORE-039 | Arquitectura | Los casos de uso se nombran use case | `ArquitecturaHexagonalTest.java` |
| CP-CORE-040 | Unitaria | el factor diario coincide con (1.05)^(1/360) − 1 en 30 cifras | `CalculoDeInteresTest.java` |
| CP-CORE-041 | Unitaria | escenario 1: S/ 1000 al 5 % rinden 0.13553742 en el dia, con 8 decimales | `CalculoDeInteresTest.java` |
| CP-CORE-042 | Unitaria | la raiz de Newton recupera la base al elevarla | `CalculoDeInteresTest.java` |
| CP-CORE-043 | Unitaria | escenario 4: sin saldo no hay devengo | `CalculoDeInteresTest.java` |
| CP-CORE-044 | Unitaria | la capitalizacion abona a centimos con HALF_EVEN y guarda la fraccion para el mes siguiente | `CalculoDeInteresTest.java` |
| CP-CORE-045 | Unitaria | el movimiento de capitalizacion cuadra, no exige el minimo y rechaza importes no positivos | `CalculoDeInteresTest.java` |
| CP-CORE-046 | Unitaria | nace ACTIVA, en soles y con saldo cero | `CuentaTest.java` |
| CP-CORE-047 | Unitaria | no hay forma de abrirla con saldo inicial | `CuentaTest.java` |
| CP-CORE-048 | Unitaria | Genera su cci con los digitos de control | `CuentaTest.java` |
| CP-CORE-049 | Unitaria | el número de cuenta y la moneda no pueden contradecirse | `CuentaTest.java` |
| CP-CORE-050 | Unitaria | es la suma de los asientos, no un campo guardado | `CuentaTest.java` |
| CP-CORE-051 | Unitaria | los abonos suman y los cargos restan, sin importar el orden | `CuentaTest.java` |
| CP-CORE-052 | Unitaria | no acepta asientos de otra cuenta | `CuentaTest.java` |
| CP-CORE-053 | Unitaria | el importe siempre es positivo: el signo lo lleva el tipo | `CuentaTest.java` |
| CP-CORE-054 | Unitaria | El efecto de un cargo es negativo | `CuentaTest.java` |
| CP-CORE-055 | Unitaria | una operación por partida doble suma cero | `CuentaTest.java` |
| CP-CORE-056 | Unitaria | Exige concepto | `CuentaTest.java` |
| CP-CORE-057 | Unitaria | Una cuenta bloqueada no admite movimientos | `CuentaTest.java` |
| CP-CORE-058 | Unitaria | Una cuenta cerrada no se puede bloquear | `CuentaTest.java` |
| CP-CORE-059 | Unitaria | toString no expone el numero ni el CCI: identifican al titular | `CuentaTest.java` |
| CP-CORE-060 | Unitaria | diez veces diez céntimos son exactamente un sol | `DineroTest.java` |
| CP-CORE-061 | Unitaria | redondea con HALF_EVEN, no con HALF_UP | `DineroTest.java` |
| CP-CORE-062 | Unitaria | normaliza la escala: 10 y 10.00 son el mismo importe | `DineroTest.java` |
| CP-CORE-063 | Unitaria | No suma monedas distintas | `DineroTest.java` |
| CP-CORE-064 | Unitaria | Resta ysigno | `DineroTest.java` |
| CP-CORE-065 | Unitaria | Cero es cero | `DineroTest.java` |
| CP-CORE-066 | Unitaria | Se muestra con su simbolo | `DineroTest.java` |
| CP-CORE-067 | Unitaria | Exige importe ymoneda | `DineroTest.java` |
| CP-CORE-068 | Unitaria | una transferencia carga al ordenante, abona al beneficiario y suma cero | `MovimientoTest.java` |
| CP-CORE-069 | Unitaria | se puede transferir exactamente todo el saldo, ni un centimo mas | `MovimientoTest.java` |
| CP-CORE-070 | Unitaria | los limites: minimo S/ 1.00 y maximo S/ 5,000.00 por transferencia | `MovimientoTest.java` |
| CP-CORE-071 | Unitaria | no se puede transferir a la misma cuenta | `MovimientoTest.java` |
| CP-CORE-072 | Unitaria | una cuenta bloqueada no puede enviar ni recibir | `MovimientoTest.java` |
| CP-CORE-073 | Unitaria | no hay conversion implicita entre monedas | `MovimientoTest.java` |
| CP-CORE-074 | Unitaria | sin concepto, la transferencia se describe como tal | `MovimientoTest.java` |
| CP-CORE-075 | Unitaria | el deposito simulado carga a la cuenta de fondeo y abona al cliente | `MovimientoTest.java` |
| CP-CORE-076 | Unitaria | el deposito simulado tiene tope de S/ 2,000.00 y respeta la moneda | `MovimientoTest.java` |
| CP-CORE-077 | Unitaria | no se puede construir un movimiento que cree o destruya dinero | `MovimientoTest.java` |
| CP-CORE-078 | Unitaria | cada motivo de rechazo trae un texto para el cliente | `MovimientoTest.java` |
| CP-CORE-079 | Unitaria | la forma canonica normaliza el destino y el importe | `OperacionAConfirmarTest.java` |
| CP-CORE-080 | Unitaria | la huella es el SHA-256 en Base64 de la forma canonica | `OperacionAConfirmarTest.java` |
| CP-CORE-081 | Unitaria | cambiar el destino, el importe o la clave cambia la huella | `OperacionAConfirmarTest.java` |
| CP-CORE-082 | Unitaria | El contrato se presenta con su nombre y apunta al gateway, no a la red interna | `ConfiguracionDeLaDocumentacionTest.java` |
| CP-CORE-083 | Unitaria | Activa las tareas programadas: sin ellas el outbox no publicaria nunca | `ConfiguracionDeProgramacionTest.java` |
| CP-CORE-084 | Unitaria | Esta bajo el paquete de la clase de arranque, asi que el escaneo de componentes la encuentra | `ConfiguracionDeProgramacionTest.java` |
| CP-CORE-085 | Unitaria | Sin AYNI_API_DOCS, /v3/api-docs y Swagger UI estan apagados | `DocumentacionDeLaApiTest.java` |
| CP-CORE-086 | Unitaria | cierra los dias pendientes en orden | `CierreDiarioProgramadoTest.java` |
| CP-CORE-087 | Unitaria | si un dia falla, no cierra los siguientes | `CierreDiarioProgramadoTest.java` |
| CP-CORE-088 | Unitaria | una cuenta recién abierta se ve con saldo cero | `CuentaControllerTest.java` |
| CP-CORE-089 | Unitaria | el saldo sale de los asientos, no de un campo guardado | `CuentaControllerTest.java` |
| CP-CORE-090 | Unitaria | el número y el CCI van agrupados y también en crudo, para poder copiarlos | `CuentaControllerTest.java` |
| CP-CORE-091 | Unitaria | la TREA se convierte de fracción a porcentaje en el borde | `CuentaControllerTest.java` |
| CP-CORE-092 | Unitaria | Sin tasa vigente el campo viene vacio | `CuentaControllerTest.java` |
| CP-CORE-093 | Unitaria | mientras la cuenta se abre responde 404, que no es un error | `CuentaControllerTest.java` |
| CP-CORE-094 | Unitaria | Un importe con formato no valido: 400 que nombra el campo, y la operacion no se intenta | `ManejadorDeErroresTest.java` |
| CP-CORE-095 | Unitaria | Una transferencia sin cuenta de destino: 400 con cada campo que falla, en orden estable | `ManejadorDeErroresTest.java` |
| CP-CORE-096 | Unitaria | Un cuerpo que no es JSON: 400 sin el mensaje interno del parser | `ManejadorDeErroresTest.java` |
| CP-CORE-097 | Unitaria | Un identificador de movimiento que no es UUID: 400 que nombra el parametro, no un 500 | `ManejadorDeErroresTest.java` |
| CP-CORE-098 | Unitaria | Un parametro obligatorio ausente: 400 que lo nombra | `ManejadorDeErroresTest.java` |
| CP-CORE-099 | Unitaria | GET /cuentas/mia: la cuenta del titular del token, con saldo derivado | `OperacionesControllerTest.java` |
| CP-CORE-100 | Unitaria | sin la cabecera del gateway: 401, nunca datos | `OperacionesControllerTest.java` |
| CP-CORE-101 | Unitaria | quien aun no tiene cuenta recibe 404 con explicacion | `OperacionesControllerTest.java` |
| CP-CORE-102 | Unitaria | GET /movimientos: del mas reciente al mas antiguo, acotado a 50 | `OperacionesControllerTest.java` |
| CP-CORE-103 | Unitaria | GET /movimientos/{id}: 200 con el comprobante propio; 404 si no existe o es ajeno | `OperacionesControllerTest.java` |
| CP-CORE-104 | Unitaria | GET /movimientos/{id}/comprobante.pdf: un PDF real, descargable y sin cache | `OperacionesControllerTest.java` |
| CP-CORE-105 | Unitaria | POST /transferencias sin confirmacion valida: 403 CONFIRMACION_REQUERIDA | `OperacionesControllerTest.java` |
| CP-CORE-106 | Unitaria | POST /transferencias: 201 con el comprobante y su ubicacion | `OperacionesControllerTest.java` |
| CP-CORE-107 | Unitaria | un importe que no es un decimal con dos cifras se rechaza con 400 | `OperacionesControllerTest.java` |
| CP-CORE-108 | Unitaria | sin Idempotency-Key no se mueve dinero: 400 | `OperacionesControllerTest.java` |
| CP-CORE-109 | Unitaria | POST /depositos-simulados: 201 con el comprobante | `OperacionesControllerTest.java` |
| CP-CORE-110 | Unitaria | una regla de negocio rota es 422 con el codigo del motivo | `OperacionesControllerTest.java` |
| CP-CORE-111 | Unitaria | una clave reutilizada por otro titular es 409 | `OperacionesControllerTest.java` |
| CP-CORE-112 | Unitaria | muestra TREA = TEA, lo ganado en el mes y cada devengo con su tasa | `RendimientoControllerTest.java` |
| CP-CORE-113 | Unitaria | sin cuenta activa responde 404 | `RendimientoControllerTest.java` |
| CP-CORE-114 | Unitaria | el mensaje lleva el JSON tal cual, el id del outbox como messageId y es persistente | `PublicadorDeOutboxTest.java` |
| CP-CORE-115 | Unitaria | publica con la clave core.<evento> y marca el evento como publicado | `PublicadorDeOutboxTest.java` |
| CP-CORE-116 | Unitaria | si RabbitMQ falla, anota el fallo y sigue con los demas | `PublicadorDeOutboxTest.java` |
| CP-CORE-117 | Unitaria | lo que se guarda es lo que se relee, sin perder nada por el camino | `AdaptadoresDeCoreTest.java` |
| CP-CORE-118 | Unitaria | solo cuenta como duplicada la que esta ACTIVA | `AdaptadoresDeCoreTest.java` |
| CP-CORE-119 | Unitaria | Sin cuenta activa devuelve vacio | `AdaptadoresDeCoreTest.java` |
| CP-CORE-120 | Unitaria | el correlativo lo entrega la base, no un contador en memoria | `AdaptadoresDeCoreTest.java` |
| CP-CORE-121 | Unitaria | los asientos vuelven con su importe exacto y su signo | `AdaptadoresDeCoreTest.java` |
| CP-CORE-122 | Unitaria | La tasa vigente sale del catalogo | `AdaptadoresDeCoreTest.java` |
| CP-CORE-123 | Unitaria | un producto sin tasa vigente no revienta: devuelve vacio | `AdaptadoresDeCoreTest.java` |
| CP-CORE-124 | Unitaria | una operacion ya hecha devuelve su resultado anterior | `AdaptadoresDeCoreTest.java` |
| CP-CORE-125 | Unitaria | Una operacion nueva no tiene resultado previo | `AdaptadoresDeCoreTest.java` |
| CP-CORE-126 | Unitaria | Guarda la clave con su resultado | `AdaptadoresDeCoreTest.java` |
| CP-CORE-127 | Unitaria | una operacion queda con su movimiento y sin motivo | `AdaptadoresDeCoreTest.java` |
| CP-CORE-128 | Unitaria | un rechazo queda con su motivo y sin movimiento | `AdaptadoresDeCoreTest.java` |
| CP-CORE-129 | Unitaria | una apertura duplicada queda como rechazo de apertura, no de operacion · AYNI-326 | `AdaptadoresDeCoreTest.java` |
| CP-CORE-130 | Unitaria | un acceso ajeno queda con el movimiento que se intento leer y sin motivo · AYNI-328 | `AdaptadoresDeCoreTest.java` |
| CP-CORE-131 | Unitaria | las cuentas tecnicas no se encuentran por numero: nadie puede transferirles | `AdaptadorLibroMayorTest.java` |
| CP-CORE-132 | Unitaria | la cuenta de fondeo se resuelve por su id fijo, y solo en soles | `AdaptadorLibroMayorTest.java` |
| CP-CORE-133 | Unitaria | la cuenta de intereses se resuelve por su id fijo, solo en soles, y su ausencia apunta a V5 | `AdaptadorLibroMayorTest.java` |
| CP-CORE-134 | Unitaria | sin la fila de fondeo, falla con un mensaje que apunta a la migracion | `AdaptadorLibroMayorTest.java` |
| CP-CORE-135 | Unitaria | el saldo lo suma la base y vuelve con dos decimales | `AdaptadorLibroMayorTest.java` |
| CP-CORE-136 | Unitaria | registrar escribe los dos asientos del movimiento con su importe exacto | `AdaptadorLibroMayorTest.java` |
| CP-CORE-137 | Unitaria | ultimos movimientos y asientos de un movimiento vuelven al dominio sin perder nada | `AdaptadorLibroMayorTest.java` |
| CP-CORE-138 | Unitaria | bloquear delega en el SELECT ... FOR UPDATE ordenado | `AdaptadorLibroMayorTest.java` |
| CP-CORE-139 | Unitaria | buscar por id reconstruye la cuenta | `AdaptadorLibroMayorTest.java` |
| CP-CORE-140 | Unitaria | acepta un token valido de este usuario para esta operacion | `VerificadorDeConfirmacionJwtTest.java` |
| CP-CORE-141 | Unitaria | rechaza la falta de token y lo que no es un JWT | `VerificadorDeConfirmacionJwtTest.java` |
| CP-CORE-142 | Unitaria | rechaza un token firmado con otra clave, como la del token de acceso | `VerificadorDeConfirmacionJwtTest.java` |
| CP-CORE-143 | Unitaria | rechaza un token sin firmar (alg none) | `VerificadorDeConfirmacionJwtTest.java` |
| CP-CORE-144 | Unitaria | rechaza un token caducado | `VerificadorDeConfirmacionJwtTest.java` |
| CP-CORE-145 | Unitaria | rechaza un token de otro usuario | `VerificadorDeConfirmacionJwtTest.java` |
| CP-CORE-146 | Unitaria | rechaza un token que confirma otra operacion | `VerificadorDeConfirmacionJwtTest.java` |
| CP-CORE-147 | Unitaria | rechaza un token de otro emisor, otra audiencia u otro tipo | `VerificadorDeConfirmacionJwtTest.java` |
| CP-CORE-148 | Unitaria | no arranca con una clave de menos de 32 bytes | `VerificadorDeConfirmacionJwtTest.java` |
| CP-CORE-149 | Integración | escenario 5: dos transferencias de S/ 80 sobre S/ 100 en paralelo: solo pasa una | `ConcurrenciaDeTransferenciasTest.java` |
| CP-CORE-150 | Integración | una rafaga de transferencias nunca deja el saldo negativo y el dinero se conserva | `ConcurrenciaDeTransferenciasTest.java` |
| CP-CORE-151 | Integración | escenario 3 en paralelo: la misma clave a la vez mueve el dinero una sola vez y todos reciben el mismo comprobante | `ConcurrenciaDeTransferenciasTest.java` |
| CP-CORE-152 | Integración | transferencias cruzadas A→B y B→A a la vez no se interbloquean | `ConcurrenciaDeTransferenciasTest.java` |
| CP-CORE-153 | Aceptación (Gherkin) | Apertura exitosa tras aprobación del KYC | `apertura-automatica-de-cuenta.feature` |
| CP-CORE-154 | Aceptación (Gherkin) | Intento de apertura duplicada | `apertura-automatica-de-cuenta.feature` |
| CP-CORE-155 | Aceptación (Gherkin) | Intento de apertura duplicada · registro en la pista de auditoría | `apertura-automatica-de-cuenta.feature` |
| CP-CORE-156 | Aceptación (Gherkin) | KYC rechazado | `apertura-automatica-de-cuenta.feature` |
| CP-CORE-157 | Aceptación (Gherkin) | Fallo al publicar el evento | `apertura-automatica-de-cuenta.feature` |
| CP-CORE-158 | Aceptación (Gherkin) | Devengo diario correcto | `rendimiento-diario.feature` |
| CP-CORE-159 | Aceptación (Gherkin) | Capitalización mensual | `rendimiento-diario.feature` |
| CP-CORE-160 | Aceptación (Gherkin) | Cambio de tasa preservando el histórico | `rendimiento-diario.feature` |
| CP-CORE-161 | Aceptación (Gherkin) | Saldo cero | `rendimiento-diario.feature` |
| CP-CORE-162 | Aceptación (Gherkin) | Saldo disponible calculado desde los movimientos | `saldo-e-historial.feature` |
| CP-CORE-163 | Aceptación (Gherkin) | Consulta de movimientos con filtro *(pendiente: describe una brecha, no se ejecuta)* | `saldo-e-historial.feature` |
| CP-CORE-164 | Aceptación (Gherkin) | Intento de acceso a un movimiento ajeno | `saldo-e-historial.feature` |
| CP-CORE-165 | Aceptación (Gherkin) | Intento de acceso a un movimiento ajeno · registro como evento de seguridad | `saldo-e-historial.feature` |

## `ayni-notification-service`

| ID | Nivel | Caso | Archivo |
|---|---|---|---|
| CP-NOT-001 | Arquitectura | El dominio no conoce el framework | `ArquitecturaHexagonalTest.java` |
| CP-NOT-002 | Arquitectura | El dominio no depende de la infraestructura | `ArquitecturaHexagonalTest.java` |
| CP-NOT-003 | Arquitectura | Las capas se respetan | `ArquitecturaHexagonalTest.java` |
| CP-NOT-004 | Arquitectura | Los puertos de salida se nombran port | `ArquitecturaHexagonalTest.java` |
| CP-NOT-005 | Arquitectura | Los casos de uso se nombran use case | `ArquitecturaHexagonalTest.java` |
| CP-NOT-006 | Unitaria | nace pendiente y con turno inmediato | `NotificacionTest.java` |
| CP-NOT-007 | Unitaria | al enviarse queda ENVIADA y el intento lo registra | `NotificacionTest.java` |
| CP-NOT-008 | Unitaria | cada fallo dobla la espera y al agotar el maximo queda FALLIDA | `NotificacionTest.java` |
| CP-NOT-009 | Unitaria | una notificacion que ya no esta pendiente no se vuelve a tocar | `NotificacionTest.java` |
| CP-NOT-010 | Unitaria | la politica rechaza valores sin sentido y no desborda con muchos intentos | `NotificacionTest.java` |
| CP-NOT-011 | Unitaria | el detalle de un fallo no guarda direcciones de correo y se recorta a 500 | `NotificacionTest.java` |
| CP-NOT-012 | Unitaria | el titular exige correo y no lo muestra en las trazas | `NotificacionTest.java` |
| CP-NOT-013 | Unitaria | la plantilla rellena los marcadores y no deja ninguno sin valor | `NotificacionTest.java` |
| CP-NOT-014 | Unitaria | El cliente de SES tiene limite por intento y limite total | `ConfiguracionDeCorreoTest.java` |
| CP-NOT-015 | Unitaria | una transferencia avisa a quien envia y a quien recibe, cada uno con su saldo | `TraductorDeMovimientosTest.java` |
| CP-NOT-016 | Unitaria | un evento antiguo sin titulares no produce avisos | `TraductorDeMovimientosTest.java` |
| CP-NOT-017 | Unitaria | un deposito avisa a su titular con el saldo resultante | `TraductorDeMovimientosTest.java` |
| CP-NOT-018 | Unitaria | HU-06: el interes capitalizado avisa al titular con el mes en castellano y el saldo | `TraductorDeMovimientosTest.java` |
| CP-NOT-019 | Unitaria | el eventId es el messageId del outbox; si no llega o no es valido, el del movimiento | `TraductorDeMovimientosTest.java` |
| CP-NOT-020 | Unitaria | el conversor de RabbitMQ lee el JSON crudo del outbox con el tipo del escuchador | `TraductorDeMovimientosTest.java` |
| CP-NOT-021 | Unitaria | los escuchadores entregan los avisos y el titular a sus casos de uso | `TraductorDeMovimientosTest.java` |
| CP-NOT-022 | Unitaria | cuenta cada resultado en ayni_notificaciones_total{resultado} | `DespachadorProgramadoTest.java` |
| CP-NOT-023 | Unitaria | la configuracion enlaza las tres colas y arma la politica y los enviadores | `DespachadorProgramadoTest.java` |
| CP-NOT-024 | Unitaria | envia por SES desde el remitente, en UTF-8, con HTML escapado y version de texto | `EnviadorDeCorreoSesTest.java` |
| CP-NOT-025 | Unitaria | sin nombre de pila el saludo queda neutro | `EnviadorDeCorreoSesTest.java` |
| CP-NOT-026 | Unitaria | fuera de produccion el correo solo se anota | `EnviadorDeCorreoSesTest.java` |
| CP-NOT-027 | Integración | el titular se inserta y despues se actualiza con el ultimo evento | `NotificacionesEnPostgresTest.java` |
| CP-NOT-028 | Integración | escenarios 1 y 2: el mismo evento entregado a la vez por 8 hilos produce un solo correo | `NotificacionesEnPostgresTest.java` |
| CP-NOT-029 | Integración | AYNI-327: el rechazo de una solicitud sale con la plantilla de V4 y una sola vez aunque llegue dos | `NotificacionesEnPostgresTest.java` |
| CP-NOT-030 | Integración | las plantillas de V2 y V3 se rellenan con los datos del traductor | `NotificacionesEnPostgresTest.java` |
| CP-NOT-031 | Integración | si el proveedor falla se reprograma con espera y, agotado el maximo, queda FALLIDA sin el correo en el detalle | `NotificacionesEnPostgresTest.java` |
| CP-NOT-032 | Integración | un aviso cuyo titular aun no llego espera y sale cuando llega el evento de identity | `NotificacionesEnPostgresTest.java` |
| CP-NOT-033 | Aceptación (Gherkin) | KYC rechazado | `aviso-de-solicitud-rechazada.feature` |
| CP-NOT-034 | Aceptación (Gherkin) | Un rechazo que llega dos veces produce un solo correo | `aviso-de-solicitud-rechazada.feature` |
| CP-NOT-035 | Aceptación (Gherkin) | Cada movimiento genera un correo con importe, fecha y saldo resultante | `notificacion-de-movimientos.feature` |
| CP-NOT-036 | Aceptación (Gherkin) | Un evento duplicado produce un solo correo | `notificacion-de-movimientos.feature` |
| CP-NOT-037 | Aceptación (Gherkin) | Si el correo falla se reintenta con espera creciente hasta el máximo configurado | `notificacion-de-movimientos.feature` |

## `ayni-gateway`

| ID | Nivel | Caso | Archivo |
|---|---|---|---|
| CP-GW-001 | Unitaria | Sin AYNI_API_DOCS, Swagger UI y el contrato del gateway estan apagados | `DocumentacionDeLaApiTest.java` |
| CP-GW-002 | Unitaria | Sin AYNI_API_DOCS no se crean las rutas hacia los contratos de los servicios | `DocumentacionDeLaApiTest.java` |
| CP-GW-003 | Unitaria | El compose de produccion no enciende la documentacion de la API | `DocumentacionDeLaApiTest.java` |
| CP-GW-004 | Integración | Con la documentacion encendida existen las rutas hacia los contratos de identity, core-banking y kyc | `RutasDeDocumentacionTest.java` |
| CP-GW-005 | Unitaria | Conexion rechazada por el servicio: 503, Retry-After y problem+json | `ManejadorDeServicioNoDisponibleTest.java` |
| CP-GW-006 | Unitaria | Cualquier otro error sigue su camino: el manejador no lo convierte en 503 | `ManejadorDeServicioNoDisponibleTest.java` |
| CP-GW-007 | Unitaria | Sin token en ruta protegida responde401 yno llega al servicio | `FiltroDeAutenticacionTest.java` |
| CP-GW-008 | Unitaria | Con token valido reenvia con el usuario del token | `FiltroDeAutenticacionTest.java` |
| CP-GW-009 | Unitaria | La cabecera de usuario que manda el cliente se descarta siempre | `FiltroDeAutenticacionTest.java` |
| CP-GW-010 | Unitaria | La cabecera fabricada no sustituye al token en ruta protegida | `FiltroDeAutenticacionTest.java` |
| CP-GW-011 | Unitaria | Las rutas publicas pasan sin token | `FiltroDeAutenticacionTest.java` |
| CP-GW-012 | Unitaria | El preflight cors pasa sin token | `FiltroDeAutenticacionTest.java` |
| CP-GW-013 | Unitaria | Una cabecera que no es bearer se rechaza | `FiltroDeAutenticacionTest.java` |
| CP-GW-014 | Unitaria | Un bearer vacio se rechaza | `FiltroDeAutenticacionTest.java` |
| CP-GW-015 | Unitaria | Se ejecuta antes que el enrutado | `FiltroDeAutenticacionTest.java` |
| CP-GW-016 | Unitaria | Un token caducado no identifica anadie | `FiltroDeAutenticacionTest.java` |
| CP-GW-017 | Unitaria | Un token firmado con otra clave no identifica anadie | `FiltroDeAutenticacionTest.java` |
| CP-GW-018 | Unitaria | Un token de otro emisor no identifica anadie | `FiltroDeAutenticacionTest.java` |
| CP-GW-019 | Unitaria | Un token sin sujeto no identifica anadie | `FiltroDeAutenticacionTest.java` |
| CP-GW-020 | Unitaria | Un token sin caducidad no identifica anadie | `FiltroDeAutenticacionTest.java` |
| CP-GW-021 | Unitaria | El algoritmo none se rechaza | `FiltroDeAutenticacionTest.java` |
| CP-GW-022 | Unitaria | Basura no identifica anadie | `FiltroDeAutenticacionTest.java` |
| CP-GW-023 | Unitaria | Una clave corta impide arrancar | `FiltroDeAutenticacionTest.java` |
| CP-GW-024 | Unitaria | La configuracion crea el filtro con las rutas indicadas | `FiltroDeAutenticacionTest.java` |

## `ayni-kyc-service`

| ID | Nivel | Caso | Archivo |
|---|---|---|---|
| CP-KYC-001 | Unitaria | Debe construir el cliente sin esquema y marcando http como inseguro | `test_almacen_objetos_minio.py` |
| CP-KYC-002 | Unitaria | Debe marcar https como seguro | `test_almacen_objetos_minio.py` |
| CP-KYC-003 | Unitaria | Debe descargar los bytes del objeto | `test_almacen_objetos_minio.py` |
| CP-KYC-004 | Unitaria | Debe cerrar y liberar la conexion tras descargar | `test_almacen_objetos_minio.py` |
| CP-KYC-005 | Unitaria | Debe cerrar la conexion incluso si la lectura falla | `test_almacen_objetos_minio.py` |
| CP-KYC-006 | Unitaria | Debe calcular el hash sha256 de los bytes descargados | `test_almacen_objetos_minio.py` |
| CP-KYC-007 | Unitaria | Debe traducir una clave inexistente a objeto no encontrado | `test_almacen_objetos_minio.py` |
| CP-KYC-008 | Unitaria | Debe propagar otros errores de minio tal cual | `test_almacen_objetos_minio.py` |
| CP-KYC-009 | Unitaria | Debe detectar documento cuando la imagen tiene proporcion de dni | `test_detector_documento_opencv.py` |
| CP-KYC-010 | Unitaria | Debe rechazar cuando la imagen no tiene proporcion de dni | `test_detector_documento_opencv.py` |
| CP-KYC-011 | Unitaria | Debe rechazar cuando los bytes no son una imagen valida | `test_detector_documento_opencv.py` |
| CP-KYC-012 | Unitaria | Debe usar los datos del mrz cuando el reverso se lee bien | `test_extractor_datos_paddleocr.py` |
| CP-KYC-013 | Unitaria | Debe devolver el mrz sin fecha de emision cuando el anverso no la muestra | `test_extractor_datos_paddleocr.py` |
| CP-KYC-014 | Unitaria | Debe caer a heuristicas del anverso cuando el reverso no tiene mrz | `test_extractor_datos_paddleocr.py` |
| CP-KYC-015 | Unitaria | Debe caer a heuristicas cuando el mrz tiene un checksum invalido | `test_extractor_datos_paddleocr.py` |
| CP-KYC-016 | Unitaria | Debe retornar none cuando ni mrz ni heuristicas encuentran datos | `test_extractor_datos_paddleocr.py` |
| CP-KYC-017 | Unitaria | Debe leer el mrz del anverso en el dni azul | `test_extractor_datos_paddleocr.py` |
| CP-KYC-018 | Unitaria | Debe completar con el anverso el segundo apellido que el mrz no trae | `test_extractor_datos_paddleocr.py` |
| CP-KYC-019 | Unitaria | Debe extraer los campos cuando las etiquetas estan en lineas separadas | `test_heuristicas_anverso.py` |
| CP-KYC-020 | Unitaria | Debe extraer el valor cuando esta en la misma linea que la etiqueta | `test_heuristicas_anverso.py` |
| CP-KYC-021 | Unitaria | Debe retornar none cuando falta un campo requerido | `test_heuristicas_anverso.py` |
| CP-KYC-022 | Unitaria | Debe retornar none cuando el texto esta vacio | `test_heuristicas_anverso.py` |
| CP-KYC-023 | Unitaria | Debe leer los rotulos reales del dni sin devolver el rotulo como valor | `test_heuristicas_anverso.py` |
| CP-KYC-024 | Unitaria | Debe aceptar prenombres en una sola palabra como en el dnie | `test_heuristicas_anverso.py` |
| CP-KYC-025 | Unitaria | No debe tomar el rotulo siguiente como valor cuando falta el dato | `test_heuristicas_anverso.py` |
| CP-KYC-026 | Unitaria | Debe extraer la fecha de emision aunque no haya otros datos | `test_heuristicas_anverso.py` |
| CP-KYC-027 | Unitaria | Debe encontrar el segundo apellido aunque el ocr deforme el rotulo | `test_heuristicas_anverso.py` |
| CP-KYC-028 | Unitaria | No debe inventar un segundo apellido si tras el rotulo no hay letras | `test_heuristicas_anverso.py` |
| CP-KYC-029 | Unitaria | Debe extraer todos los campos cuando el mrz es valido | `test_mrz_td1.py` |
| CP-KYC-030 | Unitaria | Debe marcar checksum invalido cuando el ocr leyo mal un digito | `test_mrz_td1.py` |
| CP-KYC-031 | Unitaria | Debe retornar none cuando alguna linea no mide 30 caracteres | `test_mrz_td1.py` |
| CP-KYC-032 | Unitaria | Debe encontrar las tres lineas del mrz entre texto con ruido | `test_mrz_td1.py` |
| CP-KYC-033 | Unitaria | Debe retornar none cuando no hay tres lineas con forma de mrz | `test_mrz_td1.py` |
| CP-KYC-034 | Unitaria | Debe validar el digito verificador cuando es correcto | `test_mrz_td1.py` |
| CP-KYC-035 | Unitaria | Debe rechazar el digito verificador cuando no coincide con el numero | `test_mrz_td1.py` |
| CP-KYC-036 | Unitaria | Debe rechazar cuando el digito verificador no es numerico | `test_mrz_td1.py` |
| CP-KYC-037 | Unitaria | Numero documento verificado es independiente de los otros checksums | `test_mrz_td1.py` |
| CP-KYC-038 | Unitaria | No debe verificar un numero de documento que no son 8 digitos | `test_mrz_td1.py` |
| CP-KYC-039 | Unitaria | No debe verificar un numero de documento de 9 digitos | `test_mrz_td1.py` |
| CP-KYC-040 | Unitaria | No debe tomar un rotulo en mayusculas como linea de mrz | `test_mrz_td1.py` |
| CP-KYC-041 | Unitaria | Debe preferir la ventana que valida sus digitos verificadores | `test_mrz_td1.py` |
| CP-KYC-042 | Unitaria | Debe aceptar un dni escaneado y recortado al borde | `test_procesamiento_documento.py` |
| CP-KYC-043 | Unitaria | Debe encontrar una tarjeta de esquinas redondeadas sobre una mesa | `test_procesamiento_documento.py` |
| CP-KYC-044 | Unitaria | No debe tratar como documento una imagen sin contorno ni proporcion de dni | `test_procesamiento_documento.py` |
| CP-KYC-045 | Unitaria | Debe tratar como recortado un dni cuyo borde queda pegado al marco | `test_procesamiento_documento.py` |
| CP-KYC-046 | Unitaria | Debe aprobar cuando la imagen es nitida sin reflejos y bien encuadrada | `test_validador_calidad_opencv.py` |
| CP-KYC-047 | Unitaria | Debe rechazar por falta de nitidez cuando la imagen esta borrosa | `test_validador_calidad_opencv.py` |
| CP-KYC-048 | Unitaria | Debe rechazar por reflejo cuando hay una zona muy brillante | `test_validador_calidad_opencv.py` |
| CP-KYC-049 | Unitaria | Debe rechazar por encuadre cuando el documento toca el borde de la foto | `test_validador_calidad_opencv.py` |
| CP-KYC-050 | Unitaria | Debe rechazar todos los controles cuando los bytes no son una imagen valida | `test_validador_calidad_opencv.py` |
| CP-KYC-051 | Unitaria | Debe rechazar por iluminacion cuando la foto esta muy oscura | `test_validador_calidad_opencv.py` |
| CP-KYC-052 | Integración | Kyc api docs esta apagado por defecto | `test_documentacion.py` |
| CP-KYC-053 | Integración | Sin kyc api docs no hay documentacion publicada | `test_documentacion.py` |
| CP-KYC-054 | Integración | Debe aceptar una foto de dni nitida y bien encuadrada | `test_router_documentos.py` |
| CP-KYC-055 | Integración | Debe rechazar un objeto que no es un dni | `test_router_documentos.py` |
| CP-KYC-056 | Integración | Debe rechazar como no dni unos bytes que no son una imagen | `test_router_documentos.py` |
| CP-KYC-057 | Integración | Debe informar el motivo concreto cuando la foto esta desenfocada | `test_router_documentos.py` |
| CP-KYC-058 | Integración | Debe responder 404 cuando la clave no existe | `test_router_documentos.py` |
| CP-KYC-059 | Integración | Debe rechazar con 400 las imagenes de mas de 5 mb | `test_router_documentos.py` |
| CP-KYC-060 | Integración | Debe rechazar con 400 las claves que no son de un dni | `test_router_documentos.py` |
| CP-KYC-061 | Integración | Debe devolver los datos extraidos en camel case | `test_router_documentos.py` |
| CP-KYC-062 | Integración | Debe indicar que no es legible cuando el ocr no encuentra datos | `test_router_documentos.py` |
| CP-KYC-063 | Integración | Si la precarga del ocr falla el servicio arranca igual | `test_router_documentos.py` |
| CP-KYC-064 | Unitaria | Debe crear resultado cuando la similitud es valida | `test_dominio.py` |
| CP-KYC-065 | Unitaria | Debe rechazar cuando la similitud esta fuera de rango | `test_dominio.py` |
| CP-KYC-066 | Integración | Distancia a similitud porcentaje en los bordes | `test_kyc_match.py` |
| CP-KYC-067 | Integración | Distancia a similitud porcentaje respeta el umbral del modelo | `test_kyc_match.py` |
| CP-KYC-068 | Integración | Verify match approved | `test_kyc_match.py` |
| CP-KYC-069 | Integración | Verify match manual review | `test_kyc_match.py` |
| CP-KYC-070 | Integración | Verify match rejected | `test_kyc_match.py` |
| CP-KYC-071 | Integración | Verify match deepface exception | `test_kyc_match.py` |
| CP-KYC-072 | Integración | Verify match invalid image | `test_kyc_match.py` |
| CP-KYC-073 | Integración | Verify match file too large | `test_kyc_match.py` |
| CP-KYC-074 | Integración | Verify match imagen con cabecera valida pero cuerpo corrupto | `test_kyc_match.py` |
| CP-KYC-075 | Integración | Validar magic bytes rechaza contenido demasiado corto | `test_kyc_match.py` |
| CP-KYC-076 | Integración | Evaluar rostro unico y vivacidad sin rostro detectado | `test_kyc_match.py` |
| CP-KYC-077 | Integración | Evaluar rostro unico y vivacidad ante error de opencv retorna false | `test_kyc_match.py` |
| CP-KYC-078 | Integración | Cotejar rostros rechaza cuando no supera vivacidad | `test_kyc_match.py` |
| CP-KYC-079 | Unitaria | Una captura de dni sin problemas se acepta | `test_resultado_evaluacion_captura.py` |
| CP-KYC-080 | Unitaria | Sin nada que medir el motivo es que no es un dni | `test_resultado_evaluacion_captura.py` |
| CP-KYC-081 | Unitaria | Sin documento reconocido se culpa primero a la toma | `test_resultado_evaluacion_captura.py` |
| CP-KYC-082 | Unitaria | Con varios problemas se informa el que conviene corregir primero | `test_resultado_evaluacion_captura.py` |
| CP-KYC-083 | Unitaria | Un anverso enviado como reverso se rechaza por lado incorrecto | `test_resultado_evaluacion_captura.py` |
| CP-KYC-084 | Unitaria | El lado incorrecto se informa antes que la calidad | `test_resultado_evaluacion_captura.py` |

## `ayni-web`

| ID | Nivel | Caso | Archivo |
|---|---|---|---|
| CP-WEB-001 | Unitaria | muestra el estado, los cuatro últimos dígitos y lo consumido frente al límite | `tarjeta.test.tsx` |
| CP-WEB-002 | Unitaria | escenario 1: congelar la deja CONGELADA al momento y lo anuncia | `tarjeta.test.tsx` |
| CP-WEB-003 | Unitaria | una tarjeta congelada se puede descongelar | `tarjeta.test.tsx` |
| CP-WEB-004 | Unitaria | el interruptor deshabilita las compras por internet sin tocar el límite | `tarjeta.test.tsx` |
| CP-WEB-005 | Unitaria | guarda un límite válido, normalizado, sin tocar las compras por internet | `tarjeta.test.tsx` |
| CP-WEB-006 | Unitaria | un límite vacío, cero o mal escrito no se envía y se explica junto al campo | `tarjeta.test.tsx` |
| CP-WEB-007 | Unitaria | si el servidor rechaza el cambio, lo dice y la tarjeta sigue como estaba | `tarjeta.test.tsx` |
| CP-WEB-008 | Unitaria | sin tarjeta todavía (404) lo dice en lugar de mostrar controles que no controlan nada | `tarjeta.test.tsx` |
| CP-WEB-009 | Unitaria | pide la confirmación con la operación revisada y transfiere con el token de identity | `transferir.test.tsx` |
| CP-WEB-010 | Unitaria | con el correo como método indica que el código llegó al correo | `transferir.test.tsx` |
| CP-WEB-011 | Unitaria | no envía hasta tener 6 dígitos y solo acepta números | `transferir.test.tsx` |
| CP-WEB-012 | Unitaria | un código incorrecto dice cuántos intentos quedan y no transfiere | `transferir.test.tsx` |
| CP-WEB-013 | Unitaria | si la confirmación vence, vuelve a la revisión para pedir otra | `transferir.test.tsx` |
| CP-WEB-014 | Unitaria | explica los demás rechazos: sin 2FA, pausa y fallo de red | `transferir.test.tsx` |
| CP-WEB-015 | Unitaria | corregir vuelve al formulario con lo que la persona ya escribió | `transferir.test.tsx` |
| CP-WEB-016 | Unitaria | cancelar el código vuelve a la revisión sin transferir | `transferir.test.tsx` |
| CP-WEB-017 | Unitaria | la de pedir el enlace va en el marco de ingreso y no se indexa | `paginas.test.tsx` |
| CP-WEB-018 | Unitaria | la de la contraseña nueva no envía el Referer ni se indexa | `paginas.test.tsx` |
| CP-WEB-019 | Unitaria | pide los nueve campos del diseño aprobado | `FormularioDeRegistro.test.tsx` |
| CP-WEB-020 | Unitaria | el celular lleva el prefijo +51 fuera del valor que se escribe | `FormularioDeRegistro.test.tsx` |
| CP-WEB-021 | Unitaria | muestra la política de contraseña antes de que se escriba nada | `FormularioDeRegistro.test.tsx` |
| CP-WEB-022 | Unitaria | marca los requisitos según se van cumpliendo | `FormularioDeRegistro.test.tsx` |
| CP-WEB-023 | Unitaria | la casilla de consentimiento nunca viene marcada por defecto | `FormularioDeRegistro.test.tsx` |
| CP-WEB-024 | Unitaria | escenario 4 · no envía nada si no se aceptan los términos | `FormularioDeRegistro.test.tsx` |
| CP-WEB-025 | Unitaria | escenario 3 · no envía si la contraseña incumple la política | `FormularioDeRegistro.test.tsx` |
| CP-WEB-026 | Unitaria | rechaza un celular que no es móvil peruano | `FormularioDeRegistro.test.tsx` |
| CP-WEB-027 | Unitaria | rechaza un documento que no tiene ocho dígitos | `FormularioDeRegistro.test.tsx` |
| CP-WEB-028 | Unitaria | no deja abrir una cuenta a un menor de edad | `FormularioDeRegistro.test.tsx` |
| CP-WEB-029 | Unitaria | escenario 1 · envía al gateway y muestra el mensaje neutro | `FormularioDeRegistro.test.tsx` |
| CP-WEB-030 | Unitaria | escenario 2 · un correo ya registrado se ve exactamente igual | `FormularioDeRegistro.test.tsx` |
| CP-WEB-031 | Unitaria | pinta bajo cada campo los errores que devuelve el servidor | `FormularioDeRegistro.test.tsx` |
| CP-WEB-032 | Unitaria | explica qué hacer cuando la red falla, sin culpar a quien lo lee | `FormularioDeRegistro.test.tsx` |
| CP-WEB-033 | Unitaria | descarga el PDF con el token y un nombre de archivo propio | `DescargarComprobante.test.tsx` |
| CP-WEB-034 | Unitaria | en la tabla es un enlace discreto y avisa si falla | `DescargarComprobante.test.tsx` |
| CP-WEB-035 | Unitaria | muestra lo ganado de verdad, que TREA es igual a TEA y cada devengo con su tasa | `PanelDeRendimiento.test.tsx` |
| CP-WEB-036 | Unitaria | sin devengos todavía, muestra la estimación rotulada como tal | `PanelDeRendimiento.test.tsx` |
| CP-WEB-037 | Unitaria | si la consulta de rendimiento falló, usa la TREA de la cuenta | `PanelDeRendimiento.test.tsx` |
| CP-WEB-038 | Unitaria | sin saldo ni tasa invita al primer depósito | `PanelDeRendimiento.test.tsx` |
| CP-WEB-039 | Unitaria | con los importes ocultos no se ve ninguna cifra | `PanelDeRendimiento.test.tsx` |
| CP-WEB-040 | Unitaria | si TREA y TEA difieren, las muestra por separado | `PanelDeRendimiento.test.tsx` |
| CP-WEB-041 | Unitaria | lee el token del fragmento, lo borra de la barra y valida el enlace | `FormularioDeContrasenaNueva.test.tsx` |
| CP-WEB-042 | Unitaria | sin token en el enlace muestra que no es válido sin llamar al servidor | `FormularioDeContrasenaNueva.test.tsx` |
| CP-WEB-043 | Unitaria | un enlace caducado o usado (410) muestra que no es válido | `FormularioDeContrasenaNueva.test.tsx` |
| CP-WEB-044 | Unitaria | sin conexión al validar deja intentar el cambio | `FormularioDeContrasenaNueva.test.tsx` |
| CP-WEB-045 | Unitaria | no envía una contraseña que incumple la política ni dos que no coinciden | `FormularioDeContrasenaNueva.test.tsx` |
| CP-WEB-046 | Unitaria | guarda la contraseña y avisa que el segundo factor sigue siendo necesario | `FormularioDeContrasenaNueva.test.tsx` |
| CP-WEB-047 | Unitaria | si el enlace caduca mientras escribe, muestra que ya no es válido | `FormularioDeContrasenaNueva.test.tsx` |
| CP-WEB-048 | Unitaria | traduce el rechazo de la política y el fallo de conexión al guardar | `FormularioDeContrasenaNueva.test.tsx` |
| CP-WEB-049 | Unitaria | permite ver la contraseña que escribe | `FormularioDeContrasenaNueva.test.tsx` |
| CP-WEB-050 | Unitaria | pide el enlace y muestra la misma confirmación para cualquier correo | `FormularioDeRecuperacion.test.tsx` |
| CP-WEB-051 | Unitaria | no llama al servidor con un correo mal escrito | `FormularioDeRecuperacion.test.tsx` |
| CP-WEB-052 | Unitaria | distingue un fallo de conexión de un correo rechazado | `FormularioDeRecuperacion.test.tsx` |
| CP-WEB-053 | Unitaria | muestra el formulario de 6 dígitos para el código 2FA | `PasoDeSegundoFactor.test.tsx` |
| CP-WEB-054 | Unitaria | permite escribir los 6 dígitos y enviar el código | `PasoDeSegundoFactor.test.tsx` |
| CP-WEB-055 | Unitaria | muestra mensaje de error cuando error es enviado | `PasoDeSegundoFactor.test.tsx` |
| CP-WEB-056 | Unitaria | permite seleccionar otro método de verificación si onSeleccionarMetodo está definido | `PasoDeSegundoFactor.test.tsx` |
| CP-WEB-057 | Unitaria | permite reenviar el código para canales OTP (Correo/SMS) | `PasoDeSegundoFactor.test.tsx` |
| CP-WEB-058 | Unitaria | muestra las 3 opciones de segundo factor | `SelectorMetodoSegundoFactor.test.tsx` |
| CP-WEB-059 | Unitaria | llama a onSeleccionarMetodo al hacer clic en una opción | `SelectorMetodoSegundoFactor.test.tsx` |
| CP-WEB-060 | Unitaria | deshabilita los botones cuando deshabilitado es true | `SelectorMetodoSegundoFactor.test.tsx` |
| CP-WEB-061 | Unitaria | pide la cámara y muestra el marco guía cuando el permiso se concede | `CapturaDeDocumento.test.tsx` |
| CP-WEB-062 | Unitaria | si el permiso se deniega, avisa en vez de dejar la pantalla en blanco | `CapturaDeDocumento.test.tsx` |
| CP-WEB-063 | Unitaria | tomar la foto pasa a la vista de revisión | `CapturaDeDocumento.test.tsx` |
| CP-WEB-064 | Unitaria | volver a tomar regresa a la cámara sin pedir permiso otra vez | `CapturaDeDocumento.test.tsx` |
| CP-WEB-065 | Unitaria | usar esta foto la sube, la manda evaluar y avisa que terminó si se acepta | `CapturaDeDocumento.test.tsx` |
| CP-WEB-066 | Unitaria | si la foto se rechaza, dice el motivo concreto y vuelve a la cámara (escenario 3) | `CapturaDeDocumento.test.tsx` |
| CP-WEB-067 | Unitaria | si la solicitud se deriva a revisión, lo avisa hacia arriba (escenarios 4 y 5) | `CapturaDeDocumento.test.tsx` |
| CP-WEB-068 | Unitaria | el mensaje de rechazo dice qué repetir para cada motivo | `CapturaDeDocumento.test.tsx` |
| CP-WEB-069 | Unitaria | si la subida falla, avisa y deja reintentar sin perder la foto tomada | `CapturaDeDocumento.test.tsx` |
| CP-WEB-070 | Unitaria | el enlace para subir un archivo está disponible aunque la cámara funcione | `CapturaDeDocumento.test.tsx` |
| CP-WEB-071 | Unitaria | cambiar a archivo detiene la cámara y muestra la zona de carga | `CapturaDeDocumento.test.tsx` |
| CP-WEB-072 | Unitaria | un archivo también sirve para quien no tiene cámara | `CapturaDeDocumento.test.tsx` |
| CP-WEB-073 | Unitaria | un archivo JPG válido pasa a la vista de revisión | `CapturaDeDocumento.test.tsx` |
| CP-WEB-074 | Unitaria | un PDF ya no se acepta: kyc-service no puede analizarlo (ADR-0028) | `CapturaDeDocumento.test.tsx` |
| CP-WEB-075 | Unitaria | rechaza un tipo de archivo que el backend no acepta | `CapturaDeDocumento.test.tsx` |
| CP-WEB-076 | Unitaria | rechaza un archivo que pesa más de 5 MB | `CapturaDeDocumento.test.tsx` |
| CP-WEB-077 | Unitaria | sube el archivo elegido con su propia extensión | `CapturaDeDocumento.test.tsx` |
| CP-WEB-078 | Unitaria | volver a la cámara desde archivo pide el permiso de nuevo | `CapturaDeDocumento.test.tsx` |
| CP-WEB-079 | Unitaria | muestra el estado de carga inicial de los modelos de IA | `CapturaDeSelfie.test.tsx` |
| CP-WEB-080 | Unitaria | muestra lo leído con el número enmascarado, listo para confirmar | `ConfirmacionDeDatosDelDni.test.tsx` |
| CP-WEB-081 | Unitaria | confirma lo corregido sin enviar el número si no se tocó | `ConfirmacionDeDatosDelDni.test.tsx` |
| CP-WEB-082 | Unitaria | permite reescribir el número entero si el leído está mal | `ConfirmacionDeDatosDelDni.test.tsx` |
| CP-WEB-083 | Unitaria | pide la fecha de emisión si el OCR no la encontró | `ConfirmacionDeDatosDelDni.test.tsx` |
| CP-WEB-084 | Unitaria | si no se pudo leer, pide volver a fotografiar el reverso con los intentos que quedan | `ConfirmacionDeDatosDelDni.test.tsx` |
| CP-WEB-085 | Unitaria | si la solicitud se deriva al leer, lo avisa hacia arriba | `ConfirmacionDeDatosDelDni.test.tsx` |
| CP-WEB-086 | Unitaria | muestra el error del servidor si la lectura falla | `ConfirmacionDeDatosDelDni.test.tsx` |
| CP-WEB-087 | Unitaria | deshabilita el boton Continuar hasta que se aceptan ambos checks | `ConsentimientoBiometrico.test.tsx` |
| CP-WEB-088 | Unitaria | usa la convención peruana: punto decimal y coma de miles | `producto.test.ts` |
| CP-WEB-089 | Unitaria | proyecta a un año exactamente la TREA, porque la TREA ya es efectiva | `producto.test.ts` |
| CP-WEB-090 | Unitaria | el rendimiento es proporcional al capital | `producto.test.ts` |
| CP-WEB-091 | Unitaria | sin capital no hay rendimiento | `producto.test.ts` |
| CP-WEB-092 | Unitaria | ErrorDeApi porCampo mapea errores por nombre de campo | `api.test.ts` |
| CP-WEB-093 | Unitaria | seleccionarMetodoSegundoFactor hace un POST al endpoint de método de 2FA | `api.test.ts` |
| CP-WEB-094 | Unitaria | generarDesafioCodigo hace un POST para solicitar un desafío OTP | `api.test.ts` |
| CP-WEB-095 | Unitaria | verificarDesafioCodigo valida un código OTP | `api.test.ts` |
| CP-WEB-096 | Unitaria | reenviarCodigoRegistro y verificarCodigoRegistro funcionan correctamente | `api.test.ts` |
| CP-WEB-097 | Unitaria | consultarTitular y consultarCuenta hacen GET requests | `api.test.ts` |
| CP-WEB-098 | Unitaria | registrar, presentarCredenciales y verificarSegundoFactor hacen POST | `api.test.ts` |
| CP-WEB-099 | Unitaria | solicitarUrlDeSubida devuelve el formulario y subirDocumento lo envía por POST | `api.test.ts` |
| CP-WEB-100 | Unitaria | evaluarCaptura, extraerDatosDelDni y confirmarDatosDelDni llaman a sus rutas por POST | `api.test.ts` |
| CP-WEB-101 | Unitaria | la recuperacion de contrasena manda el token en el cuerpo, nunca en la URL | `api.test.ts` |
| CP-WEB-102 | Unitaria | lanza ErrorDeApi cuando la respuesta HTTP no es ok | `api.test.ts` |
| CP-WEB-103 | Unitaria | lanza ErrorDeApi por fallo de red | `api.test.ts` |
| CP-WEB-104 | Unitaria | agrupa los miles y fija dos decimales sin pasar por coma flotante | `banca.test.ts` |
| CP-WEB-105 | Unitaria | normaliza lo que escribe la persona o lo rechaza | `banca.test.ts` |
| CP-WEB-106 | Unitaria | muestra la fecha en hora de Lima aunque llegue en UTC | `banca.test.ts` |
| CP-WEB-107 | Unitaria | cada clave de idempotencia es distinta | `banca.test.ts` |
| CP-WEB-108 | Unitaria | un token a punto de caducar no se usa: margen de 30 segundos | `banca.test.ts` |
| CP-WEB-109 | Unitaria | sin token en memoria, lo recupera con la cookie de renovación | `banca.test.ts` |
| CP-WEB-110 | Unitaria | si la renovación falla, no hay sesión | `banca.test.ts` |
| CP-WEB-111 | Unitaria | lee el usuario del token y tolera tokens mal formados | `banca.test.ts` |
| CP-WEB-112 | Unitaria | las consultas llevan el token como Bearer | `banca.test.ts` |
| CP-WEB-113 | Unitaria | el rendimiento se consulta con el token | `banca.test.ts` |
| CP-WEB-114 | Unitaria | transferir y depositar viajan con la clave de idempotencia | `banca.test.ts` |
| CP-WEB-115 | Unitaria | cerrar sesión es un DELETE que acepta 204 sin cuerpo | `banca.test.ts` |
| CP-WEB-116 | Unitaria | renovar es un POST con credenciales para que viaje la cookie | `banca.test.ts` |
| CP-WEB-117 | Unitaria | el comprobante en PDF se pide con el token y falla con un ErrorDeApi | `banca.test.ts` |
| CP-WEB-118 | Unitaria | la confirmacion con segundo factor va a identity con el token de acceso | `banca.test.ts` |
| CP-WEB-119 | Unitaria | redondea al entero más cercano cuando no hay empate | `formato.test.ts` |
| CP-WEB-120 | Unitaria | en la mitad exacta va al par, no siempre hacia arriba | `formato.test.ts` |
| CP-WEB-121 | Unitaria | con negativos es simétrico, como BigDecimal | `formato.test.ts` |
| CP-WEB-122 | Unitaria | rechaza un divisor cero o negativo | `formato.test.ts` |
| CP-WEB-123 | Unitaria | redondea con HALF_EVEN en lugar de truncar | `formato.test.ts` |
| CP-WEB-124 | Unitaria | en el medio céntimo exacto va al céntimo par | `formato.test.ts` |
| CP-WEB-125 | Unitaria | usa todos los decimales de la TREA, no solo dos | `formato.test.ts` |
| CP-WEB-126 | Unitaria | sin saldo no hay rendimiento | `formato.test.ts` |
| CP-WEB-127 | Unitaria | rechaza un importe o una tasa que no son decimales | `formato.test.ts` |
| CP-WEB-128 | Unitaria | aplica la TREA una sola vez, porque ya es efectiva | `formato.test.ts` |
| CP-WEB-129 | Unitaria | es exacta donde la coma flotante se desvía | `formato.test.ts` |
| CP-WEB-130 | Unitaria | redondea con HALF_EVEN la fracción de céntimo | `formato.test.ts` |
| CP-WEB-131 | Unitaria | con TREA cero devuelve el mismo capital | `formato.test.ts` |
| CP-WEB-132 | Unitaria | devuelve null si la TREA o el capital no son decimales | `formato.test.ts` |
| CP-WEB-133 | Unitaria | calcula la parte usada del límite, en entero | `formato.test.ts` |
| CP-WEB-134 | Unitaria | nunca pasa de 100 ni baja de 0 | `formato.test.ts` |
| CP-WEB-135 | Unitaria | un límite cero o un texto que no es importe dan 0 | `formato.test.ts` |
| CP-WEB-136 | Unitaria | lee el token del fragmento, con o sin almohadilla | `recuperacion.test.ts` |
| CP-WEB-137 | Unitaria | acepta los caracteres del alfabeto Base64 URL | `recuperacion.test.ts` |
| CP-WEB-138 | Unitaria | devuelve null si falta, está vacío o no tiene forma de token | `recuperacion.test.ts` |
| CP-WEB-139 | Navegador (E2E) | ${ruta} no presenta violaciones de WCAG 2.1 AA *(se repite por cada valor del bucle)* | `accesibilidad.a11y.spec.ts` |
| CP-WEB-140 | Navegador (E2E) | muestra el número, el CCI y el saldo cuando la cuenta ya existe | `cuenta-lista.spec.ts` |
| CP-WEB-141 | Navegador (E2E) | mientras se abre, avisa en lugar de dar un error | `cuenta-lista.spec.ts` |
| CP-WEB-142 | Navegador (E2E) | el CCI se puede copiar al portapapeles | `cuenta-lista.spec.ts` |
| CP-WEB-143 | Navegador (E2E) | sin titular en la URL no se queda cargando para siempre | `cuenta-lista.spec.ts` |
| CP-WEB-144 | Navegador (E2E) | ningún enlace apunta al vacío | `enlaces.spec.ts` |
| CP-WEB-145 | Navegador (E2E) | cada acción del diseño es un enlace de verdad | `enlaces.spec.ts` |
| CP-WEB-146 | Navegador (E2E) | ningún destino devuelve 404 | `enlaces.spec.ts` |
| CP-WEB-147 | Navegador (E2E) | lo que aún no existe lleva a la página de pendientes | `enlaces.spec.ts` |
| CP-WEB-148 | Navegador (E2E) | «Abrir cuenta» lleva al registro | `enlaces.spec.ts` |
| CP-WEB-149 | Navegador (E2E) | el primer paso muestra lo que aprueba el diseño | `ingreso.spec.ts` |
| CP-WEB-150 | Navegador (E2E) | la contraseña se puede mostrar y volver a ocultar | `ingreso.spec.ts` |
| CP-WEB-151 | Navegador (E2E) | tras las credenciales correctas pide el código de seis dígitos | `ingreso.spec.ts` |
| CP-WEB-152 | Navegador (E2E) | la primera vez ofrece el QR para dar de alta el segundo factor | `ingreso.spec.ts` |
| CP-WEB-153 | Navegador (E2E) | pegar el código entero lo reparte entre las seis casillas | `ingreso.spec.ts` |
| CP-WEB-154 | Navegador (E2E) | el ingreso pausado muestra la cuenta atrás y desactiva el botón | `ingreso.spec.ts` |
| CP-WEB-155 | Navegador (E2E) | un error de credenciales no revela si el correo existe | `ingreso.spec.ts` |
| CP-WEB-156 | Navegador (E2E) | saluda, muestra el saldo con su TREA y los movimientos | `panel.spec.ts` |
| CP-WEB-157 | Navegador (E2E) | sin cuenta abierta ofrece continuar la verificación | `panel.spec.ts` |
| CP-WEB-158 | Navegador (E2E) | con la solicitud rechazada lo dice, sin mandarle a registrarse otra vez | `panel.spec.ts` |
| CP-WEB-159 | Navegador (E2E) | el celular lleva el prefijo +51 fuera del valor que se escribe | `registro.spec.ts` |
| CP-WEB-160 | Navegador (E2E) | al enfocar un campo no aparece un recuadro dentro del marco | `registro.spec.ts` |
| CP-WEB-161 | Navegador (E2E) | marca los campos vacíos sin enviar nada al servidor | `registro.spec.ts` |
| CP-WEB-162 | Navegador (E2E) | no deja abrir una cuenta a un menor de edad | `registro.spec.ts` |
| CP-WEB-163 | Navegador (E2E) | la política de contraseña se cumple requisito a requisito | `registro.spec.ts` |
| CP-WEB-164 | Navegador (E2E) | ${ruta} no desborda ni deja hueco a ${ancho}px *(se repite por cada valor del bucle)* | `responsividad.spec.ts` |
| CP-WEB-165 | Navegador (E2E) | ${ruta} refluye al cambiar el ancho, no se limita a escalar *(se repite por cada valor del bucle)* | `responsividad.spec.ts` |
| CP-WEB-166 | Navegador (E2E) | el zoom del navegador muestra más contenido, no lo mismo más pequeño | `responsividad.spec.ts` |
| CP-WEB-167 | Navegador (E2E) | congela, descongela, apaga las compras por internet y cambia el límite | `tarjeta.spec.ts` |
| CP-WEB-168 | Navegador (E2E) | sin tarjeta todavía lo dice, sin controles | `tarjeta.spec.ts` |
| CP-WEB-169 | Navegador (E2E) | la barra lateral lleva a la tarjeta | `tarjeta.spec.ts` |
| CP-WEB-170 | Navegador (E2E) | no presenta violaciones de WCAG 2.1 AA, tampoco con la tarjeta congelada | `tarjeta.spec.ts` |
| CP-WEB-171 | Usabilidad | la portada dice qué es Ayni y cómo empezar sin desplazarse | `aprendizaje.spec.ts` |
| CP-WEB-172 | Usabilidad | cada campo del registro tiene una etiqueta visible que no desaparece al escribir | `aprendizaje.spec.ts` |
| CP-WEB-173 | Usabilidad | la transferencia anuncia sus límites y el formato antes de pedir nada | `aprendizaje.spec.ts` |
| CP-WEB-174 | Usabilidad | «Iniciar sesión» en el registro lleva al ingreso, no a una página en construcción | `aprendizaje.spec.ts` |
| CP-WEB-175 | Usabilidad | el registro muestra la política de contraseña antes de escribirla | `aprendizaje.spec.ts` |
| CP-WEB-176 | Usabilidad | cada error del registro queda enlazado al campo que hay que corregir | `asistencia.spec.ts` |
| CP-WEB-177 | Usabilidad | si la red falla al ingresar, el mensaje dice qué hacer | `asistencia.spec.ts` |
| CP-WEB-178 | Usabilidad | un error de credenciales se anuncia y deja los datos para reintentar | `asistencia.spec.ts` |
| CP-WEB-179 | Usabilidad | con «reducir movimiento» activado, nada sigue animándose | `inclusividad.spec.ts` |
| CP-WEB-180 | Usabilidad | el ingreso se completa solo con el teclado | `operabilidad.spec.ts` |
| CP-WEB-181 | Usabilidad | el tabulador recorre el registro en el orden en que se lee | `operabilidad.spec.ts` |
| CP-WEB-182 | Usabilidad | ${ruta}: cada parada del tabulador muestra dónde está el foco *(se repite por cada valor del bucle)* | `operabilidad.spec.ts` |
| CP-WEB-183 | Usabilidad | en el celular, cada control mide al menos 24 × 24 px | `operabilidad.spec.ts` |
| CP-WEB-184 | Usabilidad | antes de mover el dinero muestra la operación para revisarla | `proteccion-contra-errores.spec.ts` |
| CP-WEB-185 | Usabilidad | «Corregir» devuelve el formulario con lo que ya se escribió | `proteccion-contra-errores.spec.ts` |
| CP-WEB-186 | Usabilidad | pulsar «Confirmar» dos veces seguidas pide una sola confirmación | `proteccion-contra-errores.spec.ts` |
| CP-WEB-187 | Usabilidad | un importe no válido no avanza, marca el campo y dice cómo arreglarlo | `proteccion-contra-errores.spec.ts` |
