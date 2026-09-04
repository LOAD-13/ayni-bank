# syntax=docker/dockerfile:1
# Imagen ARM64: la misma corre en la Raspberry Pi 5 (staging) y en Oracle Ampere (produccion).
# Ver ADR-0005.

# ── Dependencias ──────────────────────────────────────────────────────────
# En su propia etapa para que cambiar codigo no obligue a reinstalar el arbol
# de npm entero: mientras package-lock.json no cambie, esta capa sale de cache.
FROM node:20-alpine AS deps
WORKDIR /build
COPY web/ayni-web/package.json web/ayni-web/package-lock.json ./
# `npm ci` instala exactamente lo que fija el lockfile y falla si package.json
# y el lockfile discrepan. `npm install` los reconciliaria en silencio, que es
# justo lo que no queremos en una imagen que va a produccion.
RUN npm ci

# ── Construccion ──────────────────────────────────────────────────────────
FROM node:20-alpine AS build
WORKDIR /build
COPY --from=deps /build/node_modules ./node_modules
COPY web/ayni-web ./
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

# ── Ejecucion ─────────────────────────────────────────────────────────────
FROM node:20-alpine

# Dos saneamientos que Trivy exige, y que conviene entender antes de tocarlos:
#
# 1. `apk upgrade` del arbol completo. La base arrastra paquetes de Alpine con
#    CVE ya corregidas aguas arriba —OpenSSL ayer, libexpat hoy— y la lista
#    cambia sola porque la base de datos de Trivy se actualiza a diario. Se
#    actualiza todo en vez de fijar paquetes a mano: fijarlos obliga a editar
#    cinco Dockerfiles cada vez que sale una CVE nueva. Ver el riesgo R-16.
#
# 2. Borrar npm. La imagen oficial de Node deja instalado el CLI de npm, y su
#    arbol de dependencias arrastra 20 avisos —una CRITICA en node-tar y 19
#    ALTAS en pacote, sigstore, glob, minimatch, cross-spawn y brace-expansion—
#    que no son de esta aplicacion: `npm audit` sobre el proyecto da cero. El
#    servidor `standalone` arranca con `node server.js` y jamas invoca a npm,
#    de modo que aqui es superficie de ataque sin funcion.
#
# Al elevar la imagen base, volver a escanear y comprobar si estas lineas sobran.
RUN apk upgrade --no-cache \
    && rm -rf /usr/local/lib/node_modules/npm \
    /usr/local/bin/npm /usr/local/bin/npx \
    && addgroup -S ayni && adduser -S ayni -G ayni

WORKDIR /app
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

# La salida `standalone` trae el servidor con solo las dependencias que el build
# alcanza. `static` y `public` van aparte porque Next no los incluye ahi.
COPY --from=build --chown=ayni:ayni /build/.next/standalone ./
COPY --from=build --chown=ayni:ayni /build/.next/static ./.next/static
COPY --from=build --chown=ayni:ayni /build/public ./public

USER ayni
EXPOSE 3000
# 127.0.0.1 y no localhost: con HOSTNAME=0.0.0.0, Node abre socket solo en
# IPv4, mientras que dentro del contenedor `localhost` resuelve primero a ::1.
# El resultado es un «connection refused» contra un servidor que esta vivo, y
# un contenedor marcado como unhealthy sin motivo. Los servicios Java no lo
# sufren porque la JVM escucha en dual-stack.
HEALTHCHECK --interval=20s --timeout=5s --start-period=20s --retries=5 \
    CMD wget -qO- http://127.0.0.1:3000/ >/dev/null || exit 1
CMD ["node", "server.js"]
