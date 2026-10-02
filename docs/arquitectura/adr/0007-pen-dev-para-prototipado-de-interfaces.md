# ADR-0007 · pen.dev como herramienta de prototipado de interfaces

**Estado:** Aceptada
**Fecha:** 23 de agosto de 2026

---

## Contexto

El curso exige entregar **mockups y un prototipo interactivo** de las pantallas del MVP, y exige
además declarar explícitamente qué herramienta se usa. La sesión 4 fijó los tres niveles de
fidelidad —wireframe, mockup, prototipo interactivo— y pidió que **cada pantalla responda a un
criterio de aceptación de una Historia de Usuario**.

El equipo no tiene diseñador. Las interfaces las construyen los mismos desarrolladores, en
paralelo al backend, y `web/` está vacío: el diseño precede al código, no lo documenta después.

Existe además una restricción de coherencia: el sistema de diseño ya está definido desde el Sprint
0 (`AYNI-39`) con paleta, escalas y **ratios de contraste calculados**, y `axe-core` verifica WCAG
2.1 AA en el pipeline. Una herramienta que no permita trabajar con esos tokens obliga a mantener la
paleta a mano en dos sitios, que es exactamente como se desincronizan.

## Opciones consideradas

**A. Figma.** El estándar de la industria, y lo que propuso todo el aula. Prototipado interactivo
maduro, colaboración en tiempo real, enorme comunidad. En contra: el plan gratuito limita a tres
archivos y restringe funciones de sistemas de diseño; la exportación a código es aproximada y
requiere complementos; y el trabajo es manual, lo que en un equipo sin diseñador y con seis
personas repartidas significa inconsistencia entre pantallas.

**B. Herramientas de wireframe de baja fidelidad** (Balsamiq, Excalidraw). Rápidas y baratas, pero
entregan wireframes, no mockups. El curso pide fidelidad media-alta con identidad visual completa.

**C. Maquetar directamente en Next.js.** Máxima fidelidad y reutilizable. Pero convierte cada
iteración de diseño en un ciclo de código y despliegue, y obliga a decidir la implementación antes
de haber validado la interfaz. Invierte el orden: es justo lo que el prototipado evita.

**D. pen.dev.** Lienzo infinito con jerarquía de nodos, **variables de documento** para los tokens,
componentes reutilizables, prototipado interactivo, exportación a React/Tailwind, e importación de
páginas web reales **como capas editables**. Operable por agente mediante MCP.

## Decisión

**Opción D, pen.dev.**

Cuatro razones, en orden de peso:

1. **Los tokens son variables reales del documento.** La paleta de `docs/marca/design-tokens.md`
   está cargada como 28 variables; un componente enlaza a `$azul-700`, no a un hexadecimal copiado.
   Cambiar el token repinta cuanto lo use. Es la única opción evaluada que mantiene una sola fuente
   de verdad entre la documentación y el diseño.

2. **Es operable por agente.** El diseño se construye con código sobre la API del documento, lo que
   lo hace determinista y repetible: la misma medida, el mismo color y la misma jerarquía en las
   ocho pantallas. Con edición manual, seis personas producen seis interpretaciones del mismo
   botón.

3. **Importa páginas reales como capas editables.** Permite estudiar cómo la banca peruana resuelve
   problemas ya resueltos —jerarquía del acceso a banca por internet, disposición de productos— en
   lugar de partir del folio en blanco.

4. **Exporta a React/Tailwind**, que es la pila de `ayni-web`. El prototipo no es un artefacto
   muerto: es el punto de partida de la implementación.

**Se asume el coste de la novedad.** Es una herramienta menos conocida que Figma y el equipo no la
domina, de modo que la curva de aprendizaje es real y no hay comunidad a la que recurrir. Se acepta
porque el trabajo de construcción lo concentra el prototipo y el resultado se exporta a un formato
—React/Tailwind— que sí es estándar. Si la herramienta resultase un obstáculo, el diseño ya
exportado sobrevive a la migración.

## Consecuencias

**A favor.** Una sola fuente de verdad para los tokens. Pantallas consistentes entre sí. El
prototipo alimenta la implementación en vez de duplicarla. Sin coste de licencia.

**En contra.** Dependencia de una herramienta de escritorio propietaria y poco extendida. Los
archivos `.pen` son binarios: no se pueden revisar en un Pull Request ni diferenciar entre
versiones. Si el equipo tuviera que colaborar en simultáneo sobre el mismo archivo, esta decisión
habría que revisarla.

**Mitigación.** El `.pen` es el archivo de trabajo, no el entregable: lo que se entrega y lo que se
archiva son los **PNG exportados**. La documentación que sí se versiona —tokens, especificación de
pantallas y este ADR— vive en el repositorio en Markdown, legible y diferenciable.

## Reglas de trabajo derivadas

1. **El archivo `.pen`, los assets y los PNG exportados quedan fuera del repositorio**, en
   `ayni-bank-workspace-design/`, igual que los entregables del curso. Solo entra al repositorio la
   documentación técnica en Markdown.

2. **El contexto se entrega al agente por escrito**, en `ayni-bank-workspace-design/contexto/`. Ese
   directorio incluye `05-datos-producto.md`, que **fija las cifras aprobadas y las afirmaciones
   prohibidas** y manda sobre cualquier otro documento.

3. **Ninguna cifra se inventa en una interfaz.** La TREA, los tipos de cambio y los spreads salen
   de las migraciones de Flyway ya aplicadas. Un mockup que muestra una TREA distinta de la que
   devuelve el sistema es un defecto, no una licencia gráfica.

4. **Ayni Bank no afirma ser una entidad supervisada.** No es real. Están prohibidos «Supervisado
   por la SBS», «Cubierto por el FSD», «Miembro de ASBANC», los certificados y los identificadores
   fiscales inventados. Se admite «diseñado conforme al marco normativo de la SBS», que es cierto.

5. **Sin marcas de terceros en las interfaces**, ni en textos ni dentro de las imágenes importadas.
   Los assets cosechados de sitios reales se revisan antes de usarse: varios traen logotipos y
   carteles de otra entidad dentro de la propia imagen.

6. **Cada pantalla declara qué criterio de aceptación cubre.** La tabla de trazabilidad vive en
   `contexto/03-pantallas.md` y forma parte del entregable.

---

Ver también: [ADR-0006](0006-activacion-escalonada-de-las-puertas-de-calidad.md) ·
[`docs/marca/design-tokens.md`](../../marca/design-tokens.md)
