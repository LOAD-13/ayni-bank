# ADR-0008 · Respuesta indistinguible en el registro

**Estado:** aceptada · **Fecha:** 30 de agosto de 2026 · **Ámbito:** `ayni-identity-service`

## Contexto

`HU-01` describe dos comportamientos que, tal como están redactados, **no pueden cumplirse a la vez**.

El escenario 1 dice que tras registrarse «el sistema redirige al visitante al paso de verificación de
identidad». El escenario 2 dice que ante un correo ya registrado «el sistema muestra un mensaje
genérico que no revela si el correo existe». El prototipo aprobado fija incluso la redacción:
*«Si el correo está disponible, te enviaremos un enlace de verificación»*.

La contradicción está en que **redirigir exige devolver un identificador con el que continuar**. Si
ese identificador solo existe cuando el registro fue real, su ausencia delata que la cuenta ya
existía, por muy neutro que sea el texto que lo acompaña.

Por qué importa: un endpoint de registro que responde distinto según si el correo existe es un
**oráculo de enumeración**. Cualquiera puede recorrer una lista de correos filtrada de otra brecha y
obtener la lista de clientes de Ayni. Eso es información vendible y es el primer insumo de una
campaña de *phishing* dirigida: saber que alguien es cliente de un banco concreto multiplica la
eficacia del engaño. Está recogido en OWASP ASVS bajo *identity enumeration*, y el proyecto se
comprometió al nivel 2.

## Opciones evaluadas

**A. Devolver 409 Conflict ante un correo existente.** Es lo que hace la mayoría de tutoriales y lo
más simple de implementar. Es exactamente el oráculo descrito. Descartada.

**B. Devolver el mismo cuerpo pero sin crear nada.** Elimina la diferencia visible, pero deja dos
fugas. La primera, el identificador: hay que devolver alguno, y si es inventado, la siguiente
petición sobre esa solicitud responde «no existe» y se acabó la indistinguibilidad. La segunda, el
tiempo: el camino normal deriva la contraseña con Argon2id y cuesta decenas de milisegundos,
mientras que el camino de correo duplicado no derivaría nada y respondería en pocos. **Basta un
cronómetro.** Insuficiente.

**C. Exigir confirmación por correo antes de continuar el onboarding.** Resuelve la contradicción de
raíz y es lo que hacen los bancos reales. Pero añade tabla de tokens, endpoint de confirmación,
plantilla de correo y un paso más en el flujo: alcance que `HU-01` no contempla ni en sus criterios
ni en sus ocho subtareas, en un sprint que ya está sobrecomprometido al −15 %.

**D. Solicitud señuelo persistida, con derivación de contraseña en ambos caminos.** La elegida.

## Decisión

**El registro responde siempre `202 Accepted` con el mismo cuerpo, exista o no el correo.**

- Si el correo es nuevo: se crea el usuario en `PENDIENTE_VERIFICACION` y se abre su solicitud real.
- Si el correo ya existe: **no se crea ningún usuario**, se abre una **solicitud señuelo** con
  `usuario_id` nulo, y se avisa por correo al titular legítimo de que alguien intentó registrarse con
  su dirección.

Dos detalles sin los cuales la decisión no funciona:

1. **El señuelo se persiste de verdad.** Devolver un UUID inventado sin escribir nada haría que la
   siguiente petición sobre esa solicitud respondiera «no existe». Por eso
   `solicitud_onboarding.usuario_id` es nulable, y ese es el único motivo.

2. **La contraseña se deriva también en el camino del duplicado**, aunque el resultado se descarte.
   Sin esa derivación la respuesta llegaría en pocos milisegundos frente a las decenas del camino
   normal. Devolver el mismo cuerpo por un canal y delatarlo por el reloj no protege nada.

Medido en local tras calentamiento: mediana de **53 ms** en ambos caminos, con rangos solapados.

## Consecuencias

**A favor.** El endpoint deja de ser un oráculo de enumeración. El titular legítimo se entera del
intento, que es el único con derecho a saberlo. Los cuatro escenarios de `HU-01` quedan satisfechos
sin ampliar el alcance del sprint.

**En contra.** Se escriben filas que no corresponden a ningún registro real: la tabla
`solicitud_onboarding` acumula señuelos. Se mitiga con la caducidad de siete días, que existe de
todos modos por minimización de datos.

El coste de Argon2id se paga también en el camino del duplicado. Es deliberado, y significa que un
atacante puede consumir CPU enviando registros repetidos. **Eso lo contiene la limitación de tasa del
gateway, no este diseño**, y es la razón por la que el contrato declara un `429` para este endpoint.

`HU-01` mantiene una debilidad conocida: alguien puede registrar un correo que no le pertenece y
avanzar hasta el KYC. No llegará a abrir cuenta —el KYC verifica identidad contra un DNI real— pero
ocupa la dirección. **La solución correcta sigue siendo la opción C**, confirmación por correo.

## Pendiente

Cuando se implemente `HU-13` (notificaciones por *outbox*), evaluar la opción C como refuerzo. Exige
además una plantilla `INTENTO_DE_REGISTRO` que hoy no está en el catálogo de
`V1__catalogo_de_plantillas.sql`: el aviso al titular legítimo se registra en el log a la espera de
esa plantilla.

Queda igualmente pendiente **corregir la redacción del escenario 1 de `HU-01` en Jira**, que sigue
describiendo una redirección inmediata que este ADR reinterpreta.
