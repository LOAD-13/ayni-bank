# ADR-0009 · La identidad se declara antes del OCR y se contrasta después

- **Estado:** aceptado
- **Fecha:** 30 de agosto de 2026
- **Historias afectadas:** HU-01 (registro), HU-02 (verificación de identidad)
- **Reemplaza a:** ninguno

## Contexto

El formulario de registro aprobado con el docente pide seis datos de identidad —nombres,
apellidos, tipo y número de documento, y fecha de nacimiento— antes de que el usuario
suba su DNI. En HU-02, el servicio de KYC extrae del documento esos mismos datos por OCR.

La primera lectura del asunto es que hay una duplicidad: si el OCR va a leer el DNI, pedir
los datos a mano es trabajo doble para el usuario y una fuente de errores de tecleo. Con
ese razonamiento, el formulario de registro solo debería pedir correo, celular y
contraseña, y todo lo demás debería salir del documento.

Esa lectura pasa por alto de qué falla un OCR. Un OCR no falla devolviendo un error: falla
devolviendo un resultado plausible. Un `8` leído como `6` en una foto con reflejo produce
un DNI que existe, que pasa cualquier validación de formato y que pertenece a otra persona.
Si esa lectura es la única fuente del dato, la identidad equivocada se crea en silencio y
nadie se entera hasta que hay un problema legal.

El docente añadió además un requisito que agrava el escenario: debe poder cargarse el DNI
como archivo, porque hay equipos sin cámara o con una cámara mala. Es justo el caso en el
que la calidad de la imagen es peor y el OCR acierta menos.

## Decisión

**Los datos de identidad se declaran en el paso 1 y el OCR de HU-02 no los sustituye: los
contrasta.**

- Lo declarado se guarda en `identity.solicitud_onboarding`, en columnas con sufijo
  `_declarado`.
- Lo verificado se guarda en `identity.persona`, y solo se escribe cuando las dos lecturas
  concuerdan.
- Si discrepan, la solicitud pasa a `EN_REVISION_MANUAL` en lugar de continuar.

Las dos lecturas viven en tablas distintas a propósito. En la misma fila serían
indistinguibles, y la pregunta que hay que poder responder ante una reclamación —«¿este
dato lo dijo el titular o lo leyó una máquina?»— dejaría de tener respuesta.

El número de documento declarado se cifra con **AES-256-GCM** antes de guardarse, igual que
`persona.numero_documento`, conforme al §5.2 del documento de diseño. GCM y no CBC porque
autentica: un byte alterado en la base de datos hace fallar el descifrado en lugar de
devolver basura. El vector de inicialización es aleatorio en cada cifrado, con dos
consecuencias que conviene tener presentes:

- la columna cifrada **no sirve para buscar ni para imponer unicidad**; para eso están los
  cuatro últimos dígitos en claro y, más adelante, la comprobación de duplicados sobre
  `persona`;
- el criptograma lleva el prefijo `v1:`, que identifica el esquema y permite rotar la clave
  descifrando lo antiguo mientras se escribe lo nuevo.

**Las solicitudes señuelo no guardan ninguno de estos datos.** Cuando el correo ya existe
no se crea usuario, así que no hay nada que verificar después, y conservar los datos
personales de un intento sobre una cuenta ajena sería almacenamiento sin finalidad —
contrario al principio de minimización de la Ley N.º 29733. Que esas filas tengan las
columnas vacías no delata nada: la tabla no se expone por ninguna API. Ver
[ADR-0008](0008-respuesta-indistinguible-en-el-registro.md).

Un detalle de orden que no es cosmético: **la identidad se compone antes de comprobar si el
correo existe.** Si se compusiera dentro de la rama del registro nuevo, un documento mal
formado daría 400 con el correo libre y 202 con el correo tomado, y esa diferencia volvería
a delatar qué cuentas existen por la puerta de atrás.

## Alternativas evaluadas

**Pedir los datos solo tras el OCR y dejar que el usuario los corrija.** Es lo que hacen
varias apps. El problema es psicológico y está medido: ante un campo prerrellenado, la
mayoría confirma sin leer. La corrección existe sobre el papel y no ocurre en la práctica,
de modo que el error del OCR sobrevive igual.

**No pedirlos y confiar en el OCR.** Es la opción con menos fricción y la que menos
protege. Sin un segundo término, no hay forma de detectar una lectura errónea.

**Pedirlos y guardarlos directamente en `persona`.** Ahorra columnas, pero mezcla lo
declarado con lo comprobado en la misma fila y hace imposible saber de dónde salió cada
dato. También obligaría a que `persona` existiera antes de la verificación, contradiciendo
lo que dice la migración V2 sobre cuándo se llena.

## Consecuencias

**A favor**

- Un error de OCR se detecta en lugar de propagarse. La solicitud va a revisión manual, que
  es lo correcto cuando la máquina y la persona no coinciden.
- La procedencia de cada dato queda registrada: lo declarado y lo comprobado no se
  confunden.
- La mayoría de edad se comprueba en el paso 1, antes de gastar un OCR sobre el documento
  de alguien que no puede abrir una cuenta.

**En contra**

- El formulario es más largo. Es el coste real de esta decisión y se asume: son seis campos
  frente a la posibilidad de crear una identidad falsa.
- Hay que definir en HU-02 qué se considera «concuerdan». Una comparación exacta de cadenas
  produciría revisiones manuales por una tilde. La tolerancia —normalización de espacios,
  acentos y mayúsculas antes de comparar— se decide al implementar HU-02.
- Aparece una clave de cifrado que gestionar. En desarrollo tiene un valor por defecto en
  `application.yml`; en staging y producción llega como secreto en `AYNI_CIFRADO_CLAVE` y
  no vive en el repositorio.

## Pendiente

- Definir el criterio de coincidencia OCR ↔ declarado en HU-02, incluida la tolerancia.
- Provisionar `AYNI_CIFRADO_CLAVE` como secreto de entorno cuando se despliegue a staging.
