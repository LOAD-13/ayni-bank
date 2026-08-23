# syntax=docker/dockerfile:1
# Imagen ARM64 para Raspberry Pi 5 y Oracle Ampere. Ver ADR-0005.

FROM python:3.12-slim AS base
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends         libglib2.0-0 libgl1 curl     && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY src ./src

RUN useradd --create-home --shell /bin/false ayni && chown -R ayni:ayni /app
USER ayni

EXPOSE 8000
HEALTHCHECK --interval=20s --timeout=5s --start-period=30s --retries=5     CMD curl -fsS http://localhost:8000/health || exit 1

CMD ["uvicorn", "src.infrastructure.web.main:app", "--host", "0.0.0.0", "--port", "8000"]
