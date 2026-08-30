# ADR-0006 · Activación escalonada de las puertas de calidad

**Estado:** Aceptada
**Fecha:** 23 de agosto de 2026

---

## Contexto

`DEFINITION_OF_DONE.md` exige un conjunto amplio de verificaciones automáticas: ArchUnit, cobertura
de dominio ≥ 80 %, quality gate de SonarCloud, Gitleaks, Trivy, Dependency-Check, Cucumber,
Testcontainers y axe-core.

El Sprint 0 entrega el andamiaje, no funcionalidad. Al cerrarlo el repositorio contiene los cinco
servicios en pie con sus health checks, pero **los paquetes de dominio están vacíos**: no hay
entidades, ni casos de uso, ni reglas de negocio, y por tanto tampoco pruebas que las cubran.

Esto produce una contradicción concreta que apareció al construir el pipeline:

- ArchUnit falla con *«failed to check any classes»* cuando una regla `noClasses()` se evalúa sobre
  un paquete sin clases. La regla no se incumple: es que no hay nada que evaluar.
- El quality gate de SonarCloud rechaza el Pull Request por *«0.0 % Coverage on New Code»*. El
  código nuevo son clases `@SpringBootApplication` y ficheros de configuración: declaraciones sin
  lógica, cuya cobertura no dice nada sobre la calidad de las pruebas.
- Testcontainers, Cucumber y Dependency-Check no tienen nada que verificar todavía.

Hay dos formas de resolverlo, y ambas son malas si se aplican sin criterio.

## Opciones consideradas

**A. Exigir la Definition of Done completa desde el Sprint 0.** Coherente sobre el papel. En la
práctica obliga a escribir pruebas de las clases de arranque de Spring para satisfacer un
porcentaje. Eso produce pruebas que no verifican nada, infla la cobertura y enseña al equipo que la
métrica se negocia. Es peor que no medir.

**B. Desactivar las puertas hasta que «haya código».** Rápido, y el modo habitual de que una
verificación desactivada nunca se vuelva a activar. Al no quedar registrado el motivo ni el momento
de reactivación, la Definition of Done pasa a ser un documento decorativo.

**C. Activación escalonada con disparador explícito.** Cada puerta que hoy no bloquea queda
registrada en este ADR junto al motivo, la condición exacta que la reactiva y el sprint en que
ocurre. Ninguna relajación es silenciosa y ninguna es indefinida.

## Decisión

**Opción C.**

Una puerta relajada sin fecha ni motivo escrito es deuda oculta. Una puerta relajada con disparador
registrado es una decisión de secuenciación, que es lo que realmente es: no estamos rebajando el
estándar, estamos reconociendo que **una verificación sobre un conjunto vacío no verifica nada**.

### Estado de cada puerta al cerrar el Sprint 0

| Puerta | Hoy | Motivo de la relajación | Se activa cuando |
|---|---|---|---|
| **Gitleaks** | 🔴 Bloqueante | — | Ya activa |
| **Trivy** (imágenes) | 🔴 Bloqueante | — | Ya activa |
| **Compilación y pruebas Maven** | 🔴 Bloqueante | — | Ya activa |
| **Cobertura de dominio ≥ 80 %** | 🔴 Bloqueante | — | Ya activa · ver nota abajo |
| **Ruff · mypy `--strict` · pytest** | 🔴 Bloqueante | — | Ya activa |
| **Job agregador `CI completa`** | 🔴 Bloqueante | — | Ya activa |
| **ArchUnit** | 🟡 Activa, con `allowEmptyShould(true)` | Las reglas `noClasses()` sobre `..domain..` no encuentran clases que evaluar y ArchUnit lo trata como error | Se retira la bandera en el Sprint 1, con las primeras entidades de dominio |
| **Quality gate de SonarCloud** | 🟡 Informativo (`continue-on-error`) | Hacerla bloqueante hoy haría fallar todos los PR por «Coverage on New Code» antes de que exista una sola prueba | Sprint 1, en cuanto el log reporte la puerta en verde |
| **Testcontainers** | ⚪ No integrado | No hay adaptadores de persistencia ni de mensajería que probar | Sprint 1, con el primer repositorio JPA |
| **Cucumber** | ⚪ No integrado | No hay criterios de aceptación implementados | Sprint 1, con la primera Historia de Usuario funcional |
| **OWASP Dependency-Check** | ⚪ No integrado | Trivy ya cubre las dependencias empaquetadas en la imagen; Dependency-Check aporta el análisis del árbol Maven completo | Sprint 2 |
| **Web: lint, typecheck, axe-core** | ⚪ El job existe y nunca se ejecuta | `web/` está vacío; el filtro por ruta nunca se dispara | Al andamiar `ayni-web` |
| **Validación del contrato OpenAPI** | ⚪ No integrado | `contracts/` aún no tiene especificaciones | Sprint 2 |

