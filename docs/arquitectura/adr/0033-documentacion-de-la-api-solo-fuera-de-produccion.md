# ADR-0033 · Documentación OpenAPI viva, solo fuera de producción

**Estado:** Aceptada
**Fecha:** 22 de octubre de 2026
**Historia:** AYNI-317 (taller de interoperabilidad, ISO/IEC 25010), subtarea AYNI-332

---

## Contexto

La interoperabilidad (ISO/IEC 25010) exige contratos formales: tipos, formatos, códigos HTTP y
esquemas de error que el consumidor pueda leer sin abrir el código. Hasta ahora solo identity y kyc
tenían contrato, escrito a mano en `contracts/`, y ninguno se podía consultar ni probar en vivo.
core-banking, el servicio que mueve el dinero, no tenía ninguno.

El taller de la sesión 21 pide Swagger UI accesible. Pero publicar en producción el mapa completo
de la API de un banco (cada ruta, cada parámetro y cada formato de error) le ahorra a un atacante
justo el trabajo de reconocimiento que más tiempo le cuesta. El proyecto es académico, y aun así
producción se trata como si no lo fuera.

## Decisión

**springdoc genera el contrato OpenAPI del código, y está apagado por defecto en todos los
servicios.**

- identity y core-banking exponen `/v3/api-docs` solo con `AYNI_API_DOCS=true`; kyc expone `/docs`
  y `/openapi.json` solo con `KYC_API_DOCS=true`. Sin la variable, las rutas no existen y responden
  404. notification no tiene API HTTP que documentar.
- El gateway sirve Swagger UI en `/api/docs` y agrupa los tres contratos. Las rutas hacia ellos se
  crean en código (`RutasDeDocumentacion`), condicionadas a la misma variable, y no en
  `application.yml`, donde existirían siempre. Es además la única ruta hacia kyc, que no tiene
  ninguna pública (ADR-0028).
- **Solo `infra/docker/docker-compose.yml`, el entorno local, pone esas variables.** El compose de
  producción no las lleva.
- El servidor que declara cada contrato es `/`, no la dirección interna del servicio: «Probar» en
  Swagger UI llama al gateway, que es el que valida el token.

**Tres pruebas hacen que esto no dependa de la memoria de nadie:**

1. Cada servicio Java comprueba que su `application.yml`, sin variables de entorno, deja la
   documentación en `false`.
2. El gateway comprueba que sin la variable no se crean las rutas hacia los contratos, y que
   `docker-compose.prod.yml` no contiene `API_DOCS`.
3. kyc comprueba que `/docs`, `/redoc` y `/openapi.json` responden 404 por defecto.

Si alguien enciende la documentación por error en la configuración o en el compose de producción,
la integración continua lo bloquea.

## Por qué en `develop` y no en una rama local

El código queda versionado y revisado, y la evidencia se reproduce en cualquier momento con
`docker compose up`. Una rama sin fusionar habría que rehacerla en cada avance, y no la vería quien
revise el repositorio. Lo que llega a producción es el mismo código con la documentación apagada.

## Consecuencias

- Los contratos de `contracts/` siguen siendo la fuente de verdad del contrato identity ↔ kyc, del
  que se genera el cliente Java (diseño base §3.5). Swagger UI no los reemplaza: los muestra.
- springdoc añade dependencias a las imágenes de identity, core-banking y gateway, que Trivy escanea
  como al resto.
- Para usarla: `docker compose --env-file .env -f infra/docker/docker-compose.yml up -d --wait` y
  abrir `http://localhost:8080/api/docs`.
