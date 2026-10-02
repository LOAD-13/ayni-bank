# ADR-0026 · Producción en AWS con despliegue continuo desde `main`

**Estado:** Aceptada · reemplaza a [ADR-0005](0005-infraestructura-hibrida-arm64.md)
**Fecha:** 2 de octubre de 2026

---

## Contexto

ADR-0005 eligió una infraestructura híbrida: la Raspberry Pi 5 como staging y Oracle Cloud Always
Free (ARM64) como producción. Dos hechos la dejaron sin efecto:

1. **Oracle redujo su capa gratuita.** Desde el 15 de junio de 2026 la instancia Ampere gratuita
   pasó de 4 OCPU y 24 GB a 2 OCPU y 12 GB, y desde el 18 de agosto termina las instancias
   inactivas. El riesgo R-10 de la matriz se materializó.
2. **El APF2 exige demostrar el despliegue en una nube pública**, en vivo: un *merge* a la rama
   principal tiene que llegar solo a producción, con el pipeline en verde. La Pi, detrás del router
   de un integrante, no es una nube pública, y el CD que dependía de ella llevaba fallando desde el
   15 de septiembre.

La cuenta de AWS del proyecto está en el **plan gratuito, con 100 USD de crédito hasta abril de
2027**. Los servicios elegibles incluyen la instancia `m7i-flex.large` (2 vCPU, 8 GB) y RDS
`db.t4g.micro`.

## Opciones consideradas

**A. ECS Fargate + RDS + ALB.** Lo más «nativo», pero el ALB solo ya cuesta ~16 USD/mes, y seis
tareas Fargate con la memoria que pide el KYC (TensorFlow) superan el crédito en dos meses.

**B. Una instancia EC2 con Docker Compose + RDS + S3.** Una sola máquina de 8 GB para los
contenedores, la base de datos gestionada aparte. Es el mismo compose que el equipo ya usa en local
(paridad dev/prod del 12-Factor), cuesta 45–55 USD/mes y se apaga de noche.

**C. Todo en una EC2, PostgreSQL incluido.** Algo más barato, pero las copias, la restauración a un
punto en el tiempo y el monitoreo de la base habría que montarlos a mano, y el APF2 pide
evidencias de monitoreo y administración de la base de datos en producción.

## Decisión

**Opción B**, definida entera como código en `infra/aws/ayni-bank.cfn.yaml` (CloudFormation):

| Pieza | Elección | Por qué |
|---|---|---|
| Cómputo | EC2 `m7i-flex.large`, Ubuntu 24.04, `amd64` | 8 GB; el KYC solo ocupa más de 1 GB. Elegible en el plan gratuito |
| Entrada | Caddy en la propia instancia | HTTPS automático de Let's Encrypt, cabeceras de seguridad y un único puerto público |
| Dominio | `ayni.<ip>.sslip.io` sobre una IP elástica | Certificado válido sin comprar un dominio |
| Base de datos | RDS PostgreSQL 17 `db.t4g.micro`, cifrada | Copia diaria con restauración a un punto en el tiempo (1 día, el máximo del plan gratuito), métricas y logs en CloudWatch, alarmas |
| Documentos KYC | S3, privado, cifrado, solo TLS | Sustituye a MinIO; el cliente MinIO de los servicios es compatible |
| Imágenes | ECR, escaneo al publicar, se guardan las 10 últimas | La instancia descarga con su rol de IAM: sin tokens personales |
| Administración | AWS Systems Manager | **No hay puerto SSH abierto ni claves que custodiar** |
| Secretos | Parameter Store y Secrets Manager | Se inyectan en cada despliegue; nunca están en git |
| Ahorro | No se enciende sola: la enciende el pipeline al desplegar; se apaga cada día a las 23:00 | El plan gratuito descuenta todo de los créditos y el cómputo es lo que más consume |

**Pipeline** (`.github/workflows/cd.yml`), en cada *push* a `main`:

```
CI completa (la misma de los PR) ─▶ imágenes a ECR ─▶ despliegue por SSM
   ─▶ espera de salud en la instancia ─▶ pruebas de humo contra la URL pública ─▶ tag + Release
                        └─ si algo falla ─▶ vuelve sola a la versión anterior
```

GitHub obtiene credenciales temporales por **OIDC**: el rol `ayni-github-despliegue` solo lo puede
asumir este repositorio desde `main` o desde el entorno `production`, y solo puede publicar en los
repositorios `ayni/*` de ECR y ordenar comandos a esta instancia.

La versión que se despliega sale del fichero `VERSION` de la raíz y se muestra bajo el formulario de
ingreso. Subirla (1.0.0 → 1.0.1) y fusionar es la forma visible de comprobar que el despliegue fue
automático.

**Estrategia de despliegue: recreación con reversión automática.** No es Blue-Green ni Canary: con
una sola instancia no hay dos entornos entre los que repartir tráfico. Se compensa con dos
garantías: el script de la instancia no da el despliegue por bueno hasta que los ocho contenedores
están sanos, y el pipeline vuelve a la configuración anterior si las pruebas de humo fallan. La
interrupción medida es de segundos, mientras se recrean los contenedores que cambian.

## Consecuencias

**A favor**

- Despliegue continuo real y demostrable: cada *merge* a `main` llega solo a producción.
- Cero secretos en el repositorio y cero claves de larga duración en GitHub.
- La infraestructura se recrea con un comando, y su descripción es el propio fichero.
- Base de datos gestionada: copias, PITR y métricas sin operar nada.

**En contra**

- **Un solo punto de fallo de cómputo.** Si la instancia cae, cae el servicio hasta que se
  recupere; la alarma de CloudWatch la recupera sola si el fallo es del hardware de AWS.
- **No hay staging separado.** Lo cubren la CI completa antes de cada despliegue y la reversión
  automática. Un entorno de staging duplicaría el coste.
- **Coste.** Consume el crédito del plan gratuito; la alerta de presupuesto avisa al 50 % de 40
  USD/mes.
- `sslip.io` es un servicio de terceros para resolver el nombre. Si dejara de funcionar, basta un
  dominio propio y cambiar una variable.
