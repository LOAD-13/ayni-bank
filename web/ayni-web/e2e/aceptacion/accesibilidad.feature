# language: es
#
# HU-17 · [NF] Accesibilidad y facilidad de aprendizaje de la interfaz · AYNI-28
# Pruebas de aceptación: AYNI-295
#
# Son pruebas de interfaz, así que corren en el navegador con Playwright y no en la suite
# de Cucumber de los servicios Java. Cada escenario de este fichero tiene una prueba con su
# mismo nombre en accesibilidad.spec.ts, y cada paso es un test.step con su texto exacto:
# la propia suite falla si el fichero y las pruebas dejan de coincidir.

Característica: Accesibilidad de la banca con teclado y lector de pantalla
  Como cliente con discapacidad visual
  Quiero poder operar el banco completo con lector de pantalla y solo con el teclado
  Para gestionar mi dinero de forma autónoma sin depender de nadie

  Antecedentes:
    Dado que Ana tiene la sesión abierta y una cuenta Ayni en soles con S/ 850.00

  Escenario: Transferencia solo con teclado
    Dado que Ana navega usando únicamente el teclado
    Cuando recorre el flujo completo de transferencia
    Entonces puede alcanzar y activar todos los controles mediante tabulación
    Y el foco es visible en todo momento
    Y completa la transferencia sin usar el ratón

  Escenario: Verificación automática en el pipeline
    Dado que una pantalla de la banca tiene una violación de WCAG 2.1 AA
    Cuando se ejecuta el análisis de axe-core del pipeline
    Entonces el análisis informa la violación
    Y la misma pantalla sin la violación pasa el análisis

  Escenario: Mensaje de error comprensible
    Dado que Ana está en el formulario de transferencia
    Cuando escribe un importe de S/ 900.00, superior a su saldo, e intenta continuar
    Entonces el sistema explica que el saldo es insuficiente e indica el saldo disponible
    Y el mensaje es anunciado por el lector de pantalla
