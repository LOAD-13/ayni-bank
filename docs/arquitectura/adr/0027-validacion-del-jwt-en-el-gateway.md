# ADR-0027 · Validación del JWT en el gateway e identidad por cabecera

**Estado:** Aceptada
**Fecha:** 2 de octubre de 2026
**Tarea:** AYNI-122

---

## Contexto

Hasta ahora cada servicio tenía que validar el token de acceso por su cuenta, y core-banking no lo
hacía: la consulta de la cuenta tomaba el titular de la URL (`/cuentas/titular/{usuarioId}`). Era
aceptable mientras el servicio no estaba publicado y la única pantalla que la usaba era la final del
onboarding. Con las transferencias (HU-07) y el panel (HU-08) en una URL pública deja de serlo:
cualquiera que conociera o adivinara un identificador podría ver o mover el dinero de otro.

## Decisión

1. **El gateway valida el JWT** (HS256, misma clave que lo firma identity) en las rutas protegidas:
   `/api/v1/cuentas/mia/**` y `/api/v1/transferencias/**`. Sin token, con firma inválida, caducado,
   de otro emisor o con algoritmo `none`, responde **401** y la petición no llega a ningún servicio.
2. Si el token es válido, reenvía la petición con la cabecera **`X-Ayni-Usuario`** igual al `sub`.
   La cabecera se **borra siempre** de la petición entrante: un cliente no puede fabricarla.
3. Los servicios toman la identidad **solo** de esa cabecera. Las rutas nuevas no tienen ningún
   identificador de usuario: no se puede pedir la cuenta de otro porque no hay dónde escribirlo.
4. Las operaciones monetarias exigen la cabecera **`Idempotency-Key`**. Repetir la petición con la
   misma clave devuelve el mismo comprobante y no mueve el dinero dos veces.

`/api/v1/cuentas/titular/{usuarioId}` queda por ahora fuera de la protección: la pantalla final del
onboarding la consulta antes de que exista una sesión. Es un riesgo residual declarado: los
identificadores son UUID v4 aleatorios y no se exponen en ninguna otra respuesta pública. Se
retirará cuando esa pantalla pase a usar la sesión.

## Consecuencias

- La validación vive en un solo sitio y no depende de que cada servicio se acuerde.
- Los servicios siguen sin ser alcanzables desde fuera: solo Caddy publica puertos, y Caddy solo
  habla con el gateway y la web.
- HS256 obliga a que el gateway conozca la clave de firma. Si mañana un tercero tuviera que
  verificar tokens sin poder firmarlos, se pasa a RS256 cambiando solo el emisor y el verificador.
