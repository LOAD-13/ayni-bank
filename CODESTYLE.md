# Convenciones de código — Ayni Bank

Reglas de escritura de código del proyecto. Lo que aquí se marca como **verificado** lo comprueba
una herramienta y rompe el build; el resto lo hace cumplir la revisión de código.

---

## 1. Arquitectura hexagonal — obligatoria en todos los servicios Java

```
pe.ayni.bank.<contexto>
├── domain/                    ← CERO imports de framework
│   ├── model/                 entidades y objetos de valor del negocio
│   ├── service/               reglas de negocio puras
│   └── port/
│       ├── in/                casos de uso que el mundo exterior invoca
│       └── out/               lo que el dominio necesita del exterior
├── application/               orquestación, @Transactional, implementación de puertos de entrada
└── infrastructure/            adaptadores enchufables
    ├── in/web/                controladores REST, DTOs, mappers
    ├── in/messaging/          consumidores de eventos
    ├── out/persistence/       entidades JPA, repositorios, mappers
    ├── out/messaging/         publicadores de eventos
    ├── out/client/            clientes de servicios externos
    └── config/                beans, seguridad, resiliencia
```

### La regla que lo sostiene todo — **verificada por ArchUnit**

El paquete `domain` **no puede importar**:

- `org.springframework..`
- `jakarta.persistence..` ni `javax.persistence..`
- `com.fasterxml.jackson..`
- nada de `..infrastructure..` ni de `..application..`

```java
@AnalyzeClasses(packages = "pe.ayni.bank")
class ArquitecturaHexagonalTest {

    @ArchTest
    static final ArchRule el_dominio_no_conoce_el_framework =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta.persistence..",
                                "com.fasterxml.jackson..", "..infrastructure..");

    @ArchTest
    static final ArchRule las_capas_se_respetan =
        layeredArchitecture().consideringAllDependencies()
            .layer("Dominio").definedBy("..domain..")
            .layer("Aplicacion").definedBy("..application..")
            .layer("Infraestructura").definedBy("..infrastructure..")
            .whereLayer("Dominio").mayOnlyBeAccessedByLayers("Aplicacion", "Infraestructura")
            .whereLayer("Infraestructura").mayNotBeAccessedByAnyLayer();
}
```

**Por qué importa:** el dominio se prueba con JUnit puro sin levantar Spring, en milisegundos. Y
cambiar PostgreSQL por otra cosa, o RabbitMQ por Kafka, toca un solo paquete. Eso es lo que
convierte «usamos hexagonal» en algo demostrable.

---

## 2. Nomenclatura

### Java

| Elemento | Convención | Ejemplo |
|---|---|---|
| Paquete | minúsculas, sin guiones | `pe.ayni.bank.core.domain.model` |
| Clase | `PascalCase` | `CuentaAhorro`, `TransferenciaService` |
| Interfaz de puerto de entrada | `PascalCase` + `UseCase` | `TransferirDineroUseCase` |
| Interfaz de puerto de salida | `PascalCase` + `Port` | `CuentaRepositoryPort` |
| Adaptador | `PascalCase` + `Adapter` | `CuentaJpaAdapter` |
| Entidad JPA | `PascalCase` + `Entity` | `CuentaEntity` |
| DTO de entrada | `PascalCase` + `Request` | `TransferenciaRequest` |
| DTO de salida | `PascalCase` + `Response` | `MovimientoResponse` |
| Evento | `PascalCase` en pasado | `TransferenciaCompletada` |
| Excepción | `PascalCase` + `Exception` | `SaldoInsuficienteException` |
| Método y variable | `camelCase` | `calcularDevengoDiario`, `saldoDisponible` |
| Constante | `UPPER_SNAKE_CASE` | `DIAS_ANIO_COMERCIAL` |
| Prueba | clase + `Test` | `TransferenciaServiceTest` |

**El dominio se nombra en español.** `CuentaAhorro`, `LibroMayor`, `AsientoContable`,
`saldoDisponible`. La infraestructura usa los términos técnicos que le correspondan. Razón: el
lenguaje ubicuo del negocio bancario peruano es el español, y traducirlo introduce ambigüedad —
«balance» no es lo mismo que «saldo» ni que «balance contable».

### Python (`ayni-kyc-service`)

