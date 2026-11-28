# Lighthouse en las páginas públicas — HU-17 · AYNI-293

**Objetivo:** Lighthouse igual o superior a 90 en rendimiento, accesibilidad y buenas prácticas.
**Resultado (27-nov-2026): se cumple en todas las páginas públicas.**

## Cómo se midió

- Lighthouse 12.8 con su perfil por defecto (móvil, red y CPU limitadas), contra la build de producción
  (`npm run build` y `next start`), no contra el servidor de desarrollo.
- Tres pasadas por página, tras una pasada de calentamiento. La primera pasada en frío penaliza el
  rendimiento (en una medición sin calentar, la portada bajó a 86), así que se descarta.
- Las páginas de la banca exigen sesión: su accesibilidad la comprueban axe-core y las pruebas de teclado
  (`e2e/*.a11y.spec.ts`) en cada PR.

## Resultados (rendimiento / accesibilidad / buenas prácticas)

| Página | Pasada 1 | Pasada 2 | Pasada 3 |
|---|---|---|---|
| `/` | 97 / 100 / 100 | 96 / 100 / 100 | 96 / 100 / 100 |
| `/ingresar` | 97 / 100 / 100 | 97 / 100 / 100 | 97 / 100 / 100 |
| `/registro` | 98 / 100 / 100 | 98 / 100 / 100 | 98 / 100 / 100 |
| `/ayuda` | 99 / 100 / 100 | 99 / 100 / 100 | 98 / 100 / 100 |
| `/productos` | 98 / 100 / 100 | 98 / 100 / 100 | 98 / 100 / 100 |
| `/tarifario` | 99 / 100 / 100 | 99 / 100 / 100 | 99 / 100 / 100 |
| `/libro-de-reclamaciones` | 98 / 100 / 100 | 98 / 100 / 100 | 98 / 100 / 100 |

## Lo que se corrigió

- **Iconos deformados.** Tres iconos de la portada y de productos se mostraban en cajas cuadradas sin serlo
  (904 × 706 px, por ejemplo) y se veían aplastados. Lighthouse lo marcaba en buenas prácticas (96 en
  `/ayuda` y `/productos`). Con `object-contain` conservan su proporción dentro de la caja: buenas prácticas
  pasa a 100.

## Cómo repetirlo

```powershell
# Chrome sin interfaz con un puerto de depuración (en Windows, chrome-launcher no puede borrar su perfil
# temporal y aborta: por eso se lanza aparte).
& "C:\Program Files\Google\Chrome\Application\chrome.exe" --headless=new --remote-debugging-port=9222 `
    --user-data-dir="$env:TEMP\lh-perfil" about:blank
npx lighthouse@12 http://127.0.0.1:3000/ --port=9222 `
    "--only-categories=accessibility,best-practices,performance" --output=html --output-path=./lighthouse.html
```

En PowerShell, `--only-categories` va entre comillas: si no, las comas se convierten en una lista y
Lighthouse no mide ninguna categoría.
