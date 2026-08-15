# Ayni Bank — Sistema de diseño

Fuente de verdad de la identidad visual. Los valores se derivaron por muestreo directo de
`assets/logo.png` y `assets/icon.png`, no por estimación visual.

---

## 1. Colores de marca

| Token | Hex | RGB | HSL | Uso |
|---|---|---|---|---|
| `--ayni-azul` | `#064475` | 6, 68, 117 | 206°, 90%, 24% | Color primario. Texto, encabezados, botones principales, barra de navegación. |
| `--ayni-dorado` | `#C59E41` | 197, 158, 65 | 42°, 53%, 51% | Acento. Bordes, iconografía grande, resaltados, gráficos. |

### Regla de accesibilidad — obligatoria

Comprometimos **WCAG 2.1 nivel AA** en el SLA del proyecto. Los ratios de contraste medidos son:

| Combinación | Ratio | Veredicto |
|---|---|---|
| `#064475` sobre blanco | **10.04 : 1** | ✅ AAA — válido para cualquier tamaño de texto |
| `#C59E41` sobre blanco | **2.52 : 1** | ❌ No cumple ni AA — **prohibido para texto** |
| `#C59E41` sobre `#064475` | **3.99 : 1** | ⚠️ Solo texto grande (≥ 24 px) y componentes de interfaz |
| Blanco sobre `#064475` | **10.04 : 1** | ✅ AAA |
| `#8A6D24` (dorado 700) sobre blanco | **4.89 : 1** | ✅ AA para texto normal |

**El dorado no se usa nunca para texto sobre fondo claro.** Cuando se necesite texto en tono
dorado, se emplea `--ayni-dorado-700` (`#8A6D24`). Esta regla la verifica axe-core en CI.

---

## 2. Escalas

### Azul (primario)

| Token | Hex | Uso |
|---|---|---|
| `--azul-900` | `#02223B` | Fondos oscuros, modo oscuro |
| `--azul-800` | `#043457` | Hover sobre primario |
| `--azul-700` | `#064475` | **Base de marca** |
| `--azul-600` | `#0A5A96` | Enlaces, estados activos |
| `--azul-500` | `#1276BE` | Elementos interactivos secundarios |
| `--azul-400` | `#4C9BD6` | Iconografía sobre fondo claro |
| `--azul-300` | `#8CBEE6` | Bordes destacados |
| `--azul-200` | `#C0DCF2` | Fondos de selección |
| `--azul-100` | `#E4EFF9` | Fondos sutiles |
| `--azul-050` | `#F3F8FC` | Fondo de sección |

### Dorado (acento)

| Token | Hex | Uso |
|---|---|---|
| `--dorado-800` | `#6B541B` | Texto de acento sobre fondo claro |
| `--dorado-700` | `#8A6D24` | Texto de acento (cumple AA) |
| `--dorado-600` | `#A8862F` | Bordes de énfasis |
| `--dorado-500` | `#C59E41` | **Base de marca** |
| `--dorado-400` | `#D6B76D` | Iconografía decorativa |
| `--dorado-300` | `#E5CF9C` | Fondos de resalte |
| `--dorado-200` | `#F1E4C6` | Fondos sutiles |
| `--dorado-100` | `#FAF4E7` | Fondo de aviso suave |

### Neutros

| Token | Hex |
|---|---|
| `--gris-900` | `#111820` |
| `--gris-700` | `#374151` |
| `--gris-500` | `#6B7280` |
| `--gris-300` | `#D1D5DB` |
| `--gris-100` | `#F3F4F6` |
| `--blanco` | `#FFFFFF` |

### Semánticos

| Token | Hex | Contraste sobre blanco |
|---|---|---|
| `--exito` | `#0F7A52` | 5.35 : 1 ✅ |
| `--error` | `#B3261E` | 6.54 : 1 ✅ |
| `--aviso` | `#8A6D24` | 4.89 : 1 ✅ |
| `--info` | `#0A5A96` | 7.20 : 1 ✅ |

> Nota: en una interfaz bancaria el verde y el rojo señalan abono y cargo. **Nunca se usa
> únicamente el color** para transmitir esa información: siempre acompañado de signo (`+` / `−`)
> y de etiqueta textual, porque el color solo no es accesible para daltonismo.

---

## 3. Tipografía

| Rol | Familia | Peso |
|---|---|---|
| Titulares | Inter / Montserrat | 700 |
| Cuerpo | Inter | 400 · 500 |
| Cifras monetarias | Inter con `font-variant-numeric: tabular-nums` | 600 |

**Los importes se muestran siempre con cifras tabulares**, para que las columnas de números
queden alineadas verticalmente en los estados de cuenta y los listados de movimientos.

| Token | Tamaño | Interlineado |
|---|---|---|
| `--txt-display` | 40 px | 1.15 |
| `--txt-h1` | 32 px | 1.2 |
| `--txt-h2` | 24 px | 1.3 |
| `--txt-h3` | 20 px | 1.4 |
| `--txt-body` | 16 px | 1.6 |
| `--txt-small` | 14 px | 1.5 |
| `--txt-caption` | 12 px | 1.4 |

---

## 4. Espaciado, radios y sombras

Escala base de 4 px: `4 · 8 · 12 · 16 · 24 · 32 · 48 · 64`.

| Token | Valor |
|---|---|
| `--radio-sm` | 6 px |
| `--radio-md` | 12 px |
| `--radio-lg` | 20 px |
| `--radio-full` | 9999 px |
| `--sombra-sm` | `0 1px 2px rgba(6,68,117,.08)` |
| `--sombra-md` | `0 4px 12px rgba(6,68,117,.10)` |
| `--sombra-lg` | `0 12px 32px rgba(6,68,117,.14)` |

---

## 5. Activos de marca

| Archivo | Uso |
|---|---|
| `assets/logo.png` | Logotipo completo con bajada «BANCO PERUANO». Cabecera del sitio, documentos. |
| `assets/icon.png` | Isotipo (monograma «A» con flecha). Favicon, avatar, tarjeta virtual. |
| `assets/carrusel_1.jpg` … `carrusel_3.jpg` | Imágenes del carrusel de la landing pública. |

**Área de protección:** alrededor del logotipo se reserva un margen libre equivalente a la altura
de la letra «A» del monograma. **Tamaño mínimo:** 120 px de ancho para el logotipo completo,
32 px para el isotipo.

**Usos prohibidos:** deformar las proporciones, recolorear fuera de la paleta, aplicar sombras o
contornos, colocar el logotipo sobre fondos que reduzcan el contraste por debajo de 3:1, o
reconstruir el monograma con tipografías distintas.

---

## 6. Aplicación

Estos tokens se declaran una sola vez en `web/ayni-web/src/styles/tokens.css` como variables CSS
y se consumen desde la configuración de Tailwind. **Ningún componente define colores literales.**
Si un valor hexadecimal aparece escrito directamente en un componente, la revisión de código lo
rechaza.

Los documentos de gestión (Acta, matrices, cronograma) emplean la misma paleta, de modo que la
identidad es coherente entre el producto y su documentación.
