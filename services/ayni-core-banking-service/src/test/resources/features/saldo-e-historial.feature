# language: es
#
# HU-08 · Consulta de saldo e historial de movimientos · AYNI-19
# Pruebas de aceptación: T-32 · AYNI-232
#
# Corren contra un PostgreSQL de Testcontainers con las migraciones de Flyway aplicadas: el
# saldo es la suma de los asientos que calcula la base, no una columna.
#
# Los escenarios @pendiente describen un criterio que el código todavía no cumple: están
# escritos para que la brecha quede a la vista y no se ejecutan (ver PruebasDeAceptacion).

Característica: Consulta de saldo e historial de movimientos
  Como cliente
  Quiero consultar mi saldo y el historial de mis movimientos con filtros
  Para saber en todo momento cuánto tengo y en qué se ha ido mi dinero

  Antecedentes:
    Dado que Ana tiene su cuenta Ayni en soles
    Y que Ana recibió estos depósitos:
      | fecha      | importe |
      | 2026-10-01 | 100.00  |
      | 2026-10-10 | 50.00   |
      | 2026-10-20 | 25.00   |

  Escenario: Saldo disponible calculado desde los movimientos
    Cuando Ana consulta su saldo y sus últimos movimientos
    Entonces el saldo disponible de Ana es S/ 175.00
    Y Ana ve sus movimientos del más reciente al más antiguo: 25.00, 50.00 y 100.00

  # AYNI-329: filtro de movimientos por rango de fechas, tipo, importe y saldo posterior.
  Escenario: Consulta de movimientos con filtro
    Cuando Ana filtra sus movimientos del 2026-10-05 al 2026-10-15
    Entonces Ana ve únicamente el depósito de S/ 50.00 del 2026-10-10
    Y ese movimiento muestra un saldo posterior de S/ 150.00

  Escenario: Intento de acceso a un movimiento ajeno
    Dado que Bruno tiene su cuenta Ayni en soles
    Y que Bruno recibió estos depósitos:
      | fecha      | importe |
      | 2026-10-05 | 80.00   |
    Cuando Ana solicita el comprobante del depósito de Bruno
    Entonces el sistema le deniega el acceso a Ana sin revelar el movimiento
    Pero Bruno sí puede consultar el comprobante de su depósito

  # AYNI-328
  Escenario: Intento de acceso a un movimiento ajeno · registro como evento de seguridad
    Dado que Bruno tiene su cuenta Ayni en soles
    Y que Bruno recibió estos depósitos:
      | fecha      | importe |
      | 2026-10-05 | 80.00   |
    Cuando Ana solicita el comprobante del depósito de Bruno
    Entonces el intento de Ana queda registrado como evento de seguridad en la pista de auditoría
