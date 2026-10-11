# ADR-0031 · Confirmación de transferencias con el segundo factor

**Estado:** Aceptada
**Fecha:** 11 de octubre de 2026
**Historia:** AYNI-18 (HU-07)

---

## Contexto

HU-07 exige que la transferencia se confirme con el segundo factor. El secreto del segundo
factor y los códigos por correo viven en identity; el dinero se mueve en core-banking. Hay que
unir las dos cosas sin que core-banking dependa en caliente de identity y sin que un código
confirmado sirva para una operación distinta de la que el titular vio en pantalla.

Se descartaron dos alternativas:
- **core-banking llama a identity** para validar el código. Acopla los servicios: si identity cae,
  no hay transferencias.
- **El gateway orquesta** la verificación. Mete lógica de negocio en el gateway, que hoy solo
  valida el JWT y enruta.

## Decisión

Confirmación por **token de un solo propósito** (el patrón *step-up* de la banca):

1. La web pide a identity una confirmación para la operación: `POST /api/v1/confirmaciones` con
   el destino, el importe, la moneda y la clave de idempotencia. identity guarda **solo la huella**
   de la operación y, si el titular eligió el correo, le envía un código.
2. El titular escribe el código: `POST /api/v1/confirmaciones/{id}/verificacion`. identity lo
   comprueba con la app autenticadora o con el código del correo y emite un **JWT de 5 minutos**:
   - firmado con una clave **propia** (`AYNI_CONFIRMACION_CLAVE`), distinta de la del token de
     acceso, de modo que un token no pueda pasar por el otro;
   - con `aud=ayni-core-banking`, `tipo=confirmacion`, `sub=<usuario>`, `op=<huella>` y
     `jti=<confirmación>`.
3. La web envía la transferencia con la cabecera `X-Ayni-Confirmacion`. core-banking recalcula la
   huella con lo que llega, y solo transfiere si la firma, la audiencia, el tipo, la caducidad, el
   usuario y la huella coinciden. Si no, responde **403 `CONFIRMACION_REQUERIDA`** sin tocar ningún
   saldo.

**Forma canónica de la operación** (contrato entre los dos servicios):
`TRANSFERENCIA|<destino sin espacios ni guiones>|<importe con dos decimales>|<moneda>|<clave>`,
y la huella es su SHA-256 en Base64. Las suites de identity y de core-banking comprueban **el
mismo vector de referencia**, calculado aparte con `openssl`. Si una cambia sin la otra, la CI
falla.

**Defensas:**
- 3 intentos por confirmación, uso único y 5 minutos de vida.
- **Los fallos cuentan para el mismo contador que el ingreso.** Sin eso, quien tuviera la sesión
  podría abrir confirmaciones sin fin y probar códigos de tres en tres. Al sexto fallo se pausa
  todo, igual que con la contraseña.
- Los fallos se guardan aunque se lance la excepción (`noRollbackFor`, la lección de AYNI-161).

**Reintentos:** si la transferencia ya se hizo con esa clave de idempotencia, core-banking
devuelve el comprobante original **sin pedir otra confirmación**. Repetir no mueve dinero, y un
reintento tras la caducidad del token no debe convertirse en un error para el cliente.

El depósito simulado no pide segundo factor: no saca dinero de la cuenta del titular.

## Consecuencias

- core-banking verifica sin llamar a nadie: si identity está caído no se pueden **confirmar**
  transferencias nuevas, pero las confirmadas en los últimos minutos se completan.
- El token está atado a la clave de idempotencia, así que no hace falta guardar los `jti` usados
  en core-banking: reutilizarlo solo puede repetir la misma operación, y esa repetición ya la
  resuelve la idempotencia.
- Producción necesita el parámetro SSM `/ayni/prod/confirmacion-clave` (Base64 de 32 bytes
  aleatorios) **antes** de desplegar: `desplegar.sh` lo exige y sin él los dos servicios no
  arrancan.
