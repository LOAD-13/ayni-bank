// Prueba de estrés hasta el punto de quiebre · HU-14, escenario 2 (AYNI-300).
//
// Sube la carga por escalones y mide cada uno por separado. El punto de quiebre es el primer
// escalón cuya latencia P95 supera 1 s, que es lo que define HU-14, o en el que fallan más
// del 1 % de las peticiones. Lo segundo hace falta: un servidor saturado suele empezar
// rechazando conexiones, y un rechazo es instantáneo, así que el P95 de todas las peticiones
// BAJA justo cuando el sistema se rompe. Por eso la latencia se mide solo sobre respuestas
// correctas y los errores se miran aparte. No se detiene al encontrar el quiebre: los
// escalones siguientes muestran cómo degrada el sistema (AYNI-301).
//
//   k6 run -e BASE_URL=http://localhost:8080 -e TOKEN=<token de acceso> infra/carga/estres.js
//
// Con Docker, sin instalar k6:
//
//   docker run --rm -i -v "$PWD:/w" -w /w grafana/k6:1.4.2 run \
//     -e BASE_URL=http://host.docker.internal:8080 -e TOKEN=... infra/carga/estres.js
//
// Escalones configurables: -e NIVELES=50,100,200,300,400,500 -e DURACION=2m
//
// Igual que humo.js, solo lee y NUNCA se lanza contra producción: llevar la única instancia
// de producción a su punto de quiebre es provocar la caída que HU-16 intenta evitar. Se
// ejecuta contra un entorno con la misma imagen y el mismo tamaño de instancia.
//
// Al terminar escribe dos ficheros junto al resumen de consola:
//   estres-resultado.json  todas las métricas, para el informe y para Grafana
//   estres-resultado.md    la tabla por escalón con el punto de quiebre marcado
import http from "k6/http";
import { check, sleep } from "k6";

const BASE = __ENV.BASE_URL || "http://localhost:8080";
const NIVELES = (__ENV.NIVELES || "50,100,200,300,400,500").split(",").map(Number);
const DURACION = __ENV.DURACION || "2m";
const UMBRAL_QUIEBRE_MS = 1000; // HU-14: «cuando la latencia p95 supera 1 s»
const OBJETIVO_P95_MS = 500; // HU-14: objetivo con carga nominal
const UMBRAL_QUIEBRE_ERRORES = 0.01; // más del 1 % de fallos también es quiebre

// Las mismas consultas que la banca hace al abrir la sesión (las de humo.js).
const CONSULTAS = {
  cuenta: "/api/v1/cuentas/mia",
  movimientos: "/api/v1/cuentas/mia/movimientos",
  rendimiento: "/api/v1/cuentas/mia/rendimiento",
  tarjeta: "/api/v1/tarjetas/mia",
};

function segundos(texto) {
  const unidades = { s: 1, m: 60, h: 3600 };
  return Number(texto.slice(0, -1)) * unidades[texto.slice(-1)];
}

// Un escenario por escalón, uno detrás de otro. Cada petición lleva la etiqueta de su
// escalón, y un umbral por escalón hace que el resumen traiga su P95 aparte.
const escenarios = {};
const umbrales = {};
NIVELES.forEach((usuarios, i) => {
  escenarios[`nivel_${usuarios}`] = {
    executor: "constant-vus",
    vus: usuarios,
    duration: DURACION,
    startTime: `${i * segundos(DURACION)}s`,
    gracefulStop: "10s",
    tags: { nivel: String(usuarios) },
  };
  // Umbrales informativos: no abortan la prueba. Pasado el quiebre se sigue midiendo.
  umbrales[`http_req_duration{nivel:${usuarios},expected_response:true}`] = [
    `p(95)<${UMBRAL_QUIEBRE_MS}`,
  ];
  umbrales[`http_req_failed{nivel:${usuarios}}`] = [`rate<${UMBRAL_QUIEBRE_ERRORES}`];
});

export const options = {
  scenarios: escenarios,
  thresholds: umbrales,
  summaryTrendStats: ["avg", "med", "p(95)", "p(99)", "max"],
};

export default function () {
  const cabeceras = { headers: { Authorization: `Bearer ${__ENV.TOKEN}` } };
  for (const [consulta, ruta] of Object.entries(CONSULTAS)) {
    const r = http.get(BASE + ruta, { ...cabeceras, tags: { consulta } });
    check(r, { [`${consulta} 200`]: (x) => x.status === 200 });
  }
  sleep(1);
}

function fila(nivel, metricas) {
  const duracion = metricas[`http_req_duration{nivel:${nivel},expected_response:true}`];
  const fallos = metricas[`http_req_failed{nivel:${nivel}}`];
  if (!duracion && !fallos) return null;
  // Sin ninguna respuesta correcta no hay latencia que medir: el escalón cae entero.
  const v = duracion ? duracion.values : { "p(95)": NaN, "p(99)": NaN, max: NaN };
  return {
    nivel,
    p95: v["p(95)"],
    p99: v["p(99)"],
    max: v.max,
    errores: fallos ? fallos.values.rate : 0,
  };
}

export function handleSummary(datos) {
  const filas = NIVELES.map((n) => fila(n, datos.metrics)).filter(Boolean);
  const roto = (f) =>
    !(f.p95 <= UMBRAL_QUIEBRE_MS) || f.errores > UMBRAL_QUIEBRE_ERRORES; // NaN cuenta como roto
  const quiebre = filas.find(roto);
  const ms = (x) => (Number.isNaN(x) ? "—" : `${Math.round(x)} ms`);
  const pct = (x) => `${(x * 100).toFixed(2)} %`;

  const md = [
    `# Prueba de estrés · ${new Date().toISOString()}`,
    "",
    `Objetivo: ${BASE} · escalones de ${DURACION} · HU-14, escenario 2 (AYNI-300).`,
    "",
    "Latencias solo de respuestas correctas; los fallos se cuentan en «Errores».",
    "",
    "| Usuarios concurrentes | P95 | P99 | Máximo | Errores | Estado |",
    "|---|---|---|---|---|---|",
    ...filas.map((f) => {
      let estado = "dentro del objetivo";
      if (roto(f)) estado = f === quiebre ? "**PUNTO DE QUIEBRE**" : "degradado";
      else if (f.p95 > OBJETIVO_P95_MS) estado = "sobre 500 ms";
      return `| ${f.nivel} | ${ms(f.p95)} | ${ms(f.p99)} | ${ms(f.max)} | ${pct(f.errores)} | ${estado} |`;
    }),
    "",
    quiebre
      ? `Punto de quiebre: **${quiebre.nivel} usuarios concurrentes** (P95 ${ms(quiebre.p95)}, errores ${pct(quiebre.errores)}).`
      : `Ningún escalón superó 1 s de P95 ni el 1 % de errores: el quiebre está por encima de ${NIVELES[NIVELES.length - 1]} usuarios. Añadir escalones con -e NIVELES.`,
    "",
  ].join("\n");

  return {
    stdout: md,
    "estres-resultado.json": JSON.stringify(datos, null, 2),
    "estres-resultado.md": md,
  };
}
