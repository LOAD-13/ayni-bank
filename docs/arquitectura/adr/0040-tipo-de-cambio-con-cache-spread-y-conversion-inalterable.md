# ADR-0040 · Tipo de cambio con caché diaria, spread por segmento y conversión inalterable

**Estado:** Aceptada
**Fecha:** 24 de noviembre de 2026
**Historia:** AYNI-23 (HU-12)

---

## Contexto

HU-12 pide una cuenta en dólares y conversiones entre soles y dólares con un tipo de cambio real. El
diseño (§3.7.1) fija la política: el tipo oficial de SUNAT/BCRP es la referencia y no el precio final;
Ayni cobra un margen de 0.50 % (0.25 % con saldo promedio de S/ 5 000 o más) y la interfaz muestra el
tipo aplicado, el de referencia y el margen antes de confirmar. Hay que fijar lo que esa política no
dice:

- cuándo se consulta la fuente y qué se hace cuando no responde;
- cómo se reparte el margen entre compra y venta, y con qué precisión;
- qué significa «saldo promedio» y con qué datos se mide;
- qué garantiza que el cliente no confirme un tipo que no vio;
- cómo se asegura que una conversión pasada se pueda auditar y recalcular;
- cómo conviven una cuenta en dólares y el cierre diario de intereses, que ya existía.

## Decisión

1. **La fuente se consulta una vez al día y se guarda.** `ObtenerTipoDeCambioService` sirve de la tabla
   `tipo_cambio` lo obtenido desde las 00:00 de hoy en `America/Lima`; si no hay nada, pide a
   `FuenteDeTipoDeCambioPort` (el adaptador del BCRP) y guarda. «Hoy» es el día de Lima: a las 19:00
   de Lima el reloj en UTC ya marca mañana. El guardado es un `INSERT … ON CONFLICT … DO UPDATE` y no
   `DO NOTHING`: en fin de semana la fuente devuelve el valor del viernes, y sin refrescar
   `obtenido_en` la caché de cada día nuevo quedaría vacía y se pediría a la fuente en cada operación.

2. **Si la fuente falla se usa el último valor conocido, avisando.** El adaptador lleva un
   `@CircuitBreaker(name = "bcrp")` y, con el circuito abierto, lanza
   `FuenteDeTipoDeCambioNoDisponibleException`, de modo que el dominio no depende de Resilience4j. El
   servicio responde entonces con la última fila guardada marcada `enCache = true`. Si nunca se guardó
   un tipo, no se inventa ninguno: la API responde 503.

3. **El margen es simétrico y se aplica según el sentido.** Cuando el cliente cambia soles por dólares
   el banco vende: venta de referencia × (1 + spread / 2). Cuando cambia dólares por soles el banco
   compra: compra de referencia × (1 − spread / 2). El tipo aplicado se normaliza a **6 decimales con
   `HALF_EVEN`**, que es la escala de `conversion.tipo_aplicado`: si el tipo usado tuviera más
   decimales que el guardado, recalcular el importe recibido desde lo registrado podría diferir en un
   céntimo.

4. **El segmento sale del saldo promedio de los últimos 30 días cerrados, hasta ayer.** Se usa una
   ventana móvil y no el mes calendario para que el primer día de cada mes un cliente preferencial
   no pase una jornada con la tarifa estándar solo porque aún no hay datos del mes. El saldo de cada
   día es `devengo_interes.saldo_base` de la cuenta en soles del titular, ya calculado por el cierre
   diario (ADR-0032); un día sin devengo cuenta como cero. Se elige el segmento de mayor mínimo que el
   saldo alcanza; si ninguno aplica es un error de datos, no un margen inventado.

5. **Confirmar es volver a cotizar.** `POST /api/v1/conversiones` recibe el tipo que el cliente vio.
   `ConfirmarConversionService` cotiza de nuevo y, si el tipo aplicado ya no es ese, o ahora viene de
   la caché y el cliente no lo sabía, no convierte: responde **409** con la cotización nueva.
   Excepción: un reintento con una clave de idempotencia ya usada devuelve la conversión hecha aunque
   el tipo se haya movido; rechazarlo dejaría al cliente sin saber que su dinero ya se cambió y a un
   paso de repetirlo con otra clave.

6. **La conversión es de solo inserción.** V16 añade a `conversion` la referencia, el margen, el
   segmento y la fuente, y retira `UPDATE`, `DELETE` y `TRUNCATE` con las dos barreras de la pista de
   auditoría (V14): `REVOKE` y un trigger que falla con 42501, que alcanza también al dueño de la
   tabla. Las filas anteriores se completan una sola vez, antes de cerrar la tabla, con el tipo
   aplicado como referencia, margen 0, segmento `ESTANDAR` y fuente `HISTORICO`. Con importe de
   origen, tipo aplicado y referencia se recalcula lo recibido y se comprueba que el tipo sale de la
   referencia más o menos la mitad del margen.

7. **La cuenta en dólares la abre el propio cliente y tiene su contrapartida de intereses.**
   `POST /api/v1/cuentas/mia/dolares` exige la cuenta en soles del onboarding, es idempotente por
   `Idempotency-Key` y responde 409 si ya existe. El producto `AHORRO_USD` tiene tasa vigente, así
   que una cuenta en dólares devenga y, al cerrar el mes, capitaliza. Sin una cuenta técnica de
   intereses en dólares, `cuentaDeIntereses(USD)` lanzaba una excepción y, como cada día se cierra en
   una sola transacción, habría frenado el cierre de todos los clientes. V17 siembra esa cuenta
   (`1e7e0000-…0002`) con el titular reservado de la de soles.

## Consecuencias

- El tipo y el aviso de caché llegan a la pantalla (`enCache`, `obtenidoEn`) y se guardan con la
  conversión (`tipo_en_cache`, `tipo_obtenido_en`).
- El cliente nunca confirma un número que no vio, pero entre cotizar y confirmar puede pasar un 409;
  la pantalla vuelve a mostrar el tipo nuevo.
- El saldo promedio solo mide la cuenta en soles: el dinero que un cliente tiene en dólares no le da
  la tarifa preferencial. Ampliarlo exige decidir cómo valorar los dólares y no se hizo.
- Depósitos simulados y transferencias siguen siendo solo en soles; los dólares se mueven por
  conversión. El tope por conversión (S/ 5 000.00) se aplica en la moneda de origen, así que en
  sentido inverso son US$ 5 000.00.
- La conversión no pide segundo factor: mueve dinero entre cuentas del mismo titular. Si se quisiera
  exigirlo, bastaría con añadir `X-Ayni-Confirmacion` como en las transferencias.
- Las pruebas que vacían tablas deben apagar los triggers de usuario de `conversion`, igual que con la
  pista de auditoría.
- La consulta por un auditor se cubre a nivel de datos y de prueba (feature de Cucumber); una pantalla
  de auditor depende de los roles y del back-office, que son otras historias.
