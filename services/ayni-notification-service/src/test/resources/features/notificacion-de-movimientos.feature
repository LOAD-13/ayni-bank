# language: es
#
# HU-13 · Notificación por correo de cada movimiento · AYNI-24
#
# Corren contra los casos de uso con adaptadores en memoria y con el traductor real de
# los eventos de core-banking. La deduplicación con entregas simultáneas y el jsonb los
# prueba NotificacionesEnPostgresTest contra un PostgreSQL de verdad.
#
# El escenario 3 (una operación revertida no deja evento en el outbox) es de core-banking
# y lo prueba ConcurrenciaDeTransferenciasTest: aquí no puede llegar nada que no ocurrió.

Característica: Notificación por correo de cada movimiento
  Como titular de una cuenta Ayni
  Quiero recibir un correo por cada movimiento de mi cuenta
  Para enterarme al momento de lo que pasa con mi dinero

  Antecedentes:
    Dado que el reloj marca el 11 de octubre de 2026 a las 10:00 en Lima
    Y que "Ana" es titular con el correo "ana@example.pe"
    Y que "Bruno" es titular con el correo "bruno@example.pe"

  Escenario: Cada movimiento genera un correo con importe, fecha y saldo resultante
    Cuando core-banking publica que "Ana" transfirió PEN 1234.50 a "Bruno" dejando saldos de 765.50 y 2234.50
    Y pasa el despachador
    Entonces "ana@example.pe" recibe un correo "Transferencia enviada" que dice "PEN 1,234.50"
    Y el correo de "ana@example.pe" dice "11/10/2026 a las 10:00" y "saldo disponible es PEN 765.50"
    Y "bruno@example.pe" recibe un correo "Recibiste una transferencia" que dice "saldo disponible es PEN 2,234.50"
    Y ningún correo contiene un número de cuenta completo

  Escenario: Un evento duplicado produce un solo correo
    Cuando core-banking publica que "Ana" transfirió PEN 50.00 a "Bruno" dejando saldos de 950.00 y 1050.00
    Y el mismo mensaje llega otra vez
    Y pasa el despachador
    Y pasa el despachador
    Entonces "ana@example.pe" recibe exactamente 1 correo
    Y "bruno@example.pe" recibe exactamente 1 correo

  Escenario: Si el correo falla se reintenta con espera creciente hasta el máximo configurado
    Dado que el proveedor de correo está caído y se permiten 3 intentos con una espera base de 30 segundos
    Cuando core-banking publica un depósito de PEN 500.00 para "Ana" con saldo resultante 1500.00
    Y pasa el despachador
    Entonces el aviso queda pendiente para dentro de 30 segundos
    Cuando pasan 30 segundos y pasa el despachador
    Entonces el aviso queda pendiente para dentro de 60 segundos
    Cuando pasan 60 segundos y pasa el despachador
    Entonces el aviso queda fallido con 3 intentos registrados
    Y "ana@example.pe" recibe exactamente 0 correos
