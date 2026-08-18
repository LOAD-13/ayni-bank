# Sprint Backlog — Sprint 2 · Onboarding KYC

**7 – 20 de septiembre de 2026** · 2 semanas · 2 ítems · 26 puntos

## Sprint Goal

> Que el KYC simulado del Sprint 1 se sustituya por verificación real de identidad: DNI con OCR y
> cotejo biométrico facial, sin tocar la integración entre servicios.

## Capacidad

80 h disponibles · Comprometido: 66 h

---

### `AYNI-13` · HU-02 · Verificación de identidad mediante DNI [13 pts]

| # | Subtarea | Est. |
|---|---|---|
| 1 | Definir el contrato OpenAPI de `kyc-service` en `contracts/` | 3h |
| 2 | Generar el cliente Java desde el contrato en tiempo de build | 2h |
| 3 | Implementar la detección de que la imagen es un DNI peruano | 6h |
| 4 | Implementar la validación de calidad: nitidez, reflejos y encuadre | 4h |
| 5 | Implementar el OCR de anverso y reverso con PaddleOCR | 6h |
| 6 | Validar el dígito verificador del número de DNI | 1h |
| 7 | Implementar la subida a MinIO con URL pre-firmada desde el navegador | 4h |
| 8 | Calcular y almacenar el hash SHA-256 de cada documento | 2h |
| 9 | Migración Flyway de `solicitud_onboarding` y `documento_kyc` | 2h |
| 10 | Envolver la llamada a `kyc-service` con Resilience4j | 3h |
| 11 | Implementar el límite de tres intentos y la derivación a revisión manual | 3h |
| 12 | Pantalla de captura de DNI con guía visual de encuadre | 6h |
| 13 | Feature de Cucumber con los cinco escenarios | 4h |

### `AYNI-14` · HU-03 · Verificación biométrica facial [13 pts]

| # | Subtarea | Est. |
|---|---|---|
| 1 | Implementar la detección de rostro único en la selfie | 4h |
| 2 | Implementar la prueba de vivacidad contra fotografías y pantallas | 8h |
| 3 | Implementar el cotejo facial contra la foto del DNI | 6h |
| 4 | Hacer configurable el umbral de similitud sin desplegar | 2h |
| 5 | Registrar score y decisión en la pista de auditoría | 2h |
| 6 | Implementar la pantalla de consentimiento informado (Ley 29733) | 3h |
| 7 | Bloquear la captura si no hay consentimiento otorgado | 2h |
| 8 | Derivar a revisión manual del Oficial de Cumplimiento bajo el umbral | 3h |
| 9 | Preparar el conjunto de imágenes de control para medir precisión | 4h |
| 10 | Pantalla de captura de selfie con detección en vivo | 6h |
| 11 | Feature de Cucumber con los cuatro escenarios | 4h |

---

## Riesgos del sprint

| Riesgo | Mitigación |
|---|---|
| La precisión del OCR y del cotejo no alcanza el umbral (R-01, R-02) | Conjunto de imágenes de control desde el primer día; umbrales configurables; flujo manual operativo |
| La prueba de vivacidad es el trabajo más incierto del proyecto | Se aborda primero; si consume el sprint, se degrada a detección básica y se refuerza en el Sprint 8 |

## Definition of Done

Aplica la de [`DEFINITION_OF_DONE.md`](../../../DEFINITION_OF_DONE.md).

**Adicional:** medición documentada de la tasa de acierto sobre el conjunto de control. Sin cifra
medida, la historia no está terminada.
