# Seguridad — Ayni Bank

Modelo de amenazas, controles aplicados y procedimiento de reporte.

> **Aviso:** Ayni Bank es un proyecto académico. No opera con dinero real ni está supervisado por la
> Superintendencia de Banca, Seguros y AFP. Los controles descritos se implementan con rigor
> profesional porque el objetivo del curso es demostrarlos, no simularlos.

---

## 1. Marcos de referencia

| Marco | Alcance de aplicación |
|---|---|
| **OWASP ASVS v4 nivel 2** | Línea base verificable de seguridad de aplicación |
| **OWASP Top 10 (2021)** | Prevención de las diez categorías de riesgo más frecuentes |
| **ISO/IEC 27001 · Anexo A** | Controles organizativos: gestión de accesos, criptografía, registro |
| **Principios de PCI-DSS** | Tratamiento del PAN y del CVV (sin buscar certificación) |
| **Ley N.º 29733** | Protección de Datos Personales del Perú — datos biométricos como sensibles |

---

## 2. Modelo de amenazas (STRIDE)

| Amenaza | Vector concreto | Control |
|---|---|---|
| **Suplantación** (*Spoofing*) | Alguien abre una cuenta con el DNI de otra persona | Prueba de vivacidad, cotejo facial con umbral ≥ 80%, derivación a revisión manual, límites operativos los primeros 30 días |
| **Manipulación** (*Tampering*) | Alteración de saldos o de asientos contables | Libro mayor de doble partida, saldo derivado y no mutable, auditoría append-only |
| **Repudio** | «Yo no hice esa transferencia» | Pista de auditoría inalterable con actor, IP, user-agent y timestamp; MFA en operaciones sensibles |
| **Divulgación** (*Information disclosure*) | Fuga del PAN o de datos de identidad | Cifrado AES-256-GCM en reposo, enmascarado en interfaz y logs, URLs pre-firmadas de 5 minutos |
| **Denegación de servicio** | Saturación de endpoints de autenticación u onboarding | Rate limiting en el gateway, bloqueo progresivo, circuit breakers |
| **Elevación de privilegios** | Un operador aprueba su propia operación | Segregación de funciones y principio de cuatro ojos; autorización verificada en cada endpoint |

---

## 3. Autenticación y gestión de sesión

| Control | Implementación |
|---|---|
| Almacenamiento de contraseñas | **Argon2id** — resistente a GPU y ASIC |
| Política de contraseñas | ≥ 12 caracteres, mayúscula, minúscula, número y símbolo |
| Token de acceso | JWT de **15 minutos** |
| Refresh token | **Rotativo**, 7 días, cookie `HttpOnly; Secure; SameSite=Strict` |
| Detección de robo de token | Reutilizar un refresh ya consumido **invalida toda la familia** de tokens |
| Segundo factor | **TOTP** obligatorio para transferir, revelar PAN y cambiar contraseña |
| Bloqueo | Progresivo tras 5 intentos fallidos, con notificación al titular |
| Enumeración de usuarios | Mensajes de error genéricos que no revelan si una cuenta existe |

---

## 4. Autorización — segregación de funciones

En banca **no existe un superadministrador**. Nadie puede completar por sí solo una operación
sensible: quien la inicia no puede aprobarla (**principio de cuatro ojos**).

| Rol | Puede | No puede |
|---|---|---|
| `CLIENTE` | Operar sus propias cuentas | Ver cuentas ajenas |
| `OPERADOR` | Consultar clientes, iniciar bloqueos | Aprobar por sí solo · ver el PAN completo |
| `SUPERVISOR` | Aprobar lo iniciado por un operador | Iniciar y aprobar la misma operación |
| `AUDITOR` | Leer todo, incluida la pista de auditoría | Escribir absolutamente nada |
| `OFICIAL_CUMPLIMIENTO` | Revisar KYC, marcar operaciones sospechosas | Mover dinero |

La autorización se verifica **en el servicio**, no solo en la interfaz. Ocultar un botón no es un
control de seguridad.

---

## 5. Criptografía

| Dato | Protección |
|---|---|
| Contraseña | Argon2id (irreversible) |
| PAN de tarjeta | AES-256-GCM en reposo · mostrado solo los últimos 4 dígitos |
| CVV | **No se almacena.** Se calcula y se muestra dinámicamente |
| Número de documento | AES-256-GCM en reposo |
| Semilla TOTP | AES-256-GCM en reposo |
| Documentos KYC y selfies | Cifrado del lado del servidor en MinIO · hash SHA-256 para detectar alteración |
| Tránsito | TLS en todas las comunicaciones, incluidas las internas entre servicios |

