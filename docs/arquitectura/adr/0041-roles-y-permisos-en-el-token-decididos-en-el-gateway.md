# ADR-0041 · Roles y permisos en el token, decididos en el gateway

**Estado:** Aceptada
**Fecha:** 24 de noviembre de 2026
**Historia:** AYNI-281 (HU-17)

---

## Contexto

El diseño (§5.4) define cinco roles con segregación de funciones: cliente, operador, supervisor,
auditor y oficial de cumplimiento. El catálogo de roles, permisos y `rol_permiso` existe desde la
migración V1 de identity, pero nada lo usaba: no había una tabla que dijera qué rol tiene cada
usuario, el JWT solo llevaba el estado y el gateway solo comprobaba que la sesión fuera válida.

## Decisión

1. **`usuario_rol` dice quién tiene qué (V13).** Quienes ya estaban registrados pasan a ser
   `CLIENTE`, y un trigger sobre `usuario` hace cliente a todo usuario nuevo, sea cual sea el camino
   por el que se cree. Va en la base y no en el código del registro: con el gateway decidiendo por
   permisos, un alta sin rol sería una cuenta que no puede usar nada. El trigger escribe el schema
   (`identity.usuario_rol`) porque corre con el `search_path` de quien inserta, no con el de Flyway.

2. **Los permisos viajan en el token, en el claim `permisos`.** `EmisorDeTokensJwt` los lee de
   `LectorDePermisosPort` al emitir. Son códigos de catálogo (`cuenta:leer`), no datos personales.
   Un cambio de rol se nota en la siguiente renovación, como mucho a los quince minutos. Un usuario
   sin roles recibe el claim vacío, no ausente.

3. **El gateway decide por ruta.** `ayni.seguridad.permisos-por-ruta` lista reglas
   `[METODO:]patron=permiso`; gana la primera que case y una ruta protegida sin regla solo exige
   sesión. Una sesión válida sin el permiso recibe 403 (RFC 7807), distinto del 401 de quien no ha
   iniciado sesión. Una regla mal escrita impide arrancar, en lugar de dejar una ruta sin proteger.

4. **Los servicios reciben los permisos como información.** El gateway reenvía `X-Ayni-Permisos` y la
   borra siempre de lo que llega del cliente, igual que `X-Ayni-Usuario`. Los servicios que mueven
   dinero o muestran datos sensibles deben volver a comprobarlo: el gateway es la primera barrera y
   no la única.

5. **Los tokens anteriores a esta decisión siguen valiendo como los de un cliente.** Sin el claim
   `permisos`, el gateway aplica los cuatro permisos del cliente durante la vida del token (quince
   minutos), para que un despliegue no cierre la sesión de nadie. Todos los tokens emitidos antes
   eran de clientes. Un token nuevo siempre lleva el claim.

## Consecuencias

- La matriz de cinco roles por operación está en `PermisosPorRolTest`, leída de las reglas reales de
  `application.yml`: cambiar una regla sin pensarlo rompe la prueba.
- Los roles de personal no tienen todavía forma de iniciar sesión en desarrollo: crear sus usuarios
  (con contraseña y segundo factor) es un seed de desarrollo pendiente, no una migración versionada.
- Las reglas actuales cubren las rutas del cliente. Las rutas de personal (por ejemplo la lectura de
  la pista de auditoría con `auditoria:leer`) añaden su regla cuando se crean.
- La segregación «quien inicia no aprueba» entre operador y supervisor es por rol; que una misma
  persona no inicie y apruebe la misma operación debe comprobarse en el servicio que la ejecute.
