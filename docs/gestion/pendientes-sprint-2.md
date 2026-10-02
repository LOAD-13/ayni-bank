# Pendientes al cerrar el Sprint 1

> Todo lo que el Sprint 1 dejó a medias, con el motivo y dónde está el hueco. Lo que ya tiene
> historia en Jira aparece con su clave; lo que **no la tiene** está marcado, y ese es el valor de
> este documento: son los huecos que no descubriría nadie leyendo el backlog.
>
> Última revisión: 30 de agosto de 2026.

---

## 1. Huecos que NO están en Jira

Estos aparecieron construyendo el Sprint 1 y no los cubre ninguna historia. Hay que decidir si
entran al backlog o se retiran del alcance.

### 1.1 Alta de WebAuthn (biometría del dispositivo)

El prototipo aprobado incluye el botón **«Entrar con la biometría del dispositivo»** en la pantalla
de ingreso, y la landing promete «Ingreso biométrico». **No existe ninguna historia que lo
construya.**

Conviene no confundir las tres cosas que el proyecto llama «biometría»:

| | Qué es | Dónde | Estado |
|---|---|---|---|
| WebAuthn / passkey | huella o rostro del **dispositivo**; nada sale de él | login | sin historia |
| Prueba de vida | selfie que **sí** viaja y se guarda en MinIO | onboarding paso 4 | HU-03 |
| Consentimiento biométrico | la autorización legal para tratar la selfie | registro paso 1 | HU-01 ✅ |

Hoy el botón enlaza a `/pendiente`. **Decisión pendiente del PO:** crear la historia o retirar el
botón del prototipo. Mi recomendación es crearla: está prometido en la landing que vio el docente.

### 1.2 Segundo factor alternativo por correo

Decidido en esta sesión: TOTP es el predeterminado y el correo será la alternativa. El enlace «Usar
otro método de verificación» del prototipo es su puerta de entrada. **Depende de HU-13**
(`notification-service`), así que no antes del Sprint 3. Ver [ADR-0010](../arquitectura/adr/0010-segundo-factor-totp-y-sesion-rotativa.md).

### 1.3 Pantalla de «Seguridad» en el panel

Donde se reconfigura el segundo factor —cambio de móvil, alta de passkey, revocar sesiones—.
**No existe en pen.dev**: hay que diseñarla antes de poder construirla.

### 1.4 Caducidad efectiva de las solicitudes de onboarding

`solicitud_onboarding.expira_en` se rellena con siete días y **nadie lo mira**. La caducidad está
declarada y no se aplica. En cuanto HU-02 empiece a guardar imágenes del DNI, eso deja de ser un
detalle: sería retención indefinida de datos personales de gente que abandonó el alta, contra el
principio de minimización de la Ley N.º 29733.

### 1.5 Bandeja de salida en `identity`

`core-banking` publica `CuentaAperturada` por outbox, como manda [ADR-0003](../arquitectura/adr/0003-patron-transactional-outbox.md).
`identity` publica `SolicitudAprobada` **directo a RabbitMQ**, sin outbox. Es deuda consciente: si
el envío falla, la solicitud queda aprobada y la cuenta no se abre. Se resuelve reprocesando, y se
cierra con HU-13, que trae el outbox completo a identidad.

### 1.6 Verificación del CCI contra la especificación oficial

La estructura de veinte dígitos es correcta. El cálculo de los dos dígitos de control es una
comprobación ponderada estándar, **no necesariamente el algoritmo que publica la SBS** —esa
especificación no es de acceso abierto—. Sirve para que el sistema sea coherente consigo mismo, pero
**antes de conectar con la cámara de compensación hay que contrastarlo**. Si difiere, cambia un solo
fichero: `Cci.java`.

### 1.7 Código de entidad provisional

`Cci.CODIGO_DE_BANCO` vale `999`. Lo asigna la SBS al autorizar la entidad; hasta entonces cualquier
valor es un marcador.

### 1.8 Pruebas de integración de la capa de infraestructura

**Es la primera tarea de calidad del Sprint 2.** Al cerrar el Sprint 1 hay 711 líneas nuevas sin
cubrir, y están todas en `infrastructure/`: adaptadores de persistencia, controladores REST y el
publicador de la bandeja de salida. En `domain/` la cobertura es del 100 %.

No se cubre con pruebas unitarias. Hace falta:

- `@DataJpaTest` con **Testcontainers** contra PostgreSQL real, para los adaptadores. Con una base
  embebida no valdría: el índice único parcial de `cuenta`, el tipo `jsonb` del outbox y las
  secuencias son específicos de PostgreSQL, y probarlos contra H2 sería probar otra cosa.
- `@WebMvcTest` para los controladores y el manejador de errores, que no necesitan base.

Hasta entonces la puerta de SonarCloud reporta por debajo del 80 % en código nuevo. Es informativa
—`continue-on-error: true`, ver ADR-0006— y **no se ha tocado el umbral para que pase**.

