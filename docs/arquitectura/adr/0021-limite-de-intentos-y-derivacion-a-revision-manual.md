# ADR-0021 · Límite de tres intentos y derivación a revisión manual

- **Estado:** aceptado
- **Fecha:** 12 de septiembre de 2026
- **Historias afectadas:** HU-02 (verificación de identidad)
- **Reemplaza a:** ninguno

## Contexto

La subtarea 11 de AYNI-13 pide el límite de tres intentos que ya menciona `diseno-base.md`
§3.5: *"ante fallo, el onboarding responde 'verificación en revisión manual' en lugar de
romperse"*. La migración V5 (subtarea 9) ya dejó anotado que esta subtarea es quien decide
cómo se filtran los reintentos, y el ADR-0020 (subtarea 10) dejó explícitamente pendiente
qué hacer con `KycServiceNoDisponibleException`.

**Dos motivos de fallo, no uno.** Una verificación de identidad puede no prosperar por dos
razones que no se parecen:

1. **Algo que el usuario puede corregir**: el documento fue rechazado por `kyc-service`
   (mala calidad, DNI no reconocido), o los datos declarados en el registro no coinciden con
   lo que el OCR leyó (ADR-0009). Aquí tiene sentido dejar reintentar — con mejor luz, con el
   documento correcto — hasta un límite.
2. **Algo que el usuario no puede corregir**: `kyc-service` no respondió tras agotar
   Resilience4j (`KycServiceNoDisponibleException`, ADR-0020). Pedirle que vuelva a intentar
   no cambia nada: el problema no está en su documento, está en la infraestructura.

Tratar los dos casos igual — contar ambos contra el mismo límite — haría que una caída
temporal del servicio le costara intentos a alguien que no hizo nada mal.

## Decisión

**Un contador de intentos en `solicitud_onboarding`, y dos vías de entrada distintas.**

`identity.solicitud_onboarding.intentos_verificacion_kyc` (migración V6) cuenta los fallos
del primer tipo. `GestionarFalloDeVerificacionKycUseCase` (`domain/port/in/`) expone dos
métodos:

- `registrarFalloDeUsuario(solicitudId)`: suma un intento y devuelve
  `ResultadoDelIntentoKyc.PUEDE_REINTENTAR` o, al llegar a tres, deriva la solicitud a
  `EN_REVISION_MANUAL` y devuelve `DERIVADA_A_REVISION_MANUAL`.
- `derivarPorServicioNoDisponible(solicitudId)`: deriva a `EN_REVISION_MANUAL` de inmediato,
  sin tocar el contador. Es lo que el futuro caso de uso de integración debe llamar al
  atrapar `KycServiceNoDisponibleException` (pendiente anotado en ADR-0020).

**Por qué un contador y no una lista de intentos.** No hace falta guardar el historial de
cada intento (qué documento, qué error) para decidir cuándo derivar a revisión manual — solo
hace falta el conteo. Una tabla de intentos sería la opción correcta si algún día se necesita
auditar cada fallo individualmente; hoy nadie pidió eso, y añadirla sería la abstracción que
`GenerarUrlDeSubidaService` (subtarea 7) ya evitó una vez.

**Por qué el límite vive en el caso de uso y no en el repositorio.** El puerto
(`registrarIntentoFallidoDeKyc`) solo incrementa y devuelve el total; quien compara ese total
contra `LIMITE_DE_INTENTOS = 3` es `GestionarFalloDeVerificacionKycService`. Igual que en
ADR-0020 con Resilience4j: la infraestructura persiste, el dominio decide. Si el límite
cambiara mañana (política de negocio, no de almacenamiento), el cambio no toca la
migración ni el adaptador.

**Por qué el `CHECK (intentos_verificacion_kyc BETWEEN 0 AND 3)` en la base.** El límite de
tres ya se hace cumplir en el servicio, pero la tabla es también un límite de defensa: si un
futuro cambio en el caso de uso olvidara comprobarlo, la base rechaza la fila en vez de
guardar un intento número quince en silencio. Se verificó experimentalmente contra el
`ayni-postgres` real (transacción abortada con `ON_ERROR_STOP=1`, sin persistir nada): el
`ALTER TABLE` aplica, el `DEFAULT 0` funciona, y un `UPDATE` a 4 sí es rechazado.

## Alternativas evaluadas

**Contar también `KycServiceNoDisponibleException` contra el límite de tres.** Es lo más
simple de implementar — un solo camino, un solo contador — pero penaliza al usuario por una
caída de infraestructura que no depende de él. Descartado: viola el principio de que un
límite de intentos existe para corregir algo que la persona controla.

**Guardar el motivo de cada fallo en una tabla `intento_verificacion_kyc`.** Permitiría
responder "por qué" se derivó una solicitud, no solo "cuántas veces". Se descarta por ahora
porque nadie lo pidió — ni el diseño base ni el sprint backlog mencionan trazabilidad por
intento — y es exactamente el tipo de tabla que se puede añadir después sin romper nada,
porque el contador de `solicitud_onboarding` seguiría siendo la fuente de verdad para el
límite.

## Consecuencias

**A favor**

- Una caída de `kyc-service` no consume intentos del usuario: se resuelve con
  `derivarPorServicioNoDisponible`, coherente con lo que ADR-0020 dejó pendiente.
- El límite de negocio (3) vive en una sola constante, en el caso de uso, fácil de encontrar
  y de cambiar.
- Probado sin Spring ni base de datos real (`GestionarFalloDeVerificacionKycServiceTest`,
  dobles escritos a mano, mismo patrón que `AprobarSolicitudServiceTest`).

**En contra**

- No hay endpoint HTTP ni caso de uso de integración todavía que invoque
  `registrarFalloDeUsuario`/`derivarPorServicioNoDisponible`: hoy el mecanismo existe y está
  probado, pero nada en el flujo real de la aplicación lo llama, porque ese caso de uso de
  integración (URL de subida → hash → `documento_kyc` → `VerificadorKycPort.iniciar` →
  interpretar el resultado) sigue siendo la deuda explícita ya anotada en el plan de AYNI-13.
- El contador no distingue de qué documento (anverso/reverso/selfie) vino el fallo. Si eso
  importa para la UI de la subtarea 12, se añade entonces — no antes de que haga falta.

## Pendiente

- **Nota de la subtarea 14 (ADR-0024)**: el escenario de Jira para "agotamiento de
  intentos" narra "falla 3 veces, intenta una cuarta, y esa cuarta deriva", mientras que
  esta ADR implementó que el tercer fallo mismo deriva. Se decidió mantener el código tal
  como está (coincide con el criterio de aceptación en prosa del ticket, "máximo 3
  intentos... antes de derivar") en vez de perseguir la narrativa del escenario de ejemplo.
  Revisar si esto cambia cuando exista feedback real de uso.
- El caso de uso de integración de HU-02 debe llamar a
  `GestionarFalloDeVerificacionKycUseCase` en los dos puntos que le corresponden: capturar
  `KycServiceNoDisponibleException` de `VerificadorKycPort.iniciar` y llamar a
  `derivarPorServicioNoDisponible`, o capturar un resultado de verificación rechazado y
  llamar a `registrarFalloDeUsuario`.
- No hay endpoint HTTP que exponga a la persona por qué su solicitud quedó en revisión
  manual (mensaje al usuario) — eso es UI/UX, fuera del alcance de esta subtarea.