Leyenda: 🔴 bloquea el Pull Request · 🟡 se ejecuta pero no bloquea · ⚪ no se ejecuta.

### Nota · La cobertura se activó antes de tener dominio, y a propósito

Al redactar este ADR se constató que el umbral del 80 % **no se aplicaba en ningún punto**: el POM
declaraba `jacoco.dominio.minimo` pero el plugin solo ejecutaba `prepare-agent` y `report`, y
`report` mide sin exigir. Solo `check` rompe el build. La Definition of Done afirmaba algo que la
automatización no hacía.

Lo previsible era aplazarlo al Sprint 1 con el resto. Se comprobó en su lugar cómo se comporta
`check` sobre paquetes vacíos, y el resultado cambió la decisión:

| Situación | Resultado |
|---|---|
| Paquetes de dominio vacíos | `All coverage checks have been met` · build en verde |
| Una clase de dominio sin pruebas | `Rule violated … lines covered ratio is 0.00, but expected minimum is 0.80` · **build roto** |

La regla es **inerte hoy y exigente en cuanto exista la primera clase de dominio**, sin que nadie
tenga que acordarse de activarla. Activarla ahora no cuesta nada y elimina el riesgo de olvido, que
es la forma habitual en que estas puertas nunca se encienden.

Las dos comprobaciones importaban por igual. Verificar solo que el build pasa habría dejado abierta
la posibilidad de que el patrón `pe.ayni.bank.*.domain.*` no coincidiera con nada nunca: una puerta
decorativa que jamás salta es peor que ninguna, porque da confianza infundada.

### Nota · `sonar:sonar` no consulta la puerta de calidad

Detalle que conviene no olvidar al activarla: **`mvn sonar:sonar` sube el análisis y termina**. No
espera el veredicto. Aunque se retirase el `continue-on-error`, el paso seguiría en verde mientras
la subida funcionara, pasara o no pasara la puerta.

El check rojo que aparece en el Pull Request no lo produce ese paso, sino la **aplicación de
SonarCloud en GitHub**, como estado de commit independiente. Y como la protección de rama solo
exige `CI completa`, ese rojo nunca ha bloqueado una fusión.

Por eso el paso lleva ahora `-Dsonar.qualitygate.wait=true`: sigue sin bloquear, pero deja el
veredicto escrito en el log. Ese es el instrumento que permite saber cuándo la puerta está lista
para volverse bloqueante, en lugar de decidirlo a ciegas.

**Volverla bloqueante son dos cambios, no uno:** conservar `qualitygate.wait` y retirar
`continue-on-error`.

### Exclusiones de cobertura — permanentes, no transitorias

Aparte de lo anterior, el POM padre declara:

```xml
<sonar.coverage.exclusions>
    **/*Application.java,
    **/infrastructure/config/**,
    **/domain/port/**
</sonar.coverage.exclusions>
```

Esto **no es una relajación temporal y no debe retirarse.** Son las clases de arranque de Spring
Boot, la configuración declarativa y las interfaces de los puertos: no contienen lógica ejecutable
propia. Medir su cobertura desplaza el foco desde donde vive el negocio —el dominio— hacia
declaraciones que se «cubren» sin verificar nada.

