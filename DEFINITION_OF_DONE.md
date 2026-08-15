# Definition of Done — Ayni Bank

Compromiso del equipo asociado al Incremento. **Un ítem no está terminado hasta que cumple todo lo
que sigue.** «Casi terminado» no existe: o está en la columna Hecho cumpliendo esta lista, o sigue
en curso.

---

## Para una Historia de Usuario

### Funcionalidad
- [ ] Todos los criterios de aceptación de la historia están implementados.
- [ ] Cada escenario **DADO/CUANDO/ENTONCES** está automatizado como prueba de Cucumber y pasa.
- [ ] Los casos de error y los límites están cubiertos, no solo el camino feliz.

### Código
- [ ] Respeta la arquitectura hexagonal — **ArchUnit en verde**.
- [ ] Cumple `CODESTYLE.md`; Spotless y Checkstyle en verde.
- [ ] Sin código muerto, sin código comentado, sin `TODO` huérfano.
- [ ] Todo importe monetario en `BigDecimal` con escala y redondeo correctos.
- [ ] Sin datos sensibles en logs: PAN, contraseña, token, documento, biometría.

### Pruebas
- [ ] Pruebas unitarias de dominio, **sin Spring**.
- [ ] Pruebas de integración con Testcontainers si toca base de datos, mensajería o almacenamiento.
- [ ] **Cobertura del dominio ≥ 80%.**
- [ ] Todas las pruebas del proyecto pasan, no solo las nuevas.

### Calidad y seguridad
- [ ] Quality gate de **SonarCloud** superado.
- [ ] **Gitleaks** sin hallazgos.
- [ ] **Trivy** y **Dependency-Check** sin vulnerabilidades críticas ni altas.
- [ ] Si hay interfaz: **axe-core** sin violaciones de WCAG 2.1 AA.

### Integración
- [ ] Pull Request con **al menos una aprobación** de otro integrante.
- [ ] Todos los checks del pipeline en verde.
- [ ] Fusionado a `develop` y **desplegado automáticamente a staging**.
- [ ] Pruebas de humo en staging correctas.

### Documentación
- [ ] Contrato OpenAPI actualizado si cambió alguna API.
- [ ] Migración Flyway incluida si cambió el esquema.
- [ ] **ADR registrado** si se tomó una decisión arquitectónica.
- [ ] Ticket de Jira movido a Hecho con enlace al PR.

---

## Para un Sprint

- [ ] Todos los ítems comprometidos cumplen la Definition of Done anterior.
- [ ] El incremento es **desplegable a producción** en cualquier momento.
- [ ] Sprint Review realizado con demostración sobre el entorno de staging.
- [ ] Sprint Retrospective realizada con acciones de mejora concretas y asignadas.
- [ ] Backlog refinado para el siguiente sprint.
- [ ] Sin regresiones respecto del sprint anterior.

---

## Para un despliegue a producción

- [ ] Rama `release/x.y.z` creada desde `develop` y estabilizada.
- [ ] Suite completa de Playwright en verde sobre staging.
- [ ] Prueba de carga con **k6**: p95 < 500 ms.
- [ ] **OWASP ZAP** ejecutado sin hallazgos de riesgo alto.
- [ ] `CHANGELOG.md` actualizado.
- [ ] Tag de versión semántica creado.
- [ ] **Aprobación explícita del Product Owner** en el entorno `production` de GitHub.
- [ ] Respaldo de base de datos verificado antes de desplegar.
- [ ] Pruebas de humo posteriores al despliegue en verde.
- [ ] Plan de reversión disponible y probado.

---

## Lo que NO cuenta como terminado

Se dice explícitamente porque son las excusas habituales:

- «Funciona en mi máquina.»
- «Falta solo escribir las pruebas.»
- «El check falla, pero no tiene que ver con mi cambio.»
- «Lo documento después.»
- «Ya lo subí a develop, luego lo reviso alguien.»
- «La cobertura bajó un poco, pero es poco.»

Si algo de esto aplica, **el ítem sigue en curso**.
