# ADR-0042 · Historial de versiones de las entidades sensibles con Hibernate Envers

**Estado:** Aceptada.
**Fecha:** 26 de noviembre de 2026
**Historia:** AYNI-279 (HU-15)

---

## Contexto

HU-15 pide que cuenta, tarjeta y persona se versionen automáticamente, para reconstruir el estado de
cualquiera de ellas en una fecha pasada y saber quién hizo cada cambio (escenario 2). La pista de
auditoría (`evento_auditoria`, ADR de V14) dice *qué operación* ocurrió, pero no guarda el estado
completo de la entidad antes y después.

Al revisar el código aparecieron dos hechos que condicionan la solución:

- La tabla `identity.persona` (V2) no la escribe ningún flujo: no tiene entidad ni SQL. Los datos de la
  persona que sí cambian (correo, celular, estado, consentimiento) viven en `identity.usuario`.
- El estado de la tarjeta se cambiaba con un `UPDATE` por JDBC, que Envers no ve.

## Decisión

1. **Envers** en `CuentaEntity` y `TarjetaEntity` (core-banking) y en `UsuarioEntity` (identity, como
   registro de la persona). Cada cambio escribe la versión completa en `cuenta_aud`, `tarjeta_aud` o
   `usuario_aud`, y una fila en `revision_auditoria` con el momento y el **actor**, que es el usuario de la
   sesión (`X-Ayni-Usuario`, puesto por el gateway). Sin petición (procesos programados, siembra) no hay
   actor.
2. **Fuera del historial:** el PAN cifrado y su huella (no cambian nunca) y el hash de la contraseña (nadie
   necesita sus versiones viejas).
3. El cambio de estado de la tarjeta pasa de JDBC a JPA para que Envers lo registre.
4. **Solo inserción:** las tablas `_aud` y `revision_auditoria` llevan `REVOKE UPDATE, DELETE, TRUNCATE` y un
   trigger que rechaza esas operaciones con `42501`, igual que la pista de auditoría. Envers, con su
   estrategia por defecto, solo inserta.
5. **Siembra inicial:** las migraciones crean una revisión con el estado actual de cada fila. Sin ella, la
   consulta por fecha de una entidad que no ha cambiado desde que existe Envers no encontraría versión.

## Consecuencias

- La consulta del estado en una fecha (AYNI-280) se apoya en `AuditReader` sobre estas tablas.
- Antes de la siembra no hay historia: el estado anterior al despliegue de esta versión no se puede
  reconstruir.
- Todo cambio futuro a estas entidades debe pasar por JPA. Un `UPDATE` directo no dejaría versión.
- La tabla `persona` sigue sin uso. Si un flujo empieza a escribirla, su entidad debe nacer con `@Audited`.
- Igual que la pista de auditoría, la garantía de solo inserción frente al dueño de la tabla depende de usar
  en producción un rol de migraciones distinto del de la aplicación.
