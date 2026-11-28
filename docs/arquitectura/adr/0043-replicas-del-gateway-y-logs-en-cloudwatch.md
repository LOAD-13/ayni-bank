# ADR-0043 · Dos réplicas del gateway en producción y logs en CloudWatch

**Estado:** Aceptada.
**Fecha:** 28 de noviembre de 2026
**Historia:** AYNI-348 (HU-16)

---

## Contexto

Producción corre en una sola EC2 (ADR-0026). Las réplicas de AYNI-306 se probaron en el compose local,
pero en producción cada servicio tenía una sola copia: si el gateway caía, la API entera dejaba de
responder hasta que Docker lo reiniciara (26,8 s medidos en el experimento E1 de la semana 16).

Los logs de los contenedores se quedaban en el disco de la instancia (`json-file`). Para leerlos había que
entrar por SSM, y al apagar o reemplazar la instancia se perdían. CloudWatch solo recibía los logs de RDS.
Además, ninguna línea de log identificaba la petición que la produjo.

## Decisión

1. **Gateway ×2 tras Caddy.** `ayni-gateway` y `ayni-gateway-2` usan la misma imagen y la misma
   configuración. Caddy reparte por turnos, saca del reparto la réplica que no pasa
   `/actuator/health` (cada 5 s) y reintenta durante 5 s en la otra una petición que no llegó a ninguna.
   La cabecera `X-Ayni-Instancia` indica qué réplica atendió.
   - Es seguro porque el gateway no guarda estado: la sesión viaja en el token y el límite de tasa vive
     en Redis (ADR-0035).
   - core-banking y notification siguen con una copia por sus tareas programadas
     (`docs/despliegue/replicas-de-servicios.md`). identity, también una por ahora, para no pasar la
     memoria de la `m7i-flex.large`.
2. **Logs a CloudWatch Logs** con el driver `awslogs`: grupo `/ayni/prod`, un flujo por contenedor y
   modo no bloqueante. El rol de la instancia ya tiene `CloudWatchAgentServerPolicy`. Docker guarda una
   copia local, así que `docker logs` sigue funcionando.
3. **Id de petición en cada línea.** Micrometer Tracing con Brave pone el `traceId` en el MDC, y el
   patrón de log de los cuatro servicios Java lo imprime. No hay colector de trazas: solo sirve para
   correlacionar líneas.
4. **Métricas del sistema operativo.** El agente de CloudWatch (memoria y disco) se instala por SSM,
   según `docs/despliegue/manual-de-despliegue.md`. CPU, red y disco de EBS ya llegan como métricas
   estándar de EC2.

## Consecuencias

- Una caída del gateway deja de interrumpir la API: la otra réplica la sigue atendiendo.
- El gateway usa unos 350 MiB más de memoria (límite de 640 MiB).
- Los logs sobreviven a la instancia y se consultan desde la consola o con Logs Insights. El costo
  previsto es de centavos al mes con el volumen actual.
- La instancia sigue siendo un punto único de falla. Dos instancias tras un balanceador de AWS son la
  mejora siguiente.