| Elemento | Convención | Ejemplo |
|---|---|---|
| Módulo y paquete | `snake_case` | `deteccion_documento.py` |
| Clase | `PascalCase` | `DetectorDocumento` |
| Función y variable | `snake_case` | `extraer_datos_dni` |
| Constante | `UPPER_SNAKE_CASE` | `UMBRAL_SIMILITUD_FACIAL` |

Formateo con **Ruff**, tipado con **mypy** en modo estricto.

### TypeScript (`ayni-web`)

| Elemento | Convención | Ejemplo |
|---|---|---|
| Componente | `PascalCase` | `TarjetaVirtual.tsx` |
| Hook | `use` + `PascalCase` | `useSaldoCuenta.ts` |
| Función y variable | `camelCase` | `formatearImporte` |
| Tipo e interfaz | `PascalCase` | `MovimientoCuenta` |
| Archivo de utilidades | `camelCase` | `formatoMoneda.ts` |

### Base de datos

| Elemento | Convención | Ejemplo |
|---|---|---|
| Schema | `snake_case` | `identity`, `core`, `notification` |
| Tabla | `snake_case` **singular** | `cuenta`, `asiento_contable` |
| Columna | `snake_case` | `saldo_disponible`, `creado_en` |
| Clave primaria | `id` | `id` |
| Clave foránea | `<tabla>_id` | `cuenta_id` |
| Índice | `idx_<tabla>_<columnas>` | `idx_asiento_cuenta_fecha` |
| Restricción única | `uq_<tabla>_<columnas>` | `uq_cuenta_numero` |
| Restricción de verificación | `ck_<tabla>_<regla>` | `ck_cuenta_saldo_no_negativo` |
| Migración Flyway | `V<n>__<descripcion>.sql` | `V7__crear_tabla_outbox_event.sql` |

**Tabla en singular**: `cuenta`, no `cuentas`. Una fila es una cuenta.

---

## 3. Manejo del dinero — reglas absolutas

```java
// ✅ CORRECTO
public record Money(BigDecimal importe, Currency moneda) {
    public Money {
        Objects.requireNonNull(importe);
        Objects.requireNonNull(moneda);
        importe = importe.setScale(4, RoundingMode.HALF_EVEN);
    }

    public Money mas(Money otro) {
        if (!this.moneda.equals(otro.moneda)) {
            throw new MonedasIncompatiblesException(this.moneda, otro.moneda);
        }
        return new Money(this.importe.add(otro.importe), this.moneda);
    }
}

// ❌ PROHIBIDO
double saldo = 1000.0;
float interes = saldo * 0.05f;
```

| Regla | Detalle |
|---|---|
| Tipo | `BigDecimal` siempre. `double` y `float` están **prohibidos** para dinero. |
| Redondeo | `RoundingMode.HALF_EVEN` (redondeo bancario) |
| Escala | 4 decimales para saldos, 8 para devengos de interés |
| Base de datos | `NUMERIC(19,4)` para saldos, `NUMERIC(19,8)` para devengos |
| Moneda | Nunca un importe suelto. Siempre `Money(importe, moneda)` |
| Comparación | `compareTo() == 0`, **nunca** `equals()` — `1.0` y `1.00` no son `equals` |
| Año comercial | 360 días, conforme al sistema financiero peruano |

---

## 4. Estilo general

- **Longitud de línea:** 120 caracteres.
- **Indentación:** 4 espacios en Java y Python, 2 en TypeScript. Nunca tabuladores.
- **Un archivo, una responsabilidad.** Si una clase supera ~300 líneas, probablemente hace de más.
- **Métodos cortos.** Si no cabe en una pantalla, extrae.
- **Sin código muerto ni comentado.** Para eso está el historial de git.
- **Sin `TODO` sin ticket.** `// TODO(AYNI-88): soportar cuentas mancomunadas` o nada.

### Comentarios

Comenta el **porqué**, nunca el qué. El qué ya lo dice el código.

```java
// ❌ Suma el interés al saldo
saldo = saldo.add(interes);

// ✅ Se capitaliza el último día del mes porque la SBS exige que el rendimiento
//    publicado como TREA refleje capitalización efectiva, no interés simple.
saldo = saldo.add(interesDevengadoDelMes);
```

### Excepciones

- Excepciones de dominio específicas: `SaldoInsuficienteException`, no `RuntimeException`.
- **Nunca** capturar y silenciar. Si capturas, o resuelves o relanzas con contexto.
- El mensaje de error hacia el usuario **jamás** expone detalle técnico ni interno.

