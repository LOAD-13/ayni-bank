# language: es
#
# HU-01 · Registro de usuario · AYNI-12
#
# Los cuatro escenarios estan copiados de la historia tal como el docente los
# aprobo en Jira. Se traducen linea a linea a propósito: cuando la historia y el
# codigo dejan de coincidir, esta prueba falla y la discrepancia se ve en el
# build en lugar de descubrirse en la revision de sprint.
#
# Se ejecutan contra el caso de uso con adaptadores en memoria, no contra HTTP.
# Lo que la historia describe es comportamiento de negocio —que se cree el
# usuario, que la respuesta no delate la cuenta, que no se guarde nada si falta
# el consentimiento—, y eso no cambia segun por donde entre la peticion. Levantar
# Spring y una base de datos para comprobarlo anadiria minutos al build y
# fragilidad, sin comprobar ni una regla mas.

Característica: Registro de un visitante en Ayni Bank
  Como visitante
  Quiero registrarme con mi correo y una contraseña segura
  Para empezar el proceso de apertura de mi cuenta

  Antecedentes:
    Dado que hoy es el 30 de agosto de 2026
    Y que no hay ninguna cuenta registrada con el correo "ana.quispe@example.pe"

  Escenario: Registro exitoso con datos válidos
    Cuando me registro con el correo "ana.quispe@example.pe" y la contraseña "Cont!rasena2026#"
    Entonces el sistema crea mi usuario en estado "PENDIENTE_VERIFICACION"
    Y el sistema guarda mi contraseña derivada con Argon2id, nunca en claro
    Y el sistema abre una solicitud de onboarding ligada a mi usuario
    Y el sistema guarda mis datos de identidad para contrastarlos después con el OCR
    Y el sistema me envía el correo de bienvenida

  Escenario: El correo ya está registrado
    Dado que ya existe una cuenta con el correo "ana.quispe@example.pe"
    Cuando me registro con el correo "ana.quispe@example.pe" y la contraseña "Cont!rasena2026#"
    Entonces el sistema no crea ningún usuario duplicado
    Y la respuesta es indistinguible de un registro correcto
    Y el sistema deriva la contraseña igualmente, para que el cronómetro no delate la cuenta
    Y el sistema avisa por correo al titular legítimo
    Y el sistema no guarda ningún dato personal de ese intento

  Esquema del escenario: La contraseña no cumple la política
    Cuando intento registrarme con la contraseña "<contraseña>"
    Entonces el sistema rechaza el registro indicando que falta "<requisito>"
    Y el sistema no crea ningún usuario

    Ejemplos:
      | contraseña         | requisito      |
      | Corta1!            | LONGITUD_MINIMA |
      | contrasenalarga1!  | MAYUSCULA      |
      | CONTRASENALARGA1!  | MINUSCULA      |
      | ContrasenaLargaSin | DIGITO         |
      | ContrasenaLarga123 | SIMBOLO        |

  Escenario: No se aceptan los términos
    Cuando me registro sin aceptar el tratamiento de mis datos personales
    Entonces el sistema rechaza el registro por falta de consentimiento
    Y el sistema no crea ningún usuario
    Y el sistema no abre ninguna solicitud de onboarding

  # Añadido sobre la historia original: es la regla que impide recoger los datos
  # biométricos de un menor antes de descubrir, ya en el OCR, que lo es.
  Escenario: Un menor de edad no puede abrir una cuenta
    Cuando me registro declarando que nací el 1 de enero de 2015
    Entonces el sistema rechaza el registro por no alcanzar la mayoría de edad
    Y el sistema no crea ningún usuario
