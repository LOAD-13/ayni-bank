# ADR-0005 · Infraestructura híbrida ARM64: Raspberry Pi 5 y Oracle Cloud

**Estado:** Aceptada
**Fecha:** 14 de agosto de 2026

---

## Contexto

El proyecto necesita dos entornos reales —staging y producción— para poder demostrar entrega
continua con promoción entre ellos. El presupuesto disponible es **cero**.

Recursos existentes del equipo: una Raspberry Pi 5 de 8 GB y un dominio de internet ya adquirido.

## Opciones consideradas

**A. Solo Raspberry Pi** — coste nulo, control total. Pero cinco servicios Java más PostgreSQL,
RabbitMQ y MinIO en 8 GB va muy justo; si la Pi falla el día de la sustentación no hay plan B; y
las pruebas de estrés del último sprint la ahogarían.

**B. Solo nube comercial (AWS o Azure)** — máxima credibilidad empresarial y S3 real. Pero el nivel
gratuito caduca a los doce meses, exige tarjeta de crédito, y un descuido con RDS o un NAT Gateway
genera una factura desagradable en una cuenta compartida por cuatro estudiantes.

**C. Híbrido: Pi como staging, nube como producción.**

## Decisión

**Opción C.**

| Entorno | Infraestructura | Uso |
|---|---|---|
| Desarrollo | Máquina local · `docker compose` | Trabajo diario |
| **Staging** | **Raspberry Pi 5** · self-hosted runner de GitHub Actions | Integración y pruebas E2E automáticas |
| **Producción** | **Oracle Cloud Always Free** · 4 vCPU ARM Ampere, 24 GB RAM, 200 GB | Demostración pública con dominio propio |

## Justificación

**Oracle Cloud Always Free ofrece 4 núcleos ARM Ampere, 24 GB de RAM y 200 GB de disco de forma
permanente** — no es una prueba de doce meses. Es holgadamente suficiente para los cinco servicios
más la infraestructura de apoyo.

**El detalle que hace que funcione: ambos entornos son ARM64.** La Raspberry Pi 5 y las instancias
Ampere comparten arquitectura, de modo que **la misma imagen Docker corre en los dos sitios sin
recompilar**. Un problema que aparezca en staging es reproducible en producción y viceversa, algo
que no ocurriría con staging en ARM y producción en x86.

**Dos entornos reales permiten CD con promoción**: fusión a `develop` despliega automáticamente a
staging; fusión a `main` espera aprobación explícita del Product Owner antes de tocar producción.

**Coste total: aproximadamente S/ 4 mensuales**, correspondientes al prorrateo del dominio.

## Consecuencias

**A favor** — Coste efectivamente nulo. Dos entornos reales con paridad de arquitectura. Ninguna
dependencia de servicios propietarios: todo son contenedores estándar. La Pi aporta la narrativa de
infraestructura propia sin ser un punto único de fallo.

**En contra** — El nivel gratuito de Oracle es una oferta comercial modificable unilateralmente
(riesgo **R-10** en la matriz). La Pi depende del suministro eléctrico del domicilio, con una
probabilidad del 99.5% de sufrir al menos un corte durante el proyecto según los indicadores SAIFI
de Osinergmin (riesgo **R-04**).

**Mitigación** — Ambos riesgos están cubiertos por el mismo hecho: la infraestructura está
íntegramente contenedorizada y es portable. Si Oracle cambia sus condiciones, la Pi puede asumir
producción en menos de 24 horas con el mismo `docker compose`. Si la Pi cae, producción no se ve
afectada en absoluto.
