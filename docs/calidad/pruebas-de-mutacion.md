# Pruebas de mutación del dominio · AYNI-304

La cobertura de líneas dice qué código ejecutan las pruebas, no si lo comprueban. Las pruebas de
mutación (PIT) cambian el código —un `<` por `<=`, un `+` por `-`, una llamada de validación que
desaparece— y miden cuántos de esos cambios hacen fallar alguna prueba. Un mutante que sobrevive es
una regla que nadie está vigilando.

## Cómo se ejecuta

PIT vive en el perfil `mutacion` del pom raíz, fuera del build normal: tarda y no es un requisito
para fusionar.

```bash
mvn -Pmutacion -pl services/ayni-core-banking-service -am test-compile \
    org.pitest:pitest-maven:mutationCoverage -Djacoco.skip=true
```

El informe queda en `services/ayni-core-banking-service/target/pit-reports/index.html` y los datos
en `mutations.xml`. El alcance inicial es el dominio del core (`pe.ayni.bank.core.domain.*`, con sus
pruebas de ese mismo paquete: unitarias, sin Spring ni base de datos), que es donde está el dinero.
Para otro alcance basta con `-Dpit.clases=… -Dpit.pruebas=…`.

## Resultado

| | Mutantes | Muertos | Vivos | Sin cobertura | Muertos |
|---|---:|---:|---:|---:|---:|
| Primera ejecución | 294 | 249 | 30 | 15 | **84.7 %** |
| Tras las pruebas nuevas | 294 | 286 | 7 | 1 | **97.3 %** |

La meta propuesta en el plan era el 80 %; ya se cumplía antes de tocar nada, así que el trabajo fue
matar los vivos del dominio monetario y no llegar a un número. Las clases del dinero quedan al
100 %: `Dinero` (17 de 17), `Conversion` (15 de 15), `TipoDeCambioAplicado` (4 de 4),
`PoliticaDeSpread` (9 de 9), `Capitalizacion` y `Devengo`; `Movimiento` mata 33 de 34.

## Qué se encontró y cómo se mató

Pruebas en `FronterasDelDominioTest` y `CvvDeReferenciaTest`:

- **`Dinero.menos` sin validar la moneda.** Quitar `exigirLaMismaMoneda` no rompía nada: restar
  soles y dólares devolvía una cifra sin sentido. Ahora lo comprueba una prueba.
- **`Conversion.de` sin exigir cuentas operativas.** Quitar `exigirOperativa` de origen o de destino
  permitía convertir con una cuenta bloqueada. Ahora hay una prueba para cada lado.
- **Fronteras de `Asiento`, `SegmentoCambiario`, `TipoDeCambioOficial`, `FiltroDeMovimientos` y
  `FiltroDeAuditoria`:** el valor justo en el borde (140 caracteres, margen cero, compra igual a
  venta, mismo día o mismo importe, tamaño de página 1 y máximo) y el siguiente.
- **`CalculadoraDeCvv`:** ocho mutantes sobre el truncado a 31 bits sobrevivían porque las pruebas
  solo miraban la forma (tres dígitos, estable en la ventana). `CvvDeReferenciaTest` fija diez
  valores calculados aparte con HMAC-SHA256 en Python, así que el CVV tiene que coincidir con el de
  cualquier otra implementación.
- **Lo que no tenía cobertura:** getters, `toString`, `Cuenta.hashCode`, `Tarjeta.congelar` y
  `descongelar`, la excepción `TipoDeCambioCambioException`. Entre ellos, `Cuenta.toString` está
  documentado como «sin número ni CCI»: ahora una prueba lo exige.

## Mutantes que se aceptan

Quedan ocho. Ninguno cambia el comportamiento observable:

| Clase · línea | Mutación | Por qué se acepta |
|---|---|---|
| `CalculoDeInteres.raiz` · 50 | `i < VUELTAS_MAXIMAS` → `<=` | El bucle de Newton converge mucho antes de la vuelta 100: cambiar el tope por una vuelta más no se puede observar. Es una guarda contra un bucle infinito. |
| `CalculoDeInteres.raiz` · 54 | `<= TOLERANCIA` → `<` | Hace falta que la diferencia sea exactamente `1E-32`. Con aritmética de 34 dígitos no ocurre en las entradas reales. |
| `CalculoDeInteres.raiz` · 59 | `return x` → `null` | Es la salida de la guarda anterior, que no se alcanza (sin cobertura). |
| `Cci.rellenar` · 128 | `> LONGITUD_DE_CUENTA` → `>=` | El número de cuenta sin la oficina tiene siempre 11 dígitos, nunca 12: el borde es inalcanzable. |
| `Movimiento.recortar` · 146 | `<= 140` → `< 140` | Con 140 caracteres exactos, recortar a 140 da el mismo texto. Mutante equivalente. |
| `Pan.superaLuhn` · 37 | `> 9` → `>= 9` | Un dígito duplicado es siempre par, así que nunca vale 9: equivalente. |
| `Pan.superaLuhn` · 41 | `suma +=` → `suma -=` | `suma % 10 == 0` y `-suma % 10 == 0` coinciden: equivalente. |
| `TipoDeCambioOficial` · 19 | `venta.signum() <= 0` → `< 0` | Una venta igual a cero con compra positiva la rechaza igualmente la comprobación siguiente (venta menor que compra): equivalente. |

## Cómo seguir

- Antes de ampliar el alcance a `application` o a otros servicios, ejecutar PIT con ese alcance y
  repetir este ejercicio: leer los vivos, matar los que importen y anotar los que se aceptan.
- Si una ejecución nueva deja más vivos de los de la tabla, son código nuevo sin vigilar o una
  prueba que dejó de comprobar algo.
- `pit.umbral` (por defecto 0) permite hacer fallar la ejecución por debajo de un porcentaje de
  mutantes muertos; no se activa en el CI hasta decidir dónde corre y cuánto tarda.
