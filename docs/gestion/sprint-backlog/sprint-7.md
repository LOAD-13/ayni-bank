# Sprint Backlog — Sprint 7 · Operación, Auditoría y Accesibilidad

**16 – 29 de noviembre de 2026** · 2 semanas · 2 ítems · 16 puntos

## Sprint Goal

> Que el banco pueda operarse y auditarse con segregación de funciones, y que cualquier persona
> pueda usarlo con lector de pantalla y solo con teclado.

**Hito asociado:** evidencias de calidad ISO/IEC 25010 completas.

## Capacidad

120 h disponibles · Comprometido: 99 h

---

### `AYNI-26` · HU-15 · [NF] Trazabilidad y pista de auditoría inalterable [8 pts]

| # | Subtarea | Est. |
|---|---|---|
| 1 | Migración Flyway de `evento_auditoria` | 2h |
| 2 | **Revocar `UPDATE` y `DELETE`** sobre la tabla a nivel de PostgreSQL | 3h |
| 3 | Implementar el interceptor que registra actor, acción, recurso, IP y agente | 5h |
| 4 | Configurar Hibernate Envers en `cuenta`, `tarjeta` y `persona` | 5h |
| 5 | Implementar la consulta de estado histórico en una fecha dada | 4h |
| 6 | Implementar los cinco roles con sus permisos diferenciados | 5h |
| 7 | Implementar el principio de cuatro ojos en operaciones sensibles | 5h |
| 8 | Auditar los logs para verificar que no exponen datos sensibles | 3h |
| 9 | Back-office con vista de auditoría de solo lectura | 6h |
| 10 | Feature de Cucumber con los cuatro escenarios | 3h |

### `AYNI-28` · HU-17 · [NF] Accesibilidad y facilidad de aprendizaje [8 pts]

| # | Subtarea | Est. |
|---|---|---|
| 1 | Integrar axe-core en el pipeline como verificación bloqueante | 3h |
| 2 | Corregir las violaciones de WCAG 2.1 AA detectadas | 6h |
| 3 | Verificar contraste de todos los pares de color contra los tokens | 3h |
| 4 | Asegurar navegación completa por teclado en los flujos críticos | 5h |
| 5 | Implementar indicador de foco visible y coherente | 2h |
| 6 | Añadir texto alternativo y etiquetas asociadas en formularios | 3h |
| 7 | Reescribir los mensajes de error para que indiquen cómo corregir | 3h |
| 8 | Optimización de rendimiento web hasta Lighthouse ≥ 90 | 5h |
| 9 | Prueba con usuarios reales usando lector de pantalla | 4h |
| 10 | Feature de Cucumber con los tres escenarios | 3h |

---

## Definition of Done

Aplica la de [`DEFINITION_OF_DONE.md`](../../../DEFINITION_OF_DONE.md).

**Adicional:** debe demostrarse en vivo que **el intento de alterar la pista de auditoría es
rechazado por la base de datos**, y que una transferencia se completa **usando solo el teclado**.
