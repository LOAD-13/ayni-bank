# ADR-0037 · Alta disponibilidad de la base de datos: dos niveles de RPO

**Estado:** Aceptada. En producción queda **preparada y apagada** hasta que se apruebe el gasto.
**Fecha:** 26 de octubre de 2026
**Historia:** AYNI-154 (T-15, hallazgo del Taller de Diseño Físico de la semana 6, apartados 6.2 y 6.3)

---

## Contexto

La base de producción es una sola instancia RDS (`MultiAZ: false`) con un día de copias automáticas.
Si cae, el RPO real no es de minutos: es «todo lo ocurrido desde la última copia o el último punto
recuperable». En el escenario central del taller, cinco minutos de pérdida son unas 7 600
transacciones ya confirmadas al cliente que dejarían de existir. El cliente vio su comprobante y el
banco no tendría el asiento.

Un solo RPO para todo no sirve. La confirmación síncrona a otra región es físicamente inviable: con
20–80 ms de latencia entre regiones, cada operación se volvería lenta. Por eso la banca fija dos
objetivos.

## Decisión

**Dos niveles, con objetivos distintos:**

| | Fallo | RPO | RTO | Cómo |
|---|---|---|---|---|
| **Nivel 1** | Caída de un nodo (el habitual) | **0** | < 5 min | Réplica síncrona en la misma región con promoción automática |
| **Nivel 2** | Desastre regional (el raro) | < 5 min | < 4 h | Copias y WAL replicados a otra región; restauración a un instante |

Disponibilidad objetivo: 99,9 % (43 min al mes).

**1. En AWS, el nivel 1 es RDS Multi-AZ.** El taller pedía Patroni o repmgr porque partía de una base
autogestionada en OCI. Desde el ADR-0026 la base es RDS, y Multi-AZ hace exactamente ese trabajo de
forma gestionada:

- mantiene una réplica en otra zona de disponibilidad y escribe en las dos de forma síncrona antes de
  confirmar: RPO 0;
- detecta la caída, promueve la réplica y mueve el DNS del *endpoint*. AWS lo cifra en uno o dos
  minutos, y la aplicación no cambia de configuración: reconecta al mismo nombre.

Reproducirlo con Patroni sobre EC2 exigiría dos instancias, etcd y su operación, para obtener lo
mismo. El CloudFormation lleva el parámetro **`AltaDisponibilidad`** (por defecto `false`), que activa
`MultiAZ` y sube la retención de copias de 1 a 7 días.

**2. Queda apagado por coste.** Multi-AZ duplica el coste de la instancia y del almacenamiento de
RDS, y el plan gratuito no lo cubre. El taller estimó la configuración completa en unos US$ 73 al
mes, pero sobre tarifas de OCI. Antes de activarlo hay que rehacer la cuenta con la calculadora de
AWS. Activarlo es un único `cloudformation deploy` (manual de despliegue, apartado 11).

**3. El nivel 2 son las copias automáticas replicadas a otra región**
(`start-db-instance-automated-backups-replication`). Copian instantáneas y WAL, lo que permite
restaurar a un instante en `us-west-2` si se pierde `us-east-1`. Se activa a mano porque depende de
una clave KMS en la región de destino.

**4. Alcance del nivel 1.** El taller limita el RPO 0 a `core.asiento`, `core.cuenta` e
`identity.usuario`. Los catálogos, las plantillas y los informes toleran el nivel 2. Con RDS Multi-AZ
la réplica es de almacenamiento y cubre toda la instancia: no se puede elegir por tabla. Con una base
autogestionada sí se puede, y se haría por servicio: `synchronous_commit = local` como valor general y
`remote_apply` solo en las conexiones de core-banking e identity (por ejemplo,
`?options=-c%20synchronous_commit%3Dremote_apply` en su URL de JDBC).

**5. RAID.** El taller exige RAID 1 o 10 en todo nodo con datos y descarta RAID 5 por su penalización
de escritura con 4,37 M de inserciones al día.

- **En RDS no aplica.** El almacenamiento es EBS, que AWS ya replica dentro de la zona. Con Multi-AZ,
  además, hay una segunda copia completa en otra zona. No hay discos que configurar.
- **En un nodo propio**, por ejemplo la Raspberry Pi que el taller proponía como réplica de coste
  cero, se aplica RAID 1 con `mdadm` sobre dos SSD:
  `mdadm --create /dev/md0 --level=1 --raid-devices=2 /dev/sda /dev/sdb`.

**6. Laboratorio local para demostrarlo y medirlo.** `infra/docker/ha/` levanta un primario y una
réplica síncrona de PostgreSQL 16 (`synchronous_commit = remote_apply`,
`synchronous_standby_names = FIRST 1 (replica)`). Es independiente del entorno de desarrollo.
`infra/scripts/prueba-de-conmutacion.sh` mide el coste de confirmar, comprueba el RPO, mata el
primario, promueve la réplica y cronometra. Resultado en una máquina de desarrollo (500
confirmaciones de cada tipo):

| | Medido |
|---|---|
| Confirmación local | 2,45 ms |
| Confirmación síncrona (`remote_apply`) | 2,79 ms (**+0,34 ms**) |
| Transacciones confirmadas perdidas tras matar el primario | **0 de 1 000** |
| De la caída a la réplica aceptando escrituras | **0,8 s** (promoción disparada por el script) |

El sobrecoste por confirmación queda por debajo del rango de 1–3 ms que estimó el taller, porque
ambos nodos comparten máquina. Entre zonas de AWS será mayor. Las cifras de confirmación incluyen el
viaje de `psql` por cada sentencia, igual en los dos casos.

## Advertencia: el RPO 0 no protege del error humano

Un `DELETE` mal filtrado se replica en milisegundos con la misma fidelidad que un dato bueno. Para
eso está el WAL archivado (la restauración a un instante de RDS, T-14), que permite volver al momento
anterior al error. No son redundantes: la réplica cubre la pérdida de hardware y el PITR cubre el
error. Por eso `AltaDisponibilidad` sube también la retención a 7 días.

## Consecuencias

- Con el parámetro activado, la caída de la zona de la base deja de ser una pérdida de datos y se
  convierte en un corte de uno o dos minutos.
- Mientras siga apagado, el riesgo del contexto existe: es una decisión de coste consciente, no un
  olvido. La disponibilidad real se mide en el tablero de salud técnica (SLO actual 99,5 %).
- Una réplica síncrona única tiene un coste propio: si ella cae, el primario no puede confirmar. RDS
  lo resuelve reemplazándola. En el laboratorio, con una sola réplica, se ve tal cual.
- La aplicación no cambia: ni configuración ni código.
