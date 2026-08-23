# Guía de contribución — Ayni Bank

Este documento define **cómo trabajamos**. No es opcional: varias de estas reglas están
automatizadas y el pipeline rechaza lo que no las cumpla.

---

## 1. Flujo de ramas (GitFlow)

```
main       ──●───────────────●──────────────●──   producción · protegida
              ╲             ╱ ╲            ╱
release        ╲   ●───●───●   ╲  ●───●───●       estabilización · SemVer
                ╲ ╱             ╲╱
develop    ──●───●───●───●───●───●───●───●────    integración continua
              ╲   ╱     ╲   ╱
feature        ●─●       ●─●                       una rama por Historia de Usuario
```

| Rama | Nace de | Se fusiona en | Para qué |
|---|---|---|---|
| `main` | — | — | Solo código en producción. **Protegida.** |
| `develop` | `main` | `main` (vía `release`) | Integración de todo lo terminado. **Protegida.** |
| `feature/*` | `develop` | `develop` | Una Historia de Usuario o tarea |
| `bugfix/*` | `develop` | `develop` | Corrección de un defecto no urgente |
| `release/*` | `develop` | `main` y `develop` | Estabilización previa a un despliegue |
| `hotfix/*` | `main` | `main` y `develop` | Corrección urgente en producción |

### Nombres de rama

Siempre en minúsculas, con la clave de Jira y una descripción corta en kebab-case:

```
feature/AYNI-42-registro-de-usuario
feature/AYNI-13-ocr-documento-identidad
bugfix/AYNI-57-saldo-negativo-en-concurrencia
release/1.2.0
hotfix/1.2.1-token-refresh-no-rota
```

### Crear una rama

```bash
git switch develop
git pull origin develop
git switch -c feature/AYNI-42-registro-de-usuario
```

**Nunca** partas una rama de otra rama de feature. Si necesitas algo de un compañero, espera a que
se fusione en `develop`.

---

## 2. Commits — Conventional Commits

```
<tipo>(<ámbito>): <descripción en imperativo y minúscula>

<cuerpo opcional explicando el porqué, no el qué>

Refs: AYNI-42
```

### Tipos permitidos

| Tipo | Cuándo |
|---|---|
| `feat` | Nueva funcionalidad para el usuario |
| `fix` | Corrección de un defecto |
| `docs` | Solo documentación |
| `style` | Formato, espacios, punto y coma — sin cambio de comportamiento |
| `refactor` | Reestructuración sin cambiar comportamiento ni añadir función |
| `perf` | Mejora de rendimiento |
| `test` | Añadir o corregir pruebas |
| `build` | Sistema de construcción, dependencias, Docker |
| `ci` | Pipelines y automatización |
| `chore` | Tareas de mantenimiento sin impacto en `src` |
| `revert` | Revierte un commit anterior |

### Ámbitos

`gateway` · `identity` · `core-banking` · `kyc` · `notification` · `web` · `infra` · `docs` ·
`contracts`

### Ejemplos

```bash
git commit -m "feat(core-banking): calcular devengo diario de interés"
git commit -m "fix(identity): rotar refresh token al renovar la sesión"
git commit -m "test(core-banking): cubrir transferencias concurrentes sobre el mismo saldo"
git commit -m "docs(arquitectura): registrar ADR sobre el patrón outbox"
```

Con cuerpo:

```bash
git commit -m "fix(core-banking): usar BigDecimal en el cálculo de interés

El uso de double introducía error de redondeo acumulativo que en un año
desviaba el saldo en céntimos. Se migra a BigDecimal con escala 8 y
redondeo HALF_EVEN, conforme al criterio bancario.

Refs: AYNI-57"
```

### Reglas duras

- La descripción va en **imperativo** («calcular», no «calculado» ni «calcula»).
- Sin punto final. Máximo 72 caracteres en la primera línea.
- **Un commit, un cambio lógico.** No mezcles refactor con nueva funcionalidad.
- El `CHANGELOG.md` se genera desde estos mensajes: un commit mal escrito ensucia el registro de
  versiones del proyecto.

---

## 3. Pull Requests

### Antes de abrirlo

```bash
# 1. Actualiza tu rama con lo último de develop
git switch develop && git pull origin develop
git switch feature/AYNI-42-registro-de-usuario
git rebase develop

# 2. Verifica en local lo que el pipeline verificará en remoto
./mvnw clean verify
```

### Reproducir el pipeline en local

El Sprint 0 se cerró tras ocho rondas de CI, y **las ocho eran reproducibles en local**. Cada
ronda cuesta entre cinco y quince minutos de espera; reproducirla en local cuesta segundos. Antes
de subir, ejecuta la verificación que corresponda a lo que tocaste.

**Servicios Java.** `verify` incluye ArchUnit; si falla ahí, falla en el CI.

```bash
./mvnw -B clean verify
```

