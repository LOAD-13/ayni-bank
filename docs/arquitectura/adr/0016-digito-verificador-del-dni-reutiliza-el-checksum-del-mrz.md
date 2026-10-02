# ADR-0016 · El "dígito verificador del DNI" reutiliza el checksum del MRZ

- **Estado:** aceptado
- **Fecha:** 6 de septiembre de 2026
- **Historias afectadas:** HU-02 (verificación de identidad)
- **Reemplaza a:** ninguno (complementa [ADR-0015](0015-ocr-mrz-primero-con-fallback-a-heuristicas.md))

## Contexto

La subtarea 6 de AYNI-13 pide "validar el dígito verificador del número de DNI" (1h estimada).
A diferencia de las subtareas 3-5, esta parecía tener una especificación pública clara y
exacta — pero al revisarla con cuidado, esa suposición era imprecisa y había que corregirla
antes de implementar.

**El DNI peruano, como número de 8 dígitos, no tiene un dígito verificador oficial publicado
por RENIEC.** No existe un algoritmo público y documentado que permita calcular un checksum a
partir de los 8 dígitos y confirmar matemáticamente que un número "tiene forma válida". Son
números correlativos de asignación, sin dígito de control incorporado.

Lo que sí existe, y es oficial, es el dígito verificador del **MRZ** (subtarea 5,
[ADR-0015](0015-ocr-mrz-primero-con-fallback-a-heuristicas.md)): el estándar ICAO 9303 exige
que el número de documento en la zona de lectura mecánica traiga su propio dígito de control,
calculado con el algoritmo módulo-10 de pesos cíclicos `[7, 3, 1]`. Ese código ya se implementó
en `mrz_td1.py` como parte del parseo del MRZ.

Existe además un algoritmo **no oficial** (módulo-11 con pesos específicos) que circula en
proyectos open source peruanos y se usa informalmente en formularios web para "validar" un DNI
de 8 dígitos. No está confirmado por RENIEC como estándar, y no hay garantía de que sea
correcto para todos los DNIs vigentes ni de que siga siéndolo en el futuro.

## Decisión

**Se reutiliza el dígito verificador oficial del MRZ, no el algoritmo no oficial.**

Cambios sobre lo ya implementado en la subtarea 5:

1. Se extrae la función `_checksum` (privada) en una función pública
   `validar_digito_verificador(campo: str, digito_control: str) -> bool` en `mrz_td1.py`,
   reutilizable de forma independiente y testeable por sí sola.
2. `DatosMrz` deja de exponer un único booleano compuesto (`todos_los_checksums_validos`) como
   campo directo; ahora expone los cuatro checksums del TD1 por separado
   (`numero_documento_verificado`, `fecha_nacimiento_verificada`, `fecha_caducidad_verificada`,
   `checksum_compuesto_valido`), y `todos_los_checksums_validos` pasa a ser una `@property`
   derivada (AND de los cuatro) — se mantiene por compatibilidad con el código de la subtarea 5
   que ya la usaba.
3. La granularidad es intencional: permite que un consumidor futuro distinga, por ejemplo, "el
   número de documento se verificó correctamente" de "la fecha de caducidad se leyó mal", en
   vez de solo saber que *algo* falló en el MRZ.

No se implementa el algoritmo módulo-11 no oficial en ningún lado del código.

## Alternativas evaluadas

**Implementar el algoritmo módulo-11 no oficial de todas formas, como validación adicional.**
Se descartó: no hay fuente oficial que lo respalde, y un algoritmo de validación incorrecto es
peor que no tenerlo — podría rechazar DNIs válidos o aceptar formatos que no corresponden a
ningún esquema real, dando una falsa sensación de seguridad. Si en el futuro se encuentra una
fuente oficial de RENIEC que lo confirme, se puede añadir como una validación adicional, no
como sustituto de esta decisión.

**Solo validar que el DNI tenga 8 dígitos numéricos, sin ningún checksum.** Es lo mínimo que se
puede hacer sin inventar un algoritmo, pero desperdicia la validación real que ya existe (el
checksum del MRZ) cuando el dato viene de esa fuente.

## Consecuencias

**A favor**

- Cero código nuevo de bajo nivel: se reutiliza y refina lo que ya existía de la subtarea 5.
- Es la única validación 100% oficial disponible para el número de documento.
- La granularidad de los cuatro checksums es útil más allá de esta subtarea (ej. para
  diagnóstico/logs cuando falla la verificación).

**En contra**

- Esta validación **solo aplica cuando el dato viene del MRZ** (`fuente=MRZ`). Cuando los datos
  vienen del fallback de heurísticas del anverso (`fuente=HEURISTICA_ANVERSO`,
  [ADR-0015](0015-ocr-mrz-primero-con-fallback-a-heuristicas.md)), no hay dígito de control
  disponible para el número extraído por regex — ese camino sigue sin ninguna forma de validar
  el número de DNI matemáticamente. Es una limitación heredada del fallback, no algo que esta
  subtarea pueda resolver sin inventar un algoritmo no confiable.

## Pendiente

- Si en el futuro aparece una fuente oficial de RENIEC (o de la propia SBS/reguladores para
  KYC bancario) que documente un algoritmo de validación para el número de DNI de 8 dígitos
  fuera del MRZ, reevaluar esta decisión.
