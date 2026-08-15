# Hoja de ruta — Ayni Bank

Del **14 de agosto** al **13 de diciembre de 2026**. Diecisiete semanas: un Sprint 0 de una semana
y ocho sprints de dos semanas.

📊 Cronograma visual: [`docs/gestion/gantt.mmd`](docs/gestion/gantt.mmd) ·
Tablero: [Jira · proyecto AYNI](https://jloadenegri.atlassian.net/jira/software/projects/AYNI)

---

## Sprints

### Iniciación · 14 – 17 ago
Formalizar el proyecto: Acta de Constitución, cronograma, Product Backlog priorizado, matrices de
riesgos e interesados, listado de adquisiciones y línea base del alcance.

**Hito:** Acta de Constitución aprobada.

---

### Sprint 0 · Fundación · 17 – 23 ago
`EP-00` `EP-01`

Repositorio con GitFlow y ramas protegidas · pipelines de CI/CD · Docker Compose de desarrollo ·
esqueleto hexagonal de los cinco servicios con reglas de ArchUnit · sistema de diseño e identidad
visual · documentos guía.

**Meta:** que cualquier integrante levante todo el sistema con un solo comando y que ningún cambio
llegue a `develop` sin pasar las verificaciones.

**Hito:** línea base de planificación establecida y fundación técnica operativa.

---

### Sprint 1 · Esqueleto Ambulante · 24 ago – 6 sep
`EP-01` `EP-02` `EP-03`

Registro de usuario · autenticación con MFA · apertura automática de cuenta · despliegue continuo
con aprobación.

**Meta:** un flujo fino que **atraviesa los cinco servicios** — navegador → gateway →
identity-service → kyc-service (que de momento aprueba siempre) → evento → core-banking crea la
cuenta → notification envía el correo.

> Funcionalmente es pobre; arquitectónicamente lo valida todo. El riesgo principal de una
> arquitectura distribuida es descubrir tarde que los servicios no se entienden. Aquí ese riesgo se
> materializa —o se descarta— en la cuarta semana, cuando aún queda el 75% del tiempo.

**Hito:** integración entre servicios validada.

---

### Sprint 2 · Onboarding KYC · 7 – 20 sep
`EP-02`

Captura del DNI por ambas caras con detección automática del documento · extracción de datos por
OCR · selfie con prueba de vivacidad · cotejo biométrico facial · almacenamiento en MinIO ·
consentimiento conforme a la Ley 29733.

**Meta:** sustituir el KYC simulado del Sprint 1 por el real, sin tocar la integración.

**Hito:** cierre de la fase de identidad digital.

---

### Sprint 3 · Núcleo Transaccional · 21 sep – 4 oct
`EP-03`

Libro mayor de doble partida · devengo diario de interés con año comercial de 360 días ·
capitalización mensual · gestión de tasas con vigencia histórica · publicación de TREA · consulta
de saldo y movimientos.

**Hito:** **despliegue a producción versión 1.**

---

### Sprint 4 · Transferencias · 5 – 18 oct
`EP-05` `EP-07`

Transferencias entre cuentas Ayni · patrón Transactional Outbox · notification-service consumiendo
eventos · correo transaccional · comprobantes · idempotencia.

**Meta:** que el dinero se mueva de forma atómica y que el cliente se entere, sin que el sistema de
notificaciones pueda comprometer una transacción.

---

### Sprint 5 · Tarjeta de Débito · 19 oct – 1 nov
`EP-04`

Emisión de tarjeta virtual con PAN válido por Luhn · cifrado del PAN · CVV dinámico · congelar y
descongelar · límite diario · control de compras por internet.

**Hito:** **despliegue a producción versión 2.**

---

### Sprint 6 · Interoperabilidad · 2 – 15 nov
`EP-05` `EP-08`

Transferencias interbancarias por CCI contra cámara de compensación simulada con contrato OpenAPI ·
cuenta en dólares · conversión multimoneda · **integración real con SUNAT/BCRP** para tipo de
cambio.

**Meta:** demostrar interoperabilidad con un sistema externo genuinamente real, no solo con
simuladores.

---

### Sprint 7 · Experiencia y Operación · 16 – 29 nov
`EP-06` `EP-09`

Estado de cuenta en PDF · back-office con segregación de funciones y principio de cuatro ojos ·
pista de auditoría e Hibernate Envers · Prometheus, Grafana y Loki · accesibilidad WCAG 2.1 AA ·
optimización de rendimiento web.

**Hito:** evidencias de calidad ISO/IEC 25010 completas.

---

### Sprint 8 · Endurecimiento y Cierre · 30 nov – 13 dic
`EP-10`

Pruebas de carga y estrés con k6 hasta el punto de quiebre · pruebas de concurrencia sobre el mismo
saldo · alta disponibilidad con réplicas · prueba cronometrada de respaldo y restauración · pruebas
de mutación con PIT · OWASP ZAP · documentación final.

**Hito:** **Producto Mínimo Viable — Release 1.0.0** y cierre formal del proyecto.

---

## Hitos

| Fecha | Hito |
|---|---|
| 17 ago 2026 | Acta de Constitución aprobada |
| 23 ago 2026 | Línea base de planificación y fundación técnica |
| 6 sep 2026 | Integración entre servicios validada |
| 20 sep 2026 | Cierre de la fase de identidad digital |
| 4 oct 2026 | **Despliegue a producción v1** |
| 18 oct 2026 | Transferencias y notificaciones operativas |
| 1 nov 2026 | **Despliegue a producción v2** |
| 15 nov 2026 | Interoperabilidad y multimoneda completas |
| 29 nov 2026 | Evidencias de calidad ISO/IEC 25010 |
| 13 dic 2026 | **MVP · Release 1.0.0** |

---

## Fuera del alcance de esta versión

Aplicativo móvil nativo · billetera digital y pagos por QR · productos de crédito · depósitos a
plazo y fondos mutuos · agencias y cajeros · tarjetas físicas · integración productiva con RENIEC y
con la Cámara de Compensación Electrónica · autorización ante la SBS.

Estas exclusiones están declaradas en el Acta de Constitución y **no se incorporan sin aprobación
explícita del patrocinador**. Toda incorporación de alcance exige retirar un ítem equivalente.
