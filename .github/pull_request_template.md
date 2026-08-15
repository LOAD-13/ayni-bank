## Qué hace

<!-- Breve descripción de la funcionalidad o corrección. El porqué, no solo el qué. -->

## Historia de Usuario

<!-- Clave de Jira, por ejemplo: AYNI-42 -->
Refs: AYNI-

## Cómo probarlo

1. Levantar el entorno: `docker compose -f infra/docker/docker-compose.yml up -d`
2.
3.

## Criterios de aceptación cubiertos

<!-- Los escenarios DADO/CUANDO/ENTONCES de la Historia de Usuario -->
- [ ] Escenario 1:
- [ ] Escenario 2:
- [ ] Escenario 3:

---

## Checklist

**Funcionalidad y pruebas**
- [ ] Todos los criterios de aceptación están implementados
- [ ] Cada escenario DADO/CUANDO/ENTONCES está automatizado como prueba de Cucumber
- [ ] Casos de error y valores límite cubiertos, no solo el camino feliz
- [ ] Cobertura del dominio ≥ 80%

**Arquitectura y estilo**
- [ ] ArchUnit en verde — el dominio no importa Spring, JPA ni infraestructura
- [ ] Cumple `CODESTYLE.md`
- [ ] Sin código muerto, comentado, ni `TODO` sin ticket

**Reglas de sistema financiero**
- [ ] Todo importe monetario en `BigDecimal` con escala y redondeo `HALF_EVEN`
- [ ] El saldo no se edita: se deriva de los asientos contables
- [ ] Las operaciones monetarias son idempotentes (`Idempotency-Key`)

**Seguridad**
- [ ] Sin secretos, credenciales ni datos personales en el código
- [ ] Sin datos sensibles en logs: PAN, contraseña, token, documento, biometría
- [ ] La autorización se verifica en el servicio, no solo en la interfaz

**Documentación**
- [ ] Contrato OpenAPI actualizado si cambió alguna API
- [ ] Migración Flyway incluida si cambió el esquema (y **no** se editó una ya fusionada)
- [ ] ADR registrado si se tomó una decisión arquitectónica

---

<!--
Recordatorios:
· PR pequeño. Si supera ~400 líneas de cambio, divídelo.
· Mínimo una aprobación de otro integrante. Quien abre el PR no lo aprueba.
· No se fusiona con checks en rojo, ni "porque el fallo no tiene que ver con mi cambio".
· Squash and merge hacia develop.
-->