### 1.9 Detección de documento duplicado

Dos personas no pueden tener el mismo DNI, pero el número declarado se guarda cifrado con IV
aleatorio, así que **la columna no sirve para imponer unicidad**. La comprobación tiene que hacerse
en HU-02, sobre la identidad ya verificada. Ver [ADR-0009](../arquitectura/adr/0009-identidad-declarada-antes-del-ocr.md).

---

## 2. Provisional que hay que retirar

Cosas que funcionan hoy y **no pueden llegar a producción**.

| Qué | Dónde | Lo retira |
|---|---|---|
| `AprobacionController` bajo perfil `dev` | `identity` | HU-02: lo sustituye el resultado del OCR |
| Ruta `/api/v1/dev/**` en el gateway | `ayni-gateway` | HU-02 |
| Titular tomado de la ruta y no del token | `/cuentas/titular/{id}`, `/usuarios/{id}/resumen` | HU-07: validación del JWT en el gateway |
| Notificadores que solo escriben en el log | `identity` | HU-13 |
| Claves de desarrollo en `application.yml` | `AYNI_CIFRADO_CLAVE`, `AYNI_JWT_CLAVE` | al provisionar staging (Sprint 2) |

**`AYNI_COOKIES_SEGURAS` va en `true` en cuanto haya HTTPS.** En local está en `false` porque una
cookie `Secure` sobre HTTP el navegador la descarta sin avisar.

---

## 3. Alcance del Sprint 2 ya mapeado

| Clave | Qué | Nota |
|---|---|---|
| `AYNI-99` | OCR de anverso y reverso con PaddleOCR | contrasta con lo declarado |
| `AYNI-121` | Carga del DNI desde archivo | pedido por el docente. **Pen no tiene esa pantalla** |
| `AYNI-31` | Despliegue continuo a staging | movido desde el Sprint 1 |
| HU-02, HU-03 | Verificación de identidad y prueba de vida | pasos 2, 3 y 4 del asistente |

**Regla de HU-02 acordada en esta sesión:** si la fecha de nacimiento que lee el OCR corresponde a un
menor de edad, **rechazo y borrado de las imágenes**. No revisión manual: no hay nada que revisar.
La comprobación del paso 1 no desaparece —evita recoger la biometría de un menor—, pero no es la que
manda.

**La selfie no sirve para estimar la edad.** Los estimadores por rostro tienen un error de varios
años; denegar un servicio financiero con eso no se sostiene. La selfie es prueba de vida y cotejo
contra la foto del DNI.

**Pantalla 5b, «Derivada a revisión»:** diseñada en Pen, sin construir. Es el camino de HU-02 cuando
las lecturas no concuerdan.

---

## 4. Discrepancias entre el prototipo y las historias

Detectadas al construir. **Manda Jira**, pero el prototipo hay que corregirlo o quedará como
documentación que miente.

| Prototipo | Historia | Resuelto como |
|---|---|---|
| «Detectamos **3** intentos seguidos» | HU-04 dice **5** | implementados 5 |
| «Duración del bloqueo por confirmar» | no fijada | 5 min, duplicando, techo de 1 h |
| «Reenviar código · disponible en 00:23» | TOTP | **omitido**: no hay nada que reenviar |
| «Plazo por confirmar» en 5b | no fijado | pendiente de decidir en HU-02 |

---

## 5. Trampas del entorno que cuestan una tarde

Encontradas hoy. Están aquí para que no vuelvan a costar lo mismo.

- **`mvn test` en local puede pasar mientras el build limpio falla.** La compilación incremental
  reutiliza clases viejas: si se añade un método a un puerto y no se actualizan los dobles de prueba,
  el error solo aparece en Docker, que compila desde cero. **Para dar algo por bueno:
  `./mvnw clean verify`.**
- **`docker compose up -d --build` reconstruye la imagen pero no siempre reemplaza el contenedor.**
  Hace falta `--force-recreate`, o se depura contra código viejo.
- **Surefire solo ejecuta clases con `Test` en el nombre.** La suite de Cucumber existía y no corría;
  el build pasaba en verde sin comprobar un solo escenario. Corregido con `includes` en el POM padre.
- **ArchUnit atrapa los tipos anidados dentro de un puerto.** Un `record` o una excepción declarados
  dentro de una interfaz de `domain.port` incumplen la nomenclatura. Van en `domain.model`.
- **El prefijo `__Host-` en una cookie exige `Secure`.** Sobre HTTP el navegador la descarta en
  silencio: la respuesta es 200 y la renovación falla después sin motivo aparente.
- **Los assets exportados de Pen tienen un color fijo.** El texto «AYNI Bank» se exportó en blanco
  porque en el prototipo siempre va sobre azul; sobre fondo claro era invisible y ninguna prueba lo
  detectaba. Resuelto con una máscara CSS en `LogotipoAyni`.
