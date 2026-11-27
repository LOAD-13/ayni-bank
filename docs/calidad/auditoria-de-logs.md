# Auditoría de los logs técnicos: sin datos sensibles

**Historia:** HU-15, criterio 4 y escenario 4 · AYNI-283 · 26-nov-2026

## Alcance

Ningún log técnico (los que llegan a Loki) puede contener el PAN completo, contraseñas, tokens ni datos del
documento de identidad. Se revisaron los cinco servicios: gateway, identity, core-banking, notification y
kyc.

## Cómo se revisó

1. **Código:** todas las llamadas `log.info/warn/error/debug` de los servicios Java y `logger.*` de kyc, con
   foco en las que llevan correo, celular, PAN, token, contraseña, DNI, documento o IP, y en las que
   registran el mensaje de una excepción.
2. **Ejecución:** el recorrido E2E completo (ingreso con TOTP, depósito, transferencias, interbancaria,
   tarjeta) contra el compose local, y búsqueda en los logs de identity y del gateway de JWT (`eyJ…`), PAN
   (16 cifras seguidas) e IP.

## Resultado

| Dato | Resultado |
|---|---|
| PAN | Solo se registra enmascarado (`Pan.enmascarado()`, últimos 4). Ninguna traza con el número completo. |
| Contraseñas | Ninguna traza las incluye; solo se registra que se cambiaron, con el `usuarioId`. |
| Tokens | Ninguna traza incluye el token de acceso, el de renovación ni el de confirmación. |
| Documento | Los errores de validación del DNI nombran el campo, nunca su valor. La auditoría de kyc no lleva datos personales. |
| Correo y celular | Siempre enmascarados (`correo.enmascarado()`, `celular.enmascarado()`). |
| **IP** | **Hallazgo:** cinco trazas de inicio de sesión de identity registraban la IP, en contra de la regla del propio servicio («la IP es dato personal: vive en la tabla, no en las trazas»). **Corregido:** se quitó de las trazas. Los cinco eventos ya guardaban la IP en la pista de auditoría, así que no se pierde información para investigar. |
| E2E | 0 JWT, 0 PAN y 0 IP en los logs de identity y del gateway tras el recorrido completo. |

## Red de seguridad

Que el código hoy no registre datos sensibles no impide que mañana se escape uno, por ejemplo en el mensaje
de una excepción. Los cuatro servicios Java tienen en su `application.yml` un patrón de consola que enmascara
antes de escribir, también dentro de las trazas de excepciones:

- tokens JWT y `Bearer …` → `[TOKEN]`;
- `contrasena=`, `password=` y `clave=` → `***`;
- un DNI etiquetado (`DNI 12345678`) → `********`;
- cualquier número de 13 a 19 cifras (PAN) → los 4 primeros y los 4 últimos.

`LogsSinDatosSensiblesTest`, en cada servicio, lee ese patrón del mismo `application.yml` y comprueba que
enmascara cada caso y que deja intacto lo que no es sensible (identificadores e importes).

kyc no tiene esa red: sus dos únicas trazas (la auditoría JSON del cotejo facial y un error genérico) no
llevan datos personales. Si se añaden trazas con datos del documento, hay que darle un filtro equivalente.