---

## 5. Pruebas

```java
@Test
void debe_rechazar_transferencia_cuando_el_saldo_es_insuficiente() {
    // Dado
    var cuenta = CuentaAhorro.abrir(clienteId, Moneda.PEN);
    cuenta.acreditar(Money.soles("100.00"));

    // Cuando
    var resultado = catchThrowable(() -> cuenta.debitar(Money.soles("200.00")));

    // Entonces
    assertThat(resultado).isInstanceOf(SaldoInsuficienteException.class);
    assertThat(cuenta.saldo()).isEqualTo(Money.soles("100.00"));
}
```

- **Nombre del test:** `debe_<comportamiento esperado>_cuando_<condición>`, en snake_case.
- **Estructura Dado / Cuando / Entonces**, con esos comentarios literales.
- **Un `assert` conceptual por prueba.** Varios `assertThat` sobre el mismo concepto están bien.
- **Sin Spring en las pruebas de dominio.** Si necesitas `@SpringBootTest` para probar una regla de
  negocio, la regla está en el sitio equivocado.
- Los escenarios DADO/CUANDO/ENTONCES de cada Historia de Usuario se implementan como **features de
  Cucumber** en `src/test/resources/features/`.

---

## 6. Registro de eventos (logging)

```java
// ✅ CORRECTO — estructurado, sin datos sensibles
log.info("Transferencia completada transferenciaId={} monto={} moneda={}",
         transferencia.id(), monto.importe(), monto.moneda());

// ❌ PROHIBIDO
log.info("Tarjeta {} usada por {}", tarjeta.pan(), usuario.numeroDocumento());
```

**Nunca se registra:** PAN completo, CVV, contraseña, token, semilla TOTP, número de documento,
datos biométricos, ni imagen alguna.

| Nivel | Cuándo |
|---|---|
| `ERROR` | Falla algo que requiere intervención humana |
| `WARN` | Situación anómala que el sistema resolvió (circuit breaker abierto, reintento) |
| `INFO` | Hitos de negocio: cuenta abierta, transferencia completada |
| `DEBUG` | Detalle técnico, solo en desarrollo |

Todo log lleva `traceId` para poder seguir una operación a través de los cinco servicios.

---

## 7. API REST

| Aspecto | Convención |
|---|---|
| Rutas | plural, kebab-case: `/api/v1/cuentas/{id}/movimientos` |
| Versionado | en la ruta: `/api/v1/` |
| Campos JSON | `camelCase` |
| Fechas | ISO-8601 con zona: `2026-08-14T18:30:00-05:00` |
| Importes | cadena, no número: `"1250.4000"` — evita la pérdida de precisión de JSON |
| Errores | RFC 7807 *Problem Details* |
| Paginación | `?page=0&size=20`, con `totalElements` y `totalPages` en la respuesta |

```json
{
  "type": "https://ayni.pe/errors/saldo-insuficiente",
  "title": "Saldo insuficiente",
  "status": 422,
  "detail": "El saldo disponible es menor al monto solicitado",
  "instance": "/api/v1/transferencias",
  "traceId": "8f2b1c4e9a7d"
}
```

> **Los importes viajan como cadena.** El tipo `number` de JSON es un IEEE-754 de doble precisión;
> serializar `1250.40` puede devolver `1250.3999999999999`. En un banco eso es inaceptable.

---

## 8. Herramientas que lo verifican

| Herramienta | Qué comprueba | Rompe el build |
|---|---|---|
| **ArchUnit** | Arquitectura hexagonal y dependencias entre capas | ✅ |
| **Spotless + Checkstyle** | Formato y estilo Java | ✅ |
| **Ruff + mypy** | Formato y tipado Python | ✅ |
| **ESLint + Prettier** | Formato y estilo TypeScript | ✅ |
| **SonarCloud** | Complejidad, duplicación, code smells, seguridad | ✅ quality gate |
| **JaCoCo** | Cobertura ≥ 80% en el dominio | ✅ |
| **Gitleaks** | Secretos en el código o el historial | ✅ |
| **PIT** | Calidad real de las pruebas (mutación) | informe semanal |

Antes de abrir un Pull Request:

```bash
./mvnw spotless:apply    # corrige el formato automáticamente
./mvnw clean verify      # ejecuta todo lo que el pipeline ejecutará
```