Las claves **nunca** están en el código ni en el repositorio: se inyectan por variable de entorno y
se gestionan con GitHub Secrets en los pipelines.

---

## 6. Trazabilidad y auditoría

- **`evento_auditoria` es append-only.** `UPDATE` y `DELETE` están revocados a nivel de permisos de
  PostgreSQL, incluso para el usuario de la aplicación. Ni la propia aplicación puede borrar su
  rastro.
- **Hibernate Envers** versiona automáticamente las entidades sensibles (`cuenta`, `tarjeta`,
  `persona`), permitiendo reconstruir el estado en cualquier momento pasado.
- Cada registro guarda: actor, acción, recurso, IP, user-agent, resultado y timestamp.
- **Idempotencia obligatoria** en operaciones monetarias mediante cabecera `Idempotency-Key`.

---

## 7. Protección de datos personales (Ley N.º 29733)

La ley peruana clasifica los **datos biométricos como datos sensibles**, con exigencias reforzadas.

| Obligación | Cómo se cumple |
|---|---|
| Consentimiento explícito e informado | Pantalla previa a toda captura biométrica, explicando finalidad y tratamiento |
| Finalidad limitada | Los datos biométricos se usan **solo** para verificar identidad en el onboarding |
| Minimización | Se guarda el resultado del cotejo y las imágenes; no se construyen perfiles biométricos |
| Seguridad | Cifrado en reposo, acceso restringido al rol `OFICIAL_CUMPLIMIENTO` |
| Retención | Política documentada con plazo definido y borrado automatizado al vencer |
| Derechos del titular | Procedimiento de acceso, rectificación y supresión |
| Registro del consentimiento | Guardado en la pista de auditoría con fecha y hora |

---

## 8. Seguridad en el ciclo de desarrollo

| Verificación | Herramienta | Cuándo | Bloquea |
|---|---|---|---|
| Secretos en código e historial | **Gitleaks** | Cada push y PR | ✅ |
| Vulnerabilidades en dependencias | **OWASP Dependency-Check** | Cada PR | ✅ críticas y altas |
| Vulnerabilidades en imágenes | **Trivy** | Cada construcción | ✅ críticas y altas |
| Análisis estático de seguridad | **SonarCloud** | Cada PR | ✅ quality gate |
| Escaneo dinámico | **OWASP ZAP** | Tras desplegar a staging | Informe revisado |

Reglas de repositorio: `main` y `develop` protegidas, sin push directo, Pull Request con aprobación
obligatoria y todos los checks en verde.

---

## 9. Defensas ante ataques comunes

| Ataque | Defensa |
|---|---|
| Inyección SQL | Consultas parametrizadas vía JPA · sin concatenación de SQL |
| XSS | Escapado por defecto de React · `Content-Security-Policy` estricta |
| CSRF | Cookies `SameSite=Strict` · tokens anti-CSRF en formularios |
| Clickjacking | `X-Frame-Options: DENY` |
| Fuerza bruta | Rate limiting · bloqueo progresivo · MFA |
| Robo de sesión | Tokens de vida corta · refresh rotativo con detección de reutilización |
| IDOR | Autorización verificada por recurso en cada petición, nunca por identificador recibido |
| Transferencia duplicada | `Idempotency-Key` obligatoria |
| Condición de carrera sobre el saldo | Bloqueo pesimista dentro de una única transacción ACID |

---

## 10. Reporte de vulnerabilidades

Si encuentras una vulnerabilidad, **no abras un issue público**. Escribe a
`jloadenegri@gmail.com` con el asunto `[SECURITY] Ayni Bank`, describiendo el problema, los
pasos para reproducirlo y el impacto estimado.

Nos comprometemos a acusar recibo en 48 horas y a informar de la resolución.

---

## 11. Qué no cubre este proyecto

Por su naturaleza académica, quedan explícitamente fuera:

- Certificación PCI-DSS (se aplican sus principios, no se busca la certificación).
- Autorización de funcionamiento ante la SBS.
- Integración productiva real con RENIEC y con la Cámara de Compensación Electrónica: ambas son
  **servicios simulados con contrato de interfaz documentado**.
- Auditoría de seguridad por un tercero independiente.
