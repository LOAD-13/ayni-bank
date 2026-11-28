# HU-14 · Evidencia de rendimiento bajo carga

## Objetivos de aceptación

| Indicador | Umbral |
|---|---:|
| Consulta de saldo y movimientos, p95 | < 500 ms |
| Consulta de saldo y movimientos, p99 | < 1 000 ms |
| Tasa de error HTTP durante carga nominal | < 0.1 % |
| Transferencia interna extremo a extremo | < 3 s |
| Onboarding KYC completo | < 90 s |

## Preparación

1. Ejecutar las pruebas únicamente en staging o en un entorno aislado con cuentas sintéticas. No ejecutar estrés de 600 VU contra cuentas reales ni contra producción sin autorización expresa.
2. Levantar AYNI Bank y comprobar que los seis servicios estén saludables.
3. Crear usuarios de prueba diferentes y completar el segundo factor para cada usuario.
4. Copiar `tests/performance/k6/tokens.example.json` a `tests/performance/k6/tokens.local.json` y guardar un JWT de acceso por usuario. Este archivo es local y no debe subirse al repositorio.
5. Preparar 200 JWT para la carga nominal y 600 JWT para la prueba de estrés. Los JWT duran 15 minutos; generarlos inmediatamente antes de cada ejecución.

El inicio de sesión se realiza en dos pasos: `POST /api/v1/sesion` con correo y contraseña, seguido de `POST /api/v1/sesion/segundo-factor` con `desafioId` y código TOTP. El campo `tokenDeAcceso` de la segunda respuesta es el valor que se guarda en el archivo local.

## Ejecución nominal

Desde PowerShell, en la raíz del repositorio:

```powershell
$env:BASE_URL = 'http://localhost:8080'
Copy-Item tests/performance/k6/tokens.example.json tests/performance/k6/tokens.local.json
# Reemplazar los ejemplos por 200 JWT distintos antes de ejecutar.
./tests/performance/run-nominal.ps1
```

El escenario mantiene 200 VU durante 10 minutos y alterna las consultas de saldo y movimientos. El intervalo de 240 segundos entre solicitudes aproxima el volumen normal documentado de 50 solicitudes por minuto y evita sobrepasar el limitador individual del gateway.

## Ejecución de estrés

El punto de quiebre se mide con `infra/carga/estres.js` (AYNI-300), por escalones de usuarios; el procedimiento
completo y los resultados estan en [prueba-de-estres.md](prueba-de-estres.md).

## Registro de resultados (completar después de ejecutar)

| Dato | Resultado observado |
|---|---|
| Fecha y hora (zona America/Lima) | 27-nov-2026, 17:04 a 17:15 |
| Commit probado | `7707b68` (rama de esta HU al dia con develop) |
| Ambiente / URL | Compose local con los limites de CPU y memoria de produccion (como AYNI-300), `http://localhost:8080` |
| ID de ejecución k6 (`testid`) | Sin Remote Write; resumen en `docs/calidad/evidencia/hu14/nominal-resumen.json` |
| VU nominal / duración | 200 / 10 min |
| p95 saldo | 15.68 ms |
| p99 saldo | 26.04 ms |
| p95 movimientos | 9.46 ms |
| p99 movimientos | 12.69 ms |
| p95 transferencia interna | No medido: el escenario nominal es de solo lectura |
| Duración onboarding KYC completo | NO MEDIBLE CON LA INSTRUMENTACIÓN ACTUAL |
| Tasa de error nominal | 0.00 % (0 de 503 peticiones; 503/503 checks) |
| Throughput observado | 48 req/min (0.80 req/s), el perfil de trafico normal de 50 req/min |
| Primer escalón con p95 > 1 s | 800 VU (AYNI-300, ver abajo) |
| Tasa de error en el punto de degradación | 0.00 % en 800 VU; los fallos de 400–500 VU son de conexion del host, no de la aplicacion (ver abajo) |
| CPU / memoria / conexiones DB | Gateway y core-banking contra su techo de CPU; PostgreSQL y Redis holgados (AYNI-300) |
| Comportamiento observado | Degradacion controlada: crece la latencia, sin reinicios, sin falta de memoria ni errores de la aplicacion |
| Evidencia Grafana | No se capturo: las cifras salen de los resumenes de k6 y de `docker stats` |