**Servicio Python.** Instala en un **entorno virtual nuevo**, no sobre uno ya poblado. `pip`
actualiza sin reevaluar las restricciones de los paquetes que ya estaban instalados, así que un
conflicto de versiones puede no aparecer en tu máquina y sí en el CI, que siempre instala en
limpio.

```bash
cd services/ayni-kyc-service
python -m venv .venv-check
.venv-check/bin/pip install -r requirements.txt -r requirements-dev.txt   # Scripts/pip en Windows
ruff check . && mypy --strict src/ && pytest -q
```

**Vulnerabilidades de contenedor.** No hace falta Docker: Trivy analiza el jar directamente y
detecta las mismas dependencias que encontraría dentro de la imagen.

```bash
./mvnw -B clean package -DskipTests
mkdir -p /tmp/escaneo && cp services/ayni-gateway/target/*.jar /tmp/escaneo/app.jar
trivy rootfs --scanners vuln --severity CRITICAL,HIGH --ignore-unfixed --exit-code 1 /tmp/escaneo
```

Usa **la misma versión de Trivy que el pipeline** (hoy 0.74.0) o los resultados no serán
comparables.

### Dos causas de fallo que no son culpa tuya

- **`429 Too Many Requests` de Maven Central.** Los cinco jobs de imagen descienden el árbol de
  dependencias a la vez y Central corta. Es transitorio: **Re-run failed jobs**. Si el job
  `Java · calidad y pruebas` está en verde, la versión existe y el problema es de tasa, no del POM.
- **`mvnw: Permission denied`.** El bit de ejecución se perdió al añadir el fichero desde Windows.
  `.gitattributes` **no** lo corrige:

  ```bash
  git update-index --chmod=+x mvnw
  git ls-files -s mvnw        # debe mostrar 100755, no 100644
  ```

### Título del PR

Mismo formato que un commit: `feat(identity): registro de usuario con validación de contraseña`

### Descripción del PR

```markdown
## Qué hace
Breve descripción de la funcionalidad o corrección.

## Historia de Usuario
AYNI-42

## Cómo probarlo
1. Levantar el entorno con `docker compose up`
2. Ir a http://localhost:3000/registro
3. ...

## Criterios de aceptación cubiertos
- [ ] Escenario 1: Registro exitoso con datos válidos
- [ ] Escenario 2: Correo ya registrado
- [ ] Escenario 3: Contraseña que no cumple la política

## Checklist
- [ ] Los escenarios DADO/CUANDO/ENTONCES están implementados como pruebas de Cucumber
- [ ] Cobertura del dominio ≥ 80%
- [ ] ArchUnit en verde
- [ ] Sin secretos, credenciales ni datos personales en el código
- [ ] Sin datos sensibles en los logs (PAN, contraseñas, tokens, documento)
- [ ] Documentación actualizada si el cambio lo requiere
```

### Reglas

- **Mínimo una aprobación** de otro integrante.
- **Todos los checks en verde.** No se fusiona en rojo, ni «porque el fallo no tiene que ver».
- PR pequeño. Si supera unos 400 cambios, divídelo.
- **Squash and merge** hacia `develop`, para que el historial quede legible.
- Quien abre el PR **no** lo aprueba.

### Tras fusionar: la rama se elimina

```bash
# Remoto (o el botón "Delete branch" que aparece tras el merge)
git push origin --delete feature/AYNI-42-registro-de-usuario

# Local
git switch develop
git pull origin develop
git branch -d feature/AYNI-42-registro-de-usuario

# Limpiar referencias a ramas remotas ya borradas
git fetch --prune
```

**No se pierde trazabilidad.** El *squash* arrastra los commits a `develop`, la clave de Jira viaja
en el nombre de la rama, en el mensaje de commit y en el título del PR, y **el Pull Request queda
archivado en GitHub para siempre** con su diff completo, su revisión y su discusión. Conservar
ramas muertas solo ensucia el listado y dificulta encontrar las vivas.

---

## 3.b Versionado y etiquetas

**Cada versión funcional que llega a `main` se etiqueta.** Ese es el mecanismo de reversión: sin
tags no hay a dónde volver.

### Versionado semántico

`MAJOR.MINOR.PATCH` — por ejemplo `v1.2.3`

| Parte | Se incrementa cuando |
|---|---|
| `MAJOR` | Cambio incompatible: se rompe un contrato de API existente |
| `MINOR` | Nueva funcionalidad compatible hacia atrás |
| `PATCH` | Corrección de defecto sin cambio funcional |

Antes del primer despliegue a producción usamos `v0.x.y`. El **`v1.0.0` se reserva para el MVP** del
13 de diciembre.

### Crear una versión

