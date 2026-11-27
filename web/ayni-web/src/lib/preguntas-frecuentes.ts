/** Las dudas que aparecen al operar. Las comparten la ayuda de la banca y el centro de ayuda público. */
export const PREGUNTAS_DE_LA_BANCA = [
  {
    pregunta: "¿Cuánto puedo transferir?",
    respuesta:
      "Desde S/ 1.00 hasta S/ 5 000.00 por transferencia, a otra cuenta Ayni o a otro banco con su CCI. No cobramos comisión.",
  },
  {
    pregunta: "¿Por qué me piden un código para transferir?",
    respuesta:
      "Es tu segundo factor: aunque alguien conozca tu contraseña, sin ese código no puede mover tu dinero. Lo eliges en Configuración.",
  },
  {
    pregunta: "Se cortó la conexión al confirmar. ¿Se cobró dos veces?",
    respuesta:
      "No. Cada operación viaja con una clave única: si vuelves a confirmar, recibes el comprobante de la primera en lugar de un segundo cargo.",
  },
  {
    pregunta: "Me equivoqué al escribir los datos. ¿Cómo los cambio?",
    respuesta:
      "Antes de mover el dinero te mostramos el resumen. Pulsa «Corregir» y vuelves al formulario con lo que ya escribiste.",
  },
  {
    pregunta: "¿Dónde veo mi CCI para recibir dinero de otro banco?",
    respuesta: "En el resumen, en el recuadro «Recibir dinero», junto a tu número de cuenta.",
  },
  {
    pregunta: "¿Cómo gano intereses?",
    respuesta:
      "Tu cuenta gana intereses todos los días según su TREA, que ves junto al saldo. Se abonan a fin de mes.",
  },
  {
    pregunta: "¿Qué es el depósito simulado?",
    respuesta:
      "Ayni Bank es un proyecto académico: el depósito añade saldo de prueba para que puedas probar las transferencias. No es dinero real.",
  },
  {
    pregunta: "¿Ayni Bank me pedirá mi contraseña por correo o teléfono?",
    respuesta:
      "Nunca. Ni tu contraseña ni tus códigos. Si alguien te los pide en nombre de Ayni, no los des.",
  },
] as const;

/** Lo que pregunta quien todavía no es cliente. */
export const PREGUNTAS_DE_APERTURA = [
  {
    pregunta: "¿Qué necesito para abrir mi cuenta?",
    respuesta:
      "Tu DNI vigente, un correo y un celular. Tomas una foto de cada lado del DNI y una selfie; la cuenta se abre al terminar, en unos cinco minutos.",
  },
  {
    pregunta: "¿Hay monto mínimo o comisión de mantenimiento?",
    respuesta:
      "No. La Cuenta Ayni no tiene monto mínimo de apertura ni comisión de mantenimiento, por eso su TREA es igual a su TEA.",
  },
  {
    pregunta: "¿Por qué me piden una selfie?",
    respuesta:
      "Para comprobar que quien abre la cuenta es la persona del DNI. Comparamos tu rostro con la foto del documento y verificamos que sea una persona real frente a la cámara.",
  },
  {
    pregunta: "¿Qué hacen con mis datos personales?",
    respuesta:
      "Los usamos para abrir y operar tu cuenta, conforme a la Ley N.º 29733 de protección de datos personales. Las fotos del DNI y la selfie se guardan cifradas.",
  },
  {
    pregunta: "Olvidé mi contraseña. ¿Cómo entro?",
    respuesta:
      "En la pantalla de ingreso pulsa «¿Olvidaste tu contraseña?». Te enviamos un enlace a tu correo que vence en unos minutos y sirve una sola vez.",
  },
] as const;