## Resultado de la carga nominal (AYNI-299)

**Cumple HU-14 con holgura:** con 200 usuarios concurrentes durante 10 minutos, el p95 es de 15.68 ms en saldo y
9.46 ms en movimientos (umbral: 500 ms), el p99 no pasa de 26 ms (umbral: 1 000 ms) y no hubo ningun error.
Los 200 usuarios son clientes distintos preparados con `infra/carga/preparar-clientes.py` (AYNI-300), cada uno con
su token. El perfil es el de trafico normal (unas 50 peticiones por minuto en total): la carga sostenida y
creciente es la de la prueba de estres.

## Punto de quiebre y comportamiento en degradación (AYNI-301)

**El punto de quiebre está en 800 usuarios concurrentes**, el primer escalon con p95 sobre 1 s (1 107 ms), segun la
prueba de estres de AYNI-300 con los limites de produccion. HU-14 se cumple (p95 < 500 ms) hasta 400 usuarios.

| Usuarios | p95 | Errores | Estado |
|---|---|---|---|
| 200 | 106 ms | 0.00 % | dentro del objetivo |
| 400 | 398 ms | 0.08 % | dentro del objetivo |
| 500 | 605 ms | 0.26 % | sobre 500 ms |
| 800 | 1 107 ms | 0.00 % | **punto de quiebre** |
| 1 000 | 1 414 ms | 0.00 % | degradado |

**Como degrada.** Pasado el quiebre la latencia crece, pero ningun servicio se reinicia ni se queda sin memoria y no
hay errores de la aplicacion. La espera es de CPU: gateway y core-banking trabajan contra su techo (0.5 y 0.75
nucleos) mientras PostgreSQL y Redis van holgados. Mas CPU o mas replicas del gateway (AYNI-306) mueven el quiebre;
optimizar consultas, de momento, no.

**Las respuestas distintas de 200 que quedaron por explicar en AYNI-300.** Se repitieron los escalones de 400 y 500
usuarios (27-nov, `infra/carga/estres.js` con `-e NIVELES=400,500`) volcando cada peticion con su codigo:

| Escalon | Peticiones | Distintas de 200 | Codigo |
|---|---|---|---|
| 400 | 51 600 | 195 | 0 (sin conexion) |
| 500 | 96 320 | 469 | 0 (sin conexion) |

Todas tienen **codigo 0**: k6 no llego a conectar (`connectex: No connection could be made … 127.0.0.1:8080`). Las
rechaza el reenvio de puertos de Docker Desktop en Windows cuando cientos de usuarios abren conexion a la vez, antes
de llegar al gateway; por eso ni el gateway ni core-banking las registran. **No son errores de la aplicacion**, sino
del puesto de pruebas. En esta repeticion el salto directo a 400 usuarios, sin calentamiento, ademas infla el p95
de ese escalon (1 209 ms, con un maximo de 31 s en el primer instante). La cifra definitiva debe tomarse en staging,
con Linux y sin el reenvio de puertos de Docker Desktop.

## Alcance de las alertas

Las reglas de Prometheus de esta HU alertan sobre p95/p99 de consultas, tasa de errores y latencia de transferencias. No se crea una alerta falsa para el SLA de 90 segundos del onboarding KYC: el repositorio no tiene una métrica de duración extremo a extremo y el flujo actual deriva la selfie a revisión manual (`EN_REVISION_MANUAL`). El endpoint de extracción OCR es solo una etapa y tiene un timeout interno de 10 segundos; no representa el onboarding completo. Para medir y alertar ese SLA se debe instrumentar el proceso completo y definir qué evento marca su finalización automática.

## Evidencias que se deben adjuntar al PR

- JSON de resumen de k6 de la prueba nominal.
- JSON de resumen de k6 de la prueba de estrés.
- Captura de Grafana con el rango temporal y el `testid` visibles.
- Tabla anterior completada con mediciones reales.
- Si se detecta incumplimiento, enlace al issue/deuda técnica correspondiente.
