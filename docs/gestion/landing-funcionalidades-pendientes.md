# Landing pública — qué promete y qué existe

La landing es la puerta de entrada del producto: **cada enlace suyo es una promesa**. Este documento
registra qué destinos anuncia el diseño aprobado en el prototipo, cuáles tienen ya una Historia de
Usuario detrás y cuáles no, para poder ir sacando sus pantallas en orden y sin sorpresas.

Se mantiene actualizado a medida que se implementan las historias. **Un enlace no se activa en la
landing hasta que su pantalla existe**: un enlace que no lleva a ninguna parte enseña al visitante
que la web está rota.

---

## 1. Destinos que ya tienen historia

| Destino en la landing | Historia | Sprint | Estado |
|---|---|---|---|
| «Abrir cuenta» → registro | `AYNI-12` · HU-01 | 1 | En curso |
| «Ingresar» → inicio de sesión | `AYNI-15` · HU-04 | 1 | Por hacer |
| Cuenta de ahorro en soles | `AYNI-16` · HU-05 | 1 | Por hacer |
| Rendimiento diario y TREA | `AYNI-17` · HU-06 | 3 | Por hacer |
| Tarjeta de débito virtual | `AYNI-20` · HU-09 | 5 | Por hacer |
| Congelar tarjeta y límites | `AYNI-21` · HU-10 | 5 | Por hacer |
| Cuenta en dólares y tipo de cambio | `AYNI-23` · HU-12 | 6 | Por hacer |
| Transferencias entre cuentas Ayni | `AYNI-18` · HU-07 | 3 | Por hacer |
| Transferencia interbancaria por CCI | `AYNI-22` · HU-11 | 6 | Por hacer |
| Centro de ayuda | `AYNI-28` · HU-17 | 7 | Por hacer |

## 2. Destinos sin historia, dentro del alcance

Son pantallas de contenido estático. No mueven dinero ni tocan la base de datos, pero **existen en
la landing y hoy no llevan a ninguna parte**. Se sacan cuando toque, sin prisa.

| Destino | Qué debe contener | Prioridad |
|---|---|---|
| Tarifario | Comisiones, TREA vigente y spread cambiario. **Lo exige la transparencia SBS** y es el único de esta tabla que no es opcional | Alta |
| Libro de reclamaciones | Formulario y plazo de respuesta. Obligatorio para toda empresa que atiende consumidores en Perú | Alta |
| Términos y condiciones | Se enlaza desde la casilla de consentimiento del registro | Alta |
| Política de datos personales | Ley N.º 29733: finalidad, retención, derechos ARCO y procedimiento de borrado | Alta |
| Canales de atención | Horarios y medios de contacto | Media |
| Quiénes somos | Institucional | Baja |
| Trabaja con nosotros | Institucional | Baja |
| Sala de prensa · Blog | Institucional | Baja |

**Las cuatro de prioridad alta condicionan historias del Sprint 1.** El registro exige aceptar
términos y política de datos: hoy la casilla existe pero no hay documento al que enlazar.

## 3. Destinos fuera del alcance declarado

El Acta de Constitución (§2.2 del documento de diseño) deja **explícitamente fuera** estos
productos. El prototipo los dibujó porque hacen una landing bancaria más creíble, pero anunciarlos
contradice el propio Acta.

| Anunciado en la landing | Por qué está fuera |
|---|---|
| Préstamo personal · Préstamo pyme | Créditos, fuera del alcance |
| Tarjeta de crédito | Fuera del alcance |
| Inversiones · Depósito a plazo | Fuera del alcance |
| Seguros simples | Nunca estuvo en el alcance |
| Cuenta negocio | El producto es para **personas naturales** |
| Cobros con QR · Facturación | Producto de negocios |
| Envío de tarjeta física | Emisión de tarjetas físicas, fuera del alcance |
| Sección de app móvil con descargas | Aplicativo móvil, fuera del alcance |
| Retiros en cajeros aliados | No hay integración con red de cajeros |

**Tratamiento acordado:** estos elementos se conservan visualmente donde sostienen la composición,
pero **sin destino activo**, y las secciones de producto se pueblan con lo que Ayni sí ofrece:
cuenta en soles, cuenta en dólares, tarjeta de débito virtual y tipo de cambio con spread
transparente.

Si en algún momento se decide ampliar el alcance, la vía es un ADR nuevo y una revisión del Acta,
no un enlace añadido en silencio a la landing.

## 4. Contenido que exige revisión antes de publicarse

| Elemento del prototipo | Problema | Resolución |
|---|---|---|
| «Supervisado por la SBS» · «Miembro del FSD» | Ayni no está supervisado ni cubierto por el Fondo de Seguro de Depósitos. Es una afirmación regulatoria falsa | Se sustituye por «Diseñado conforme a la normativa SBS», que sí es cierto según §5.5 |
| «ISO 27001» a secas | Sugiere una certificación que nadie ha emitido | «Controles ISO/IEC 27001», que es lo que el §5.5 declara: se adopta el Anexo A como marco |
| TREA **4.60 %** en la franja de cifras | La TREA publicada del producto es **4.50 %** | Se corrige. La cifra sale del catálogo de `tasa_producto`, no de un literal en la interfaz |
| Wong · Yape · Luz del Sur · Visa | Marcas de terceros en la lista de movimientos y la tarjeta | Se sustituyen por los comercios ficticios del catálogo de datos de producto |
| App Store · Google Play | Además de ser marcas ajenas, anuncian una app que no existe | Se retiran con la sección de app móvil |

## 5. Regla de mantenimiento

Cuando se implemente una historia que cubra un destino de la sección 1, **se activa su enlace en la
landing en el mismo Pull Request** y se actualiza este documento. Un destino que pasa a existir y
sigue apuntando al vacío es una regresión, no una tarea pendiente.
