import http from 'k6/http';
import { check, sleep } from 'k6';

const TOKENS_PATH = __ENV.K6_TOKENS_FILE || './tests/performance/k6/tokens.local.json';
const tokenFile = JSON.parse(open(TOKENS_PATH));
const TOKENS = Array.isArray(tokenFile) ? tokenFile : tokenFile.tokens;

export const options = {
  scenarios: {
    carga_nominal: {
      executor: 'constant-vus',
      vus: 200,
      duration: '10m',
      gracefulStop: '30s',
    },
  },
  discardResponseBodies: true,
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  thresholds: {
    'http_req_duration{name:consulta_saldo}': ['p(95)<500', 'p(99)<1000'],
    'http_req_duration{name:consulta_movimientos}': ['p(95)<500', 'p(99)<1000'],
    http_req_failed: ['rate<0.001'],
    checks: ['rate>0.999'],
  },
};

export function setup() {
  if (!Array.isArray(TOKENS) || TOKENS.length < 200) {
    throw new Error(`HU-14 nominal requiere 200 JWT distintos. Encontrados: ${TOKENS?.length ?? 0}. Archivo: ${TOKENS_PATH}`);
  }
  if (TOKENS.slice(0, 200).some((token) => typeof token !== 'string' || token.trim().length < 20)) {
    throw new Error('El archivo de tokens contiene valores vacios o invalidos. Usa JWT de usuarios de prueba.');
  }
  return { tokens: TOKENS.slice(0, 200) };
}

export default function (data) {
  // Un JWT distinto por VU evita que el limitador del gateway agrupe a los 200
  // usuarios bajo una misma identidad. El intervalo mantiene el perfil de 50
  // solicitudes/minuto documentado para el trafico normal (200 usuarios).
  const token = data.tokens[__VU - 1];
  const headers = {
    Authorization: `Bearer ${token}`,
    Accept: 'application/json',
  };

  // Desfase inicial para no provocar un pico artificial de 200 peticiones al segundo 0.
  if (__ITER === 0) sleep(Math.random() * 240);

  if (__ITER % 2 === 0) {
    const response = http.get(`${__ENV.BASE_URL || 'http://localhost:8080'}/api/v1/cuentas/mia`, {
      headers,
      tags: { name: 'consulta_saldo' },
    });
    check(response, { 'consulta de saldo responde HTTP 200': (r) => r.status === 200 });
  } else {
    const response = http.get(`${__ENV.BASE_URL || 'http://localhost:8080'}/api/v1/cuentas/mia/movimientos?limite=20`, {
      headers,
      tags: { name: 'consulta_movimientos' },
    });
    check(response, { 'consulta de movimientos responde HTTP 200': (r) => r.status === 200 });
  }

  sleep(240);
}
