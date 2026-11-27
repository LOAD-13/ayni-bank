# Pruebas de seguridad web sobre producción · OWASP ZAP, Nikto, Nmap y testssl.sh

**AYNI-313** · 26-11-2026, 21:52 (hora de Lima) · objetivo `https://ayni.184-194-190-144.sslip.io`,
versión 2.0.0 · workflow «Pruebas de seguridad web»,
[ejecución 45930744775](https://github.com/LOAD-13/ayni-bank/actions/runs/45930744775)

## Resultado

**Ningún hallazgo de riesgo alto.** Se cumple el criterio de la Definition of Done para un
despliegue a producción («OWASP ZAP ejecutado sin hallazgos de riesgo alto»). Los dos hallazgos
de riesgo medio son una decisión ya tomada y documentada. Al revisar lo que ZAP no puede ver se
encontró y corrigió uno más: las respuestas con datos del cliente no prohibían guardarse en caché
(ver más abajo).

| Riesgo | Hallazgos de ZAP |
|---|---|
| Alto | **0** |
| Medio | 2 |
| Bajo | 1 |
| Informativo | 9 |

El informe completo de ZAP 2.17.0 está en
[`evidencia/zap-baseline-2026-11-26.html`](evidencia/zap-baseline-2026-11-26.html).

## Hallazgos y decisión

| Riesgo | Hallazgo | Dónde | Decisión |
|---|---|---|---|
| Medio | CSP: `script-src 'unsafe-inline'` | portada, `/ingresar`, `/pendiente` | **Aceptado.** La hidratación de Next.js mete scripts en línea; quitarlo exige *nonces* generados por petición en un middleware, que obliga a renderizar en dinámico cada página. El resto de la política sigue cerrando lo importante: solo scripts propios, `object-src 'none'`, `frame-ancestors 'none'`, `base-uri` y `form-action` en `'self'`. Está razonado en el `Caddyfile` |
| Medio | CSP: `style-src 'unsafe-inline'` | las mismas | **Aceptado.** Next.js y los componentes aplican estilos en línea (`style={…}`). Un estilo en línea no ejecuta código: el riesgo es mucho menor que en scripts |
| Bajo | Falta `Cross-Origin-Embedder-Policy` | todas | **Aceptado por ahora.** `require-corp` bloquearía cualquier recurso de otro origen sin cabecera CORP, y la subida del DNI y la selfie va a S3. Solo hace falta para APIs como `SharedArrayBuffer`, que la web no usa |
| Info | *Base64 Disclosure* | una imagen y un *chunk* de JavaScript | Falso positivo: son el contenido de la imagen y código empaquetado, no datos |
| Info | *Sec-Fetch-\** ausentes | peticiones del propio ZAP | Las cabeceras las envía el navegador; ZAP no las manda |
| Info | Contenido almacenable en caché, directivas de caché | estáticos de `/_next` | Esperado: los estáticos llevan hash en el nombre y se cachean a propósito |
| Info | *Modern Web Application*, *Non-Storable Content* | — | Descriptivos, sin riesgo |

## Lo que ZAP no ve: la API detrás de la sesión

El *baseline* solo recorre páginas públicas. Al comprobar a mano las respuestas autenticadas se
vio que `/api/v1/cuentas/mia`, los movimientos y el resto de rutas con sesión, que devuelven
saldo y número de cuenta, **no llevaban `Cache-Control`**. El riesgo real era bajo: una
respuesta a una petición con `Authorization` no la guarda una caché compartida, y sin fecha de
modificación el navegador no la reutiliza. Pero en un banco no debe depender de eso: un equipo
compartido o un proxy mal configurado podrían conservar el saldo de otra persona.

**Corregido en el gateway**: toda ruta protegida responde con `Cache-Control: no-store`,
también los 401. Las rutas públicas no cambian, para que los estáticos se sigan cacheando. Dos
pruebas nuevas en `FiltroDeAutenticacionTest`, y comprobado contra el gateway local:

```
GET /api/v1/cuentas/mia (con sesión)   200  Cache-Control: no-store
GET /api/v1/cuentas/mia (sin sesión)   401  Cache-Control: no-store
```

Llega a producción con la siguiente versión.

## Las otras tres herramientas

| Herramienta | Qué encontró |
|---|---|
| **Nmap** | Solo 80 y 443 abiertos, los dos con Caddy. El 80 solo redirige a HTTPS. Ni la base de datos, ni RabbitMQ, ni Prometheus son alcanzables desde internet |
| **testssl.sh** | Solo TLS 1.2 y 1.3; TLS 1.1 y anteriores, desactivados. No vulnerable a Heartbleed, CCS, Ticketbleed, CRIME, POODLE, SWEET32 ni FREAK. Avisa de **BREACH** por la compresión gzip de `/` (ver abajo) |
| **Nikto** | Sin hallazgos relevantes: identifica Caddy y el anuncio de HTTP/3 (`alt-svc`), que Nikto no puede probar |

**Sobre BREACH.** El ataque necesita una respuesta comprimida que mezcle un secreto con texto
que controla el atacante, y que el atacante pueda observar el tamaño de muchas respuestas. La
portada no contiene secretos y la sesión no viaja en el cuerpo: el token de acceso va en la
cabecera `Authorization` y el de renovación, en una cookie `HttpOnly`. Se deja anotado; si una
respuesta llegara a reflejar entrada del usuario junto a un dato sensible, habría que excluirla
de la compresión en el `Caddyfile`.

## Cómo repetirlo

GitHub → *Actions* → «Pruebas de seguridad web» → *Run workflow*, con la URL de producción.
Es un escaneo pasivo: no ataca el sistema. Cada herramienta sube su informe como artefacto de
la ejecución. Antes de lanzarlo, comprobar que la instancia está encendida: se apaga sola a las
23:00.

Producción sigue en la 2.0.0. Al desplegar la 2.1.0 hay que repetir el escaneo, porque cambian
la web y la API.