El umbral del 80 % se aplica al dominio. Ahí no hay excepciones.

**Ampliación del 30 de agosto de 2026.** Se añaden dos patrones más, por el mismo criterio y no
por conveniencia:

```xml
**/infrastructure/out/persistence/*Entity.java,
**/infrastructure/in/web/*Dto.java
```

Las entidades JPA son campos con sus accesores y un constructor que el mapeador rellena; los DTO
son *records* con una fábrica que copia valores. Una prueba unitaria sobre ellos comprueba que un
`get` devuelve lo que puso el `set`, es decir, que Java funciona. Lo que sí hay que probar es que el
**mapeo** entre entidad y dominio sea correcto, y eso vive en los mapeadores y adaptadores, que
**no** están excluidos.

### Lo que la puerta de SonarCloud sigue reportando en rojo, y por qué

Al cerrar el Sprint 1, la cobertura de código nuevo queda en torno al 54 %, por debajo del 80 % que
exige el *quality gate*. **No se ha tocado el umbral para que pase.**

La razón es concreta y no se disimula: las 711 líneas sin cubrir están **todas** en
`infrastructure/` —adaptadores de persistencia, controladores REST, el publicador de la bandeja de
salida—. En `domain/` la cobertura es del 100 %, y eso es lo que la puerta bloqueante de JaCoCo
exige y verifica.

Cubrir esa capa no se hace con pruebas unitarias: hace falta `@DataJpaTest` contra una base real
—Testcontainers— y `@WebMvcTest` para los controladores. Es trabajo legítimo y es la primera tarea
de calidad del Sprint 2, anotada en [`pendientes-sprint-2.md`](../../gestion/pendientes-sprint-2.md).

Mientras tanto, la puerta sigue **informativa** (`continue-on-error: true`), exactamente como este
ADR decidió. Inflar la cobertura con pruebas que instancian una entidad para leer sus accesores
haría pasar la puerta sin verificar nada, que es justo el fallo que este documento existe para
evitar.

## Consecuencias

**A favor.** El pipeline es honesto: lo que está en rojo está roto de verdad, y el equipo no
aprende a ignorar fallos rutinarios. Cada relajación tiene dueño y fecha. La Definition of Done
sigue siendo el objetivo, no se reescribe a la baja.

**En contra.** Durante el Sprint 1 conviven puertas activas y puertas pendientes, y hay que
consultar esta tabla para saber qué protege realmente el pipeline. El riesgo real es el olvido: si
las activaciones no se convierten en ítems de Jira, este ADR se queda en buenas intenciones.

**Mitigación.** Cada fila con estado 🟡 o ⚪ debe tener su ítem en el Sprint Backlog del sprint
indicado. La revisión de esta tabla es punto fijo del refinamiento del Sprint 1.

## Nota sobre las versiones de las dependencias

Durante el Sprint 0, Trivy detectó 4 vulnerabilidades CRÍTICAS y 95 ALTAS en las imágenes de los
cuatro servicios Java, heredadas de las dependencias que gestiona Spring Boot 3.3.4. Se elevó el
BOM a **Spring Boot 3.5.16** y **Spring Cloud 2025.0.3**, y se fijó `netty.version` por separado
porque el BOM aún arrastraba cinco ALTAS en `netty-codec` y `netty-codec-http`.

La lección aplicable: **la versión inicial de un BOM caduca**. La revisión de vulnerabilidades no
es un evento de arranque sino una tarea recurrente, y Trivy bloqueando el Pull Request es lo que la
hace inevitable.

---

Ver también: [ADR-0002](0002-arquitectura-hexagonal-verificada.md) ·
[`DEFINITION_OF_DONE.md`](../../../DEFINITION_OF_DONE.md) ·
[`CONTRIBUTING.md`](../../../CONTRIBUTING.md)
