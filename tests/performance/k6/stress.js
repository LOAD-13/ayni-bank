import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import exec from 'k6/execution';

const TOKENS_PATH = __ENV.K6_TOKENS_FILE || './tests/performance/k6/tokens.local.json';
const tokenFile = JSON.parse(open(TOKENS_PATH));
const TOKENS = Array.isArray(tokenFile) ? tokenFile : tokenFile.tokens;

const solicitudesLentas = new Counter('hu14_solicitudes_mayores_1s');
const usuariosEnSolicitudLenta = new Trend('hu14_vus_en_solicitud_lenta');
let avisoLentoEmitido = false;
let avisoErrorEmitido = false;

export const options = {
  scenarios: {
    estres_progresivo: {
      executor: 'ramping-vus',
      startVUs: 200,
      stages: [
        { duration: '2m', target: 200 },
        { duration: '30s', target: 300 },
        { duration: '90s', target: 300 },
        { duration: '30s', target: 400 },
        { duration: '90s', target: 400 },
        { duration: '30s', target: 500 },
        { duration: '90s', target: 500 },
        { duration: '30s', target: 600 },
        { duration: '90s', target: 600 },
      ],
      gracefulRampDown: '30s',
      gracefulStop: '30s',
      startTime: '0s',
    },
  },
  discardResponseBodies: true,
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  thresholds: {
    'http_req_duration{name:consulta_saldo,load_level:200-249}': [{ threshold: 'p(95)<1000', abortOnFail: true, delayAbortEval: '30s' }],
    'http_req_duration{name:consulta_saldo,load_level:250-349}': [{ threshold: 'p(95)<1000', abortOnFail: true, delayAbortEval: '30s' }],
    'http_req_duration{name:consulta_saldo,load_level:350-449}': [{ threshold: 'p(95)<1000', abortOnFail: true, delayAbortEval: '30s' }],
    'http_req_duration{name:consulta_saldo,load_level:450-549}': [{ threshold: 'p(95)<1000', abortOnFail: true, delayAbortEval: '30s' }],
    'http_req_duration{name:consulta_saldo,load_level:550-600}': [{ threshold: 'p(95)<1000', abortOnFail: true, delayAbortEval: '30s' }],
    'http_req_duration{name:consulta_movimientos,load_level:200-249}': [{ threshold: 'p(95)<1000', abortOnFail: true, delayAbortEval: '30s' }],
    'http_req_duration{name:consulta_movimientos,load_level:250-349}': [{ threshold: 'p(95)<1000', abortOnFail: true, delayAbortEval: '30s' }],
    'http_req_duration{name:consulta_movimientos,load_level:350-449}': [{ threshold: 'p(95)<1000', abortOnFail: true, delayAbortEval: '30s' }],
    'http_req_duration{name:consulta_movimientos,load_level:450-549}': [{ threshold: 'p(95)<1000', abortOnFail: true, delayAbortEval: '30s' }],
    'http_req_duration{name:consulta_movimientos,load_level:550-600}': [{ threshold: 'p(95)<1000', abortOnFail: true, delayAbortEval: '30s' }],
    http_req_failed: [{ threshold: 'rate<0.001', abortOnFail: true, delayAbortEval: '30s' }],
    checks: ['rate>0.999'],
  },
};

export function setup() {
  if (!Array.isArray(TOKENS) || TOKENS.length < 600) {
    throw new Error(`HU-14 estres requiere 600 JWT distintos para llegar a 600 VU. Encontrados: ${TOKENS?.length ?? 0}. Archivo: ${TOKENS_PATH}`);
  }
  if (TOKENS.slice(0, 600).some((token) => typeof token !== 'string' || token.trim().length < 20)) {
    throw new Error('El archivo de tokens contiene valores vacios o invalidos.');
  }
  return { tokens: TOKENS.slice(0, 600) };
}

function nivelDeCarga(usuariosActivos) {
  if (usuariosActivos < 250) return '200-249';
  if (usuariosActivos < 350) return '250-349';
  if (usuariosActivos < 450) return '350-449';
  if (usuariosActivos < 550) return '450-549';
  return '550-600';
}

export default function (data) {
  const token = data.tokens[__VU - 1];
  const headers = {
    Authorization: `Bearer ${token}`,
    Accept: 'application/json',
  };

  // Distribuye la primera solicitud y evita una estampida inicial artificial.
  // Un ciclo cada ~200 s aproxima el perfil de 150 solicitudes/minuto a 500 VU.
  if (__ITER === 0) sleep(Math.random() * 200);

  const usuariosActivos = exec.instance.vusActive;
  const etiquetas = { load_level: nivelDeCarga(usuariosActivos) };
  let response;
  if (__ITER % 2 === 0) {
    response = http.get(`${__ENV.BASE_URL || 'http://localhost:8080'}/api/v1/cuentas/mia`, {
      headers,
      tags: { name: 'consulta_saldo', ...etiquetas },
    });
    check(response, { 'consulta de saldo responde HTTP 200': (r) => r.status === 200 });
  } else {
    response = http.get(`${__ENV.BASE_URL || 'http://localhost:8080'}/api/v1/cuentas/mia/movimientos?limite=20`, {
      headers,
      tags: { name: 'consulta_movimientos', ...etiquetas },
    });
    check(response, { 'consulta de movimientos responde HTTP 200': (r) => r.status === 200 });
  }

  if (response.timings.duration > 1000) {
    solicitudesLentas.add(1);
    usuariosEnSolicitudLenta.add(usuariosActivos);
    if (!avisoLentoEmitido) {
      console.warn(`[HU14] CANDIDATO_DEGRADACION vus=${usuariosActivos} nivel=${etiquetas.load_level} endpoint=${response.url} duracion_ms=${response.timings.duration.toFixed(2)}`);
      avisoLentoEmitido = true;
    }
  }

  if (response.status < 200 || response.status >= 300) {
    if (!avisoErrorEmitido) {
      console.warn(`[HU14] RESPUESTA_NO_EXITOSA vus=${usuariosActivos} nivel=${etiquetas.load_level} status=${response.status} endpoint=${response.url}`);
      avisoErrorEmitido = true;
    }
  }

  sleep(200);
}