```bash
# 1. Rama de estabilización desde develop
git switch develop && git pull origin develop
git switch -c release/1.0.0

# 2. Actualizar CHANGELOG.md y la versión en los POM
git commit -m "chore(release): preparar la versión 1.0.0"
git push -u origin release/1.0.0

# 3. Pull Request de release/1.0.0 hacia main, con aprobación

# 4. Tras fusionar, etiquetar main
git switch main && git pull origin main
git tag -a v1.0.0 -m "Release 1.0.0 — MVP Ayni Bank

Onboarding KYC con verificación documental y biométrica.
Cuenta de ahorro remunerada en soles y dólares con devengo diario.
Tarjeta de débito virtual con controles del cliente.
Transferencias internas e interbancarias."
git push origin v1.0.0

# 5. Devolver los cambios de la release a develop
git switch develop
git merge --no-ff main
git push origin develop
```

El push del tag **dispara el despliegue a producción**, que queda esperando la aprobación del
Product Owner en el entorno `production` de GitHub.

Después, en GitHub → **Releases → Draft a new release** → seleccionar el tag y publicar las notas.

### Revertir a una versión anterior

```bash
# Ver las versiones disponibles
git tag -l --sort=-version:refname

# Ver qué contenía una versión
git show v1.0.0

# Desplegar una versión anterior: publicar de nuevo ese release,
# o desplegar su imagen desde GHCR
docker pull ghcr.io/load-13/ayni-core-banking-service:1.0.0
```

**Los tags no se borran ni se mueven nunca.** Un tag es un punto fijo en la historia: si se
reescribe, cualquiera que hubiera desplegado esa versión tendría algo distinto de lo que dice tener.
Si una versión sale mal, se publica una nueva (`v1.0.1`), no se corrige la anterior.

---

## 4. Revisión de código

**Quien revisa** busca: corrección de la lógica de negocio (sobre todo si toca dinero), respeto de
la arquitectura hexagonal, ausencia de datos sensibles en logs, pruebas que verifiquen de verdad y
no solo cubran líneas.

**Quien recibe** la revisión no está obligado a aceptar todo. Si una observación es técnicamente
discutible, se discute con argumentos. Lo que no se hace es implementar en silencio algo que uno
cree incorrecto.

Comenta el **código**, nunca a la persona. «Esta consulta hace N+1» es útil; «no sabes usar JPA» no.

---

## 5. Reglas específicas de un sistema financiero

Estas no se negocian y la revisión las rechaza sin discusión:

1. **Todo importe monetario en `BigDecimal`.** Ni `double` ni `float`, nunca, bajo ninguna
   circunstancia. Escala fija y redondeo `HALF_EVEN`.
2. **El saldo no se edita.** Es la suma de sus asientos contables. Si escribes
   `cuenta.setSaldo(...)`, algo está mal planteado.
3. **Todo movimiento de dinero es idempotente**, mediante cabecera `Idempotency-Key`.
4. **Nada sensible en los logs**: PAN, contraseñas, tokens, número de documento, datos biométricos.
   Enmascarado obligatorio.
5. **El paquete `domain` no importa nada de framework.** ArchUnit lo verifica y rompe el build.
6. **Ninguna migración de base de datos se edita después de haberse fusionado.** Se crea una nueva.
7. **Sin secretos en el repositorio.** Gitleaks corre en cada push.

---

## 6. Eventos de Scrum

| Evento | Cuándo | Duración |
|---|---|---|
| Sprint Planning | Primer día del sprint | 2 h |
| Daily Scrum | Diario | 15 min |
| Refinamiento del backlog | Mitad del sprint | 1 h |
| Sprint Review | Último día del sprint | 1 h |
| Sprint Retrospective | Tras el Review | 45 min |

El **Product Owner** (Dr. Carlos R. P. Tovar, docente del curso) prioriza el backlog, formula el
Product Goal y decide los despliegues a producción.
El **Scrum Master** (Joaquín Loa) facilita los eventos, retira impedimentos y vela por la Definition
of Done.
Los **seis integrantes** del equipo son Developers.

---

## 7. Puesta en marcha del entorno

```bash
git clone https://github.com/LOAD-13/ayni-bank.git
cd ayni-bank
cp .env.example .env
docker compose --env-file .env -f infra/docker/docker-compose.yml up -d --wait
```

**`--env-file .env` no es opcional.** Compose busca el `.env` en el directorio del fichero compose
—`infra/docker/`—, no en aquel desde el que lo invocas. Sin esa opción las variables se interpolan
a cadena vacía, y el fallo es confuso porque Compose solo lo advierte: verás a Postgres negarse a
arrancar por contraseña vacía sin que la causa aparezca por ningún lado.

`--wait` hace que el comando no devuelva el control hasta que todos los servicios estén *healthy*,
o falle si alguno no lo consigue. Es lo que convierte «levantó» en «funciona».

Si algo no arranca, revisa primero los health checks:

```bash
docker compose --env-file .env -f infra/docker/docker-compose.yml ps
docker compose --env-file .env -f infra/docker/docker-compose.yml logs -f ayni-core-banking-service
```

Para empezar de cero, incluidos los volúmenes de datos:

```bash
docker compose --env-file .env -f infra/docker/docker-compose.yml down -v
```
