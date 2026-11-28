# language: es
#
# HU-12 · Cuenta en dólares y tipo de cambio real · AYNI-23
# Feature de Cucumber: AYNI-275
#
# Corren contra un PostgreSQL de Testcontainers con las migraciones de Flyway aplicadas. Lo único
# simulado es la fuente oficial del tipo de cambio (el BCRP): la caché diaria, el spread, los
# segmentos, el libro mayor y el registro de la conversión son los reales.

Característica: Conversión entre soles y dólares con el tipo de cambio oficial
  Como titular con una cuenta en soles y otra en dólares
  Quiero cambiar mi dinero con un tipo que veo antes de confirmar
  Para saber exactamente cuánto recibo y poder comprobarlo después

  Antecedentes:
    Dado que Ana tiene una cuenta en soles con S/ 1000.00 y una cuenta en dólares
    Y que el BCRP publicó hoy el tipo de cambio: compra 3.745000 y venta 3.752000

  Escenario: Conversión exitosa con el tipo que el cliente vio
    Cuando Ana cotiza la conversión de S/ 376.14 a dólares
    Entonces la cotización muestra el tipo aplicado 3.761380 sobre la referencia oficial 3.752000 con el segmento ESTANDAR
    Cuando Ana confirma la conversión con el tipo que vio
    Entonces el saldo de Ana queda en S/ 623.86 y en US$ 100.00
    Y las posiciones cambiarias del banco recogen S/ 376.14 y US$ -100.00
    Y ningún asiento de la conversión mezcla monedas

  Escenario: El BCRP no responde y se avisa del último tipo conocido antes de confirmar
    Dado que el BCRP no responde y el último tipo guardado, del 2026-11-20, es de compra 3.745000 y venta 3.752000
    Cuando Ana cotiza la conversión de S/ 376.14 a dólares
    Entonces la cotización avisa que usa el último tipo conocido del 2026-11-20
    Cuando Ana intenta confirmar sin haber visto ese aviso
    Entonces la conversión no se realiza y se le devuelve la cotización nueva
    Y el saldo de Ana sigue en S/ 1000.00 y en US$ 0.00
    Cuando Ana confirma la conversión habiendo visto el aviso
    Entonces el saldo de Ana queda en S/ 623.86 y en US$ 100.00
    Y la conversión queda registrada con el tipo en caché

  Escenario: Un auditor recalcula el importe recibido con lo que quedó registrado
    Cuando Ana cotiza la conversión de S/ 376.14 a dólares
    Y Ana confirma la conversión con el tipo que vio
    Y un auditor consulta la conversión registrada
    Entonces el importe recibido US$ 100.00 se recalcula con el tipo aplicado y el importe de origen
    Y el tipo aplicado es la referencia oficial más la mitad del margen del segmento
    Y la conversión registrada no se puede modificar ni borrar
