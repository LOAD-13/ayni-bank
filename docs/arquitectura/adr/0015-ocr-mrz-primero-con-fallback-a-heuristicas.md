# ADR-0015 · OCR: leer el MRZ del reverso primero, con fallback a heurísticas del anverso

- **Estado:** aceptado
- **Fecha:** 6 de septiembre de 2026
- **Historias afectadas:** HU-02 (verificación de identidad)
- **Reemplaza a:** ninguno (complementa [ADR-0009](0009-identidad-declarada-antes-del-ocr.md),
  [ADR-0012](0012-deteccion-de-dni-por-geometria-con-opencv.md) y
  [ADR-0014](0014-opencv-contrib-python-en-vez-de-headless.md))

## Contexto

La subtarea 5 de AYNI-13 pide extraer los datos de identidad (DNI, nombres, apellidos, fecha
de nacimiento, sexo) del anverso y reverso del documento con PaddleOCR. Como en las subtareas
anteriores, no hay especificación técnica previa de cómo parsear el texto crudo que devuelve el
motor de OCR hacia los campos estructurados que define `ExtractedIdentityData` en el contrato
(`contracts/kyc-service.openapi.yaml`).

El DNI peruano (versión electrónica) trae en el reverso una **zona de lectura mecánica (MRZ)**
de 3 líneas de 30 caracteres, formato TD1 del estándar ICAO 9303 (el mismo que usan pasaportes
y muchas tarjetas de identidad). Este formato tiene posiciones fijas para cada campo y **dígitos
verificadores (checksum)** calculados sobre el número de documento, la fecha de nacimiento, la
fecha de caducidad y un campo compuesto final.

El ADR-0009 ya señaló el problema central: *"un OCR no falla devolviendo un error: falla
devolviendo un resultado plausible"* — un `8` leído como `6` produce un DNI que existe, pasa
cualquier validación de formato, y pertenece a otra persona.

## Decisión

**Se intenta leer el MRZ primero. Solo si no se encuentra o su checksum no valida, se cae a
heurísticas de texto libre sobre el anverso.**

1. `mrz_td1.py` implementa el parser TD1 completo (posiciones de cada campo, cálculo de
   checksum con el algoritmo ICAO estándar: valor numérico por carácter, pesos cíclicos
   `[7, 3, 1]`, módulo 10) y `encontrar_lineas_mrz` para localizar las 3 líneas dentro del texto
   crudo que devuelve OCR (tolerando algo de ruido: longitud 28-32 en vez de exactamente 30).
2. Si las 3 líneas se encuentran y **todos** los checksums coinciden, se usan esos datos
   (`fuente=MRZ`, `confiable=True`) — es la única fuente donde se puede *confirmar*
   objetivamente que el OCR leyó bien, no solo asumirlo.
3. Si no, `heuristicas_anverso.py` busca en el texto del anverso las etiquetas conocidas
   ("APELLIDOS", "NOMBRES", "SEXO", "FECHA DE NACIMIENTO") y un patrón de 8 dígitos para el
   DNI. Sin checksum: el resultado siempre se marca `fuente=HEURISTICA_ANVERSO`,
   `confiable=False`.
4. `DatosIdentidadExtraidos.confiable` queda disponible para que capas posteriores (el caso de
   uso que compare contra la identidad declarada, ver ADR-0009) puedan aplicar un criterio más
   estricto cuando el dato no viene del MRZ.

**Diseño de aislamiento:** `MotorOcrPort` (protocolo interno) separa la orquestación
(MRZ → fallback) de la librería PaddleOCR en sí (`MotorOcrPaddleOCR`). Permite testear la
lógica de decisión con un motor falso en milisegundos, sin cargar el modelo real (~segundos) en
cada test — los tests de `mrz_td1.py` y `heuristicas_anverso.py` prueban el parseo en sí de
forma aislada.

## Hallazgo técnico: `enable_mkldnn=False`

Al instanciar `PaddleOCR()` con su configuración por defecto en el entorno de desarrollo
(Windows/x86_64, CPU sin GPU), la inferencia falla con:

```
NotImplementedError: (Unimplemented) ConvertPirAttribute2RuntimeAttribute not support
[pir::ArrayAttribute<pir::DoubleAttribute>]
```

Es un problema del backend oneDNN de PaddlePaddle 3.3.1 en CPU, no de este código. Se resuelve
pasando `enable_mkldnn=False` al construir `PaddleOCR`. Costo: algo de rendimiento de
inferencia; beneficio: el servicio funciona en el entorno de desarrollo y en el CI
(`ubuntu-latest`, x86_64). **Pendiente de revalidar en el contenedor de producción real**
(ARM64, Raspberry Pi 5 / Oracle Ampere, ver ADR-0005): si oneDNN funciona correctamente ahí,
se puede reevaluar activarlo por rendimiento.

## Alternativas evaluadas

**Solo heurísticas sobre el anverso, ignorando el MRZ.** Es lo que se hace en varios tutoriales
de OCR de DNI, pero renuncia a la única señal que permite *validar* la lectura en vez de solo
confiar en ella. Se descarta: el ADR-0009 ya estableció que el proyecto no acepta lecturas
plausibles-pero-no-verificadas cuando hay una alternativa mejor disponible.

**Solo MRZ, sin fallback.** Más simple, pero deja sin datos cualquier caso donde el reverso no
se pueda leer bien (mala iluminación, MRZ dañado, foto de baja calidad que igual pasó la
subtarea 4 con un umbral heurístico no calibrado). El fallback, aunque menos confiable, es
mejor que no tener nada — la decisión final de aprobar/rechazar/revisión-manual (fuera del
alcance de esta subtarea) puede usar `confiable=False` para ser más exigente.

## Consecuencias

**A favor**

- El MRZ, cuando se lee bien, da datos verificables por checksum — la fuente más confiable
  posible sin depender de una base de datos externa (RENIEC solo está simulado, ver
  `diseno-base.md` §12).
- El fallback evita que un reverso mal fotografiado bloquee todo el flujo.
- Diseño testeable sin depender del modelo pesado de PaddleOCR en cada corrida de tests.

**En contra**

- Las heurísticas del anverso son frágiles (dependen del layout visual exacto, sin dataset de
  validación — mismo riesgo ya señalado en ADR-0012/0013, R-01/R-02 de `sprint-2.md`).
- El parser MRZ asume el formato TD1 estándar; si RENIEC emitiera una variante distinta en
  futuras versiones del DNI, requeriría ajuste.
- `enable_mkldnn=False` es una mitigación de un bug de terceros, no una elección de diseño;
  requiere revisión cuando cambie la versión de PaddlePaddle o el entorno de despliegue.

## Pendiente

- Revalidar si `enable_mkldnn=True` funciona en el contenedor ARM64 de producción.
- Medir con un conjunto de control (cuando exista) qué fracción de casos reales llega al
  fallback de heurísticas en vez del MRZ.
- Definir en el caso de uso de integración (fuera de esta subtarea) qué hacer cuando
  `confiable=False`: ¿aceptar igual, exigir revisión manual siempre, o algo intermedio?
