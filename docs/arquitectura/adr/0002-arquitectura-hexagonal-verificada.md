# ADR-0002 · Arquitectura hexagonal verificada por ArchUnit

**Estado:** Aceptada
**Fecha:** 14 de agosto de 2026

---

## Contexto

Cada servicio necesita una organización interna que permita probar las reglas de negocio de forma
rápida y aislada, y que soporte el cambio de tecnología sin reescribir el dominio.

El problema conocido de declarar una arquitectura en un documento es que **se erosiona**. Basta un
`@Entity` colocado por comodidad en una clase de dominio para que, tres sprints después, el dominio
dependa de JPA y ya no se pueda probar sin levantar el contexto de Spring.

## Decisión

**Arquitectura hexagonal (puertos y adaptadores)** en todos los servicios Java, con esta estructura:

```
pe.ayni.bank.<contexto>
├── domain/          modelo, reglas y puertos — sin framework
├── application/     casos de uso, orquestación, @Transactional
└── infrastructure/  adaptadores: web, persistencia, mensajería, clientes
```

Y —esta es la parte que importa— **la regla se verifica automáticamente con ArchUnit como prueba en
CI**. El paquete `domain` no puede importar `org.springframework`, `jakarta.persistence`,
`com.fasterxml.jackson` ni nada de `infrastructure`. Si se viola, **el build falla y el Pull
Request queda bloqueado**.

## Justificación

Una arquitectura que solo vive en un documento es una intención. Una arquitectura que rompe el
pipeline cuando se viola es una **restricción**.

Esta decisión convierte tres atributos de calidad de la ISO/IEC 25010 en algo medible:

| Característica | Cómo se materializa |
|---|---|
| **Modularidad** | Fronteras explícitas y verificadas entre capas |
| **Modificabilidad** | Cambiar PostgreSQL o RabbitMQ toca un solo paquete |
| **Capacidad de prueba** | El dominio se prueba con JUnit puro, sin Spring, en milisegundos |

## Consecuencias

**A favor** — Las pruebas de dominio corren en milisegundos, lo que permite ejecutarlas
continuamente. La erosión arquitectónica se detecta el día que ocurre, no meses después. Es
evidencia objetiva de mantenibilidad para el informe de calidad.

**En contra** — Más clases: hay que mapear entre modelo de dominio y entidad JPA en lugar de
anotar una sola clase. Curva de aprendizaje inicial para el equipo. La verbosidad del mapeo se
mitiga con MapStruct.

**Aceptamos el coste** porque el mapeo explícito es precisamente lo que impide que el esquema de
base de datos dicte el modelo de negocio — que en banca es el error que más caro sale.
