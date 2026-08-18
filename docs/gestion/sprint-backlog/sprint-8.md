# Sprint Backlog — Sprint 8 · Endurecimiento y Cierre

**30 de noviembre – 13 de diciembre de 2026** · 2 semanas · 2 ítems · 21 puntos

## Sprint Goal

> Que el sistema **demuestre con mediciones** —no con afirmaciones— que cumple los niveles de
> servicio comprometidos, y quede documentado y monitorizado.

**Hito asociado:** Producto Mínimo Viable · Release 1.0.0 · cierre formal del proyecto.

## Capacidad

80 h disponibles · Comprometido: 62 h. Se reserva holgura amplia para el cierre, la documentación
final y la preparación de la sustentación.

---

### `AYNI-25` · HU-14 · [NF] Tiempo de respuesta bajo carga [8 pts]

| # | Subtarea | Est. |
|---|---|---|
| 1 | Instrumentar los servicios con métricas de Micrometer y Prometheus | 4h |
| 2 | Construir los paneles de Grafana con latencia, throughput y errores | 5h |
| 3 | Escribir los escenarios de carga en k6 | 5h |
| 4 | Ejecutar la prueba de carga nominal con 200 usuarios concurrentes | 3h |
| 5 | Ejecutar la prueba de estrés hasta identificar el punto de quiebre | 4h |
| 6 | Documentar el punto de quiebre y el comportamiento en degradación | 3h |
| 7 | Optimizar los cuellos de botella detectados | 8h |
| 8 | Configurar alertas sobre los umbrales del SLA | 3h |
| 9 | Ejecutar pruebas de mutación con PIT y analizar el informe | 4h |

### `AYNI-27` · HU-16 · [NF] Disponibilidad y recuperación ante fallos [13 pts]

| # | Subtarea | Est. |
|---|---|---|
| 1 | Verificar health checks y reinicio automático en los cinco servicios | 3h |
| 2 | Configurar réplicas de los servicios sin estado | 4h |
| 3 | Automatizar el respaldo diario de la base de datos | 4h |
| 4 | **Ejecutar y cronometrar** el procedimiento de restauración | 5h |
| 5 | Documentar el procedimiento de recuperación ante desastres | 3h |
| 6 | Prueba de caída de `notification-service` durante una transferencia | 3h |
| 7 | Prueba de caída de `kyc-service` durante un onboarding | 3h |
| 8 | Medir la disponibilidad real del mes y contrastarla con el 99.5% | 3h |
| 9 | Ejecutar OWASP ZAP y resolver los hallazgos de riesgo alto | 5h |
| 10 | Redactar la evidencia de cumplimiento de ISO/IEC 25010 | 5h |
| 11 | Actualizar el CHANGELOG y crear el tag `v1.0.0` | 2h |
| 12 | Preparar la demostración de la sustentación final | 4h |

---

## Definition of Done

Aplica la de [`DEFINITION_OF_DONE.md`](../../../DEFINITION_OF_DONE.md).

**Adicional para el cierre del proyecto:**

- El **RTO de 4 horas** debe estar **medido con cronómetro**, no estimado.
- Cada una de las nueve características de ISO/IEC 25010 debe tener su evidencia con cifra.
- El tag `v1.0.0` debe existir en `main` y permitir revertir a él.
