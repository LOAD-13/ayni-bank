# ayni-web

Landing pública y banca en línea de Ayni Bank. Next.js 15 con App Router, React 19, TypeScript y
Tailwind 4.

## Arrancar

```bash
npm ci
npm run dev
```

Queda en http://localhost:3000. El navegador habla siempre con el **gateway**, nunca con un servicio
directamente: la URL se toma de `NEXT_PUBLIC_API_URL`, que por defecto apunta a
`http://localhost:8080`.

Para levantar la aplicación junto al resto del sistema, usa el `docker compose` de la raíz; las
instrucciones están en el [README del repositorio](../../README.md).

## Verificar antes de subir

Son los seis pasos que ejecuta el job `Web · calidad y accesibilidad`, en el mismo orden.

```bash
npm run lint && npm run format:check && npm run typecheck
npm run test -- --coverage
npm run test:a11y
npm run build
```

## Colores

**Ningún componente escribe un hexadecimal.** La paleta vive en
[`src/styles/tokens.css`](src/styles/tokens.css), que es la traducción literal de
[`docs/marca/design-tokens.md`](../../docs/marca/design-tokens.md). Los tokens se consumen como
utilidades de Tailwind: `bg-azul-700`, `text-gris-700`, `rounded-md`.

Dos reglas que la revisión de código no negocia:

- **El dorado nunca es color de texto sobre fondo claro.** `--dorado-500` da 2.52:1 y no cumple ni
  AA. Para texto de acento va `dorado-700`, que da 4.89:1.
- **El color nunca transmite información por sí solo.** Un abono y un cargo se distinguen por el
  signo y la etiqueta, no solo por el verde y el rojo.

## Accesibilidad

Cada pantalla trae un fichero `*.a11y.test.tsx` que la pasa por axe-core con las reglas de WCAG 2.1
AA. Es lo que ejecuta `npm run test:a11y` y lo que hace que el compromiso de accesibilidad sea una
comprobación y no una intención.

Las pruebas corren sobre jsdom, que no calcula estilos aplicados: **las reglas de contraste quedan
inactivas ahí** y se verifican aparte, sobre navegador, en HU-17.
