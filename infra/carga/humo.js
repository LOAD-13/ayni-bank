// Prueba de humo de carga (semana 14 · ISO 25010, eficiencia de desempeño). AYNI-319.
//
// Cinco usuarios virtuales durante dos minutos sobre las consultas que la banca hace al
// abrir la sesión, cada una con su etiqueta para leer su P95 y P99 por separado.
//
//   k6 run -e BASE_URL=http://localhost:8080 -e TOKEN=<token de acceso> infra/carga/humo.js
//
// El token es de un cliente del entorno local: la prueba solo lee y nunca se lanza contra
// producción. Umbrales del criterio de aceptación de HU-14: P95 < 300 ms y errores < 1 %.
import http from "k6/http";
import { check, sleep } from "k6";

const BASE = __ENV.BASE_URL || "http://localhost:8080";
const CONSULTAS = {
  cuenta: "/api/v1/cuentas/mia",
  movimientos: "/api/v1/cuentas/mia/movimientos",
  // la pantalla de movimientos con filtros y paginación (HU-08, T-36)
  historial: "/api/v1/cuentas/mia/movimientos?pagina=0&limite=20",
  rendimiento: "/api/v1/cuentas/mia/rendimiento",
  tarjeta: "/api/v1/tarjetas/mia",
  beneficiarios: "/api/v1/interbancarias/beneficiarios",
};

export const options = {
  stages: [
    { duration: "30s", target: 5 },
    { duration: "1m", target: 5 },
    { duration: "30s", target: 0 },
  ],
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<300"],
    // una entrada por consulta para que el resumen muestre su latencia aparte
    ...Object.fromEntries(Object.keys(CONSULTAS).map((c) => [`http_req_duration{consulta:${c}}`, ["p(95)<300"]])),
  },
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
