# language: es
#
# HU-15 · Trazabilidad y pista de auditoría inalterable · AYNI-26
# Pruebas de aceptación: AYNI-285
#
# Corren contra un PostgreSQL de Testcontainers con las migraciones de Flyway aplicadas.

Característica: Trazabilidad y pista de auditoría inalterable
  Como auditor interno
  Quiero que toda acción sobre datos o dinero quede registrada de forma inalterable
  Para poder reconstruir qué ocurrió, quién lo hizo y cuándo, ante un reclamo o una investigación

  Antecedentes:
    Dado que Teresa es clienta con una cuenta de ahorro con S/ 500.00 y su tarjeta de débito virtual

  Escenario: Intento de alterar la pista de auditoría
    Dado que existe en la pista el registro del depósito de Teresa
    Cuando se intenta modificar y borrar ese registro con el usuario de la aplicación
    Entonces la base de datos rechaza las dos operaciones por falta de permisos
    Y el registro original permanece intacto

  Escenario: Reconstrucción del estado histórico
    Dado que un operador pidió bloquear la cuenta de Teresa y un supervisor distinto lo aprobó
    Cuando el auditor consulta el estado de la cuenta de Teresa en una fecha anterior al bloqueo
    Entonces el sistema devuelve la versión que tenía entonces, ACTIVA
    Y al consultarla ahora devuelve la versión BLOQUEADA con el supervisor como actor del cambio

  Escenario: El auditor no puede escribir
    Dado que Óscar ha iniciado sesión con el rol AUDITOR
    Cuando Óscar intenta pedir el bloqueo de la cuenta de Teresa
    Entonces el sistema deniega la operación
    Y el intento de Óscar queda registrado en la propia pista de auditoría

  Escenario: Ausencia de datos sensibles en los logs
    Cuando a Rocío se le abre la cuenta con su tarjeta, deposita y congela la tarjeta mientras se captura el log técnico
    Entonces ninguna línea del log contiene el número completo de la tarjeta de Rocío
    Y si una línea llegara a incluirlo, el patrón de los logs lo muestra enmascarado
