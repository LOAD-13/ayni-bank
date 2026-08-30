import { defineConfig, devices } from "@playwright/test";

/**
 * Pruebas de extremo a extremo sobre la aplicación construida.
 *
 * Van aparte de Vitest a propósito: Vitest corre sobre jsdom, que **no calcula
 * disposición** —no hay anchos reales, ni desbordamiento, ni `zoom`—, de modo que no
 * puede detectar ni una franja vacía ni una barra de desplazamiento horizontal. Eso solo
 * lo ve un navegador de verdad.
 */
/**
 * Puerto del servidor de pruebas.
 *
 * 4173 y no algo del bloque 3000: en Windows, Hyper-V reserva rangos de puertos dinámicos
 * que suelen tragarse el 3000 entero y que **cambian en cada reinicio**. El síntoma es un
 * `EACCES` al arrancar el servidor, que no dice nada sobre la causa real. Consúltalos con
 * `netsh interface ipv4 show excludedportrange protocol=tcp` y anula con `PUERTO_E2E`.
 */
const PUERTO = process.env.PUERTO_E2E ?? "4173";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? "github" : "list",

  use: {
    baseURL: `http://127.0.0.1:${PUERTO}`,
    trace: "on-first-retry",
  },

  projects: [
    { name: "escritorio", use: { ...devices["Desktop Chrome"] } },
    { name: "movil", use: { ...devices["Pixel 7"] } },
  ],

  // Levanta la aplicación real construida, no el servidor de desarrollo: es la que se
  // despliega, y `next dev` difiere en optimización de imágenes y en hidratación.
  webServer: {
    command: `npm run build && npx next start --port ${PUERTO} --hostname 127.0.0.1`,
    url: `http://127.0.0.1:${PUERTO}`,
    reuseExistingServer: !process.env.CI,
    timeout: 180_000,
  },
});
