# language: es
#
# HU-06 · Rendimiento diario del saldo con TREA · AYNI-17
#
# Corren contra un PostgreSQL de Testcontainers con las migraciones de Flyway aplicadas:
# el saldo de cierre, la tasa vigente y la partida doble los resuelve la base de verdad.

Característica: Rendimiento diario del saldo con TREA
  Como cliente
  Quiero que mi saldo genere intereses todos los días y ver cuánto he ganado
  Para comprobar que mi dinero crece y entender exactamente cuánto rinde

  Antecedentes:
    Dado que la Cuenta Ayni Soles tiene una TEA de 5 % desde el 2026-09-01
    Y que Ana abrió su cuenta Ayni el 2026-10-01

  Escenario: Devengo diario correcto
    Dado que Ana tiene un saldo de S/ 1000.00 desde el 2026-10-01
    Cuando se ejecuta el cierre del 2026-10-15
    Entonces el devengo de Ana del 2026-10-15 es de 0.13553742 sobre un saldo base de 1000.00 al 5.00 %
    Y ese importe es 1000 × ((1.05)^(1/360) − 1) con BigDecimal redondeado a 8 decimales con HALF_EVEN

  Escenario: Capitalización mensual
    Dado que Ana tiene un saldo de S/ 1000.00 desde el 2026-10-01
    Cuando se ejecutan los cierres del 2026-10-01 al 2026-10-31
    Entonces a Ana se le abonan S/ 4.20 de intereses con asientos que suman cero
    Y el saldo de Ana es S/ 1004.20
    Y la fracción de 0.00166002 pasa a noviembre
    Y se publica el evento InteresCapitalizado para avisar a Ana

  Escenario: Cambio de tasa preservando el histórico
    Dado que Ana tiene un saldo de S/ 1000.00 desde el 2026-10-01
    Y que ya se ejecutó el cierre del 2026-10-14
    Cuando la TEA cambia a 4 % desde el 2026-10-15
    Y se ejecuta el cierre del 2026-10-15
    Entonces el devengo de Ana del 2026-10-14 es de 0.13553742 sobre un saldo base de 1000.00 al 5.00 %
    Y el devengo de Ana del 2026-10-15 es de 0.10895236 sobre un saldo base de 1000.00 al 4.00 %
    Y la consulta de rendimiento del 2026-10-16 muestra la TREA de 4.00 % y los devengos al 5.00 % y al 4.00 %

  Escenario: Saldo cero
    Cuando se ejecuta el cierre del 2026-10-15
    Entonces Ana no tiene devengo del 2026-10-15
    Y el saldo de Ana es S/ 0.00
