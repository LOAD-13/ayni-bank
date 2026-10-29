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

/**
 * Validación en la nube (APF3, T-26): con `BASE_URL_NUBE` solo corre `e2e/nube` contra la
 * versión desplegada, sin levantar ningún servidor local, y graba la ejecución en video.
 * Las credenciales de la cuenta de prueba llegan por variables de entorno (ver e2e/nube).
 */
const NUBE = process.env.BASE_URL_NUBE;

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  // En CI, ademas de las anotaciones del PR, un informe HTML navegable que se publica
  // como artefacto: es la evidencia de las pruebas de interfaz.
  reporter: process.env.CI
    ? [["github"], ["html", { open: "never", outputFolder: "informe-playwright" }]]
    : [["list"], ["html", { open: "never", outputFolder: "informe-playwright" }]],

  use: {
    baseURL: `http://127.0.0.1:${PUERTO}`,
    trace: "on-first-retry",
    // La bienvenida y el logro salen una sola vez y taparían el resto de pruebas: se dan
    // por vistos. Las pruebas que los miden vacían este estado con `test.use`.
    storageState: {
      cookies: [],
      origins: [
        {
          origin: `http://127.0.0.1:${PUERTO}`,
          localStorage: [
            { name: "ayni.bienvenida-vista", value: "1" },
            { name: "ayni.primera-operacion", value: "1" },
          ],
        },
      ],
    },
  },

  projects: NUBE
    ? [
        {
          name: "nube",
          testDir: "./e2e/nube",
          use: {
            ...devices["Desktop Chrome"],
            baseURL: NUBE,
            storageState: undefined,
            video: "on",
            trace: "on",
          },
        },
      ]
    : [
        { name: "escritorio", testIgnore: /nube\//, use: { ...devices["Desktop Chrome"] } },
        { name: "movil", testIgnore: /nube\//, use: { ...devices["Pixel 7"] } },
      ],

  // Levanta la aplicación real construida, no el servidor de desarrollo: es la que se
  // despliega, y `next dev` difiere en optimización de imágenes y en hidratación.
  webServer: NUBE
    ? undefined
    : {
        command: `npm run build && npx next start --port ${PUERTO} --hostname 127.0.0.1`,
        url: `http://127.0.0.1:${PUERTO}`,
        reuseExistingServer: !process.env.CI,
        timeout: 180_000,
      },
});
