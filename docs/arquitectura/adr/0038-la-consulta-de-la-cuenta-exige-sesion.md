# ADR-0038 · Toda consulta de la cuenta exige sesión, también al terminar el registro

**Estado:** Aceptada. Reemplaza el riesgo residual que dejó abierto el ADR-0027.
**Fecha:** 29 de octubre de 2026
**Historia:** AYNI-330 (T-37)

---

## Contexto

El ADR-0027 llevó la validación del JWT al gateway, y desde entonces cada operación toma el titular del
token y no de la URL. Quedó una excepción declarada: `GET /api/v1/cuentas/titular/{usuarioId}`, que la
pantalla final del registro consultaba antes de que existiera sesión para enseñar el número de cuenta y el
CCI recién creados.

El ADR-0027 dejó escrito que el riesgo era aceptable porque los identificadores son UUID v4 aleatorios y no
aparecen en ninguna otra respuesta pública. Seguía siendo una consulta sin autenticación que devuelve el
saldo, el número de cuenta y el CCI de quien la pida. Antes de publicar la versión 2.0 en la nube, el riesgo
deja de ser residual.

## Decisión

1. Se elimina `GET /api/v1/cuentas/titular/{usuarioId}`. La consulta de la cuenta propia es
   `GET /api/v1/cuentas/mia`, que toma el titular del token como el resto.
2. El gateway protege **todo** `/api/v1/cuentas` y `/api/v1/cuentas/**`, no solo `/mia`. Una ruta nueva de
   cuentas nace protegida y no puede quedar pública por olvido.
3. La pantalla final del registro confirma que la verificación se aprobó y lleva a iniciar sesión. El número,
   el CCI y el rendimiento se ven en la banca, ya con la sesión abierta.

## Consecuencias

- **A favor:** no queda ninguna consulta de datos de la cuenta sin sesión. La regla se prueba contra las
  rutas que el gateway configura de verdad (`RutasProtegidasDeLaCuentaTest`), no contra una lista copiada en
  la prueba.
- **En contra:** la persona tiene que iniciar sesión para ver su número de cuenta justo después de abrirla.
  Es un paso más, pero también el momento en que aprende a entrar a su banca.
- La pantalla final ya no sondea a core-banking mientras la cuenta se abre. La banca, sin cuenta todavía,
  muestra «Tu cuenta aún no está abierta» y el motivo (HU-08).
