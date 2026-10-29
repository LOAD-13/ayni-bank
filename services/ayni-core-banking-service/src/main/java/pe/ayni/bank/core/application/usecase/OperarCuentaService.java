package pe.ayni.bank.core.application.usecase;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.core.domain.model.Asiento;
import pe.ayni.bank.core.domain.model.Comprobante;
import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.MotivoDeRechazo;
import pe.ayni.bank.core.domain.model.Movimiento;
import pe.ayni.bank.core.domain.model.NumeroDeCuenta;
import pe.ayni.bank.core.domain.model.OperacionAConfirmar;
import pe.ayni.bank.core.domain.model.OperacionRechazadaException;
import pe.ayni.bank.core.domain.model.TipoDeAsiento;
import pe.ayni.bank.core.domain.model.TipoDeEventoDeOperacion;
import pe.ayni.bank.core.domain.model.TipoDeMovimiento;
import pe.ayni.bank.core.domain.port.in.OperarCuentaUseCase;
import pe.ayni.bank.core.domain.port.out.LibroMayorPort;
import pe.ayni.bank.core.domain.port.out.PistaDeAuditoriaPort;
import pe.ayni.bank.core.domain.port.out.PublicadorDeEventosPort;
import pe.ayni.bank.core.domain.port.out.RegistroDeIdempotenciaPort;
import pe.ayni.bank.core.domain.port.out.RepositorioDeCuentasPort;
import pe.ayni.bank.core.domain.port.out.VerificadorDeConfirmacionPort;

/**
 * Orquesta las operaciones monetarias del titular · HU-07 y deposito simulado.
 *
 * <p>Cada operacion es <strong>una sola transaccion</strong> que hace, en este orden:
 * comprobar la clave de idempotencia, bloquear las cuentas, leer el saldo, construir el
 * movimiento (las reglas viven en el dominio), escribir los dos asientos, escribir el
 * evento en el outbox y recordar la clave. O pasa todo o no pasa nada.
 *
 * <p>Si dos peticiones con la misma clave llegan a la vez, las dos superan la comprobacion
 * inicial, pero la segunda choca con la clave primaria de {@code operacion_idempotente}
 * al recordar y su transaccion entera se deshace: el dinero no se mueve dos veces.
 *
 * <p>Cada operacion deja su evento en la pista de auditoria (AYNI-158). Los rechazos
 * tambien, en una transaccion propia, porque la de la operacion se deshace.
 */
@Service
public class OperarCuentaService implements OperarCuentaUseCase {

    private static final Logger log = LoggerFactory.getLogger(OperarCuentaService.class);
    private static final String AGREGADO = "Movimiento";

    private final RepositorioDeCuentasPort cuentas;
    private final LibroMayorPort libro;
    private final PublicadorDeEventosPort eventos;
    private final RegistroDeIdempotenciaPort idempotencia;
    private final PistaDeAuditoriaPort auditoria;
    private final VerificadorDeConfirmacionPort confirmaciones;
    private final Clock reloj;

    public OperarCuentaService(RepositorioDeCuentasPort cuentas, LibroMayorPort libro,
                               PublicadorDeEventosPort eventos,
                               RegistroDeIdempotenciaPort idempotencia,
                               PistaDeAuditoriaPort auditoria,
                               VerificadorDeConfirmacionPort confirmaciones, Clock reloj) {
        this.confirmaciones = confirmaciones;
        this.cuentas = cuentas;
        this.libro = libro;
        this.eventos = eventos;
        this.idempotencia = idempotencia;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public Comprobante transferir(UUID usuarioId, String numeroDestino, Dinero importe,
                                  String concepto, UUID clave, String confirmacion) {
        try {
            Resultado resultado = hacerTransferencia(usuarioId, numeroDestino, importe,
                    concepto, clave, confirmacion);
            auditar(resultado, TipoDeEventoDeOperacion.TRANSFERENCIA_REALIZADA, usuarioId);
            return resultado.comprobante();
        } catch (OperacionRechazadaException e) {
            auditoria.registrarRechazo(usuarioId, e.motivo());
            throw e;
        }
    }

    @Override
    @Transactional
    public Comprobante depositarSimulado(UUID usuarioId, Dinero importe, UUID clave) {
        try {
            Resultado resultado = hacerDeposito(usuarioId, importe, clave);
            auditar(resultado, TipoDeEventoDeOperacion.DEPOSITO_SIMULADO, usuarioId);
            return resultado.comprobante();
        } catch (OperacionRechazadaException e) {
            auditoria.registrarRechazo(usuarioId, e.motivo());
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Comprobante> comprobante(UUID usuarioId, UUID movimientoId) {
        List<Asiento> asientos = libro.asientosDelMovimiento(movimientoId);
        Optional<Comprobante> visto = cuentas.buscarActivaDe(usuarioId, Moneda.PEN).flatMap(cuenta -> {
            boolean esSuyo = asientos.stream().anyMatch(a -> a.cuentaId().equals(cuenta.id()));
            return esSuyo ? Optional.of(comprobanteDe(asientos, cuenta)) : Optional.<Comprobante>empty();
        });
        // HU-08 escenario 2 (AYNI-328): pedir el movimiento de otra persona es un evento de
        // seguridad. La pista se escribe en su propia transaccion: esta es de solo lectura.
        if (visto.isEmpty() && esAjeno(usuarioId, asientos)) {
            auditoria.registrarAccesoAjeno(usuarioId, movimientoId);
        }
        return visto;
    }

    /**
     * El movimiento existe y ninguna de sus cuentas es del usuario. Un identificador que no
     * existe no cuenta, y tampoco el titular de una cuenta bloqueada que pide un movimiento
     * suyo: se le niega igual, pero no es un acceso ajeno y no debe ensuciar la pista.
     */
    private boolean esAjeno(UUID usuarioId, List<Asiento> asientos) {
        return !asientos.isEmpty() && asientos.stream()
                .map(a -> libro.buscarPorId(a.cuentaId()))
                .flatMap(Optional::stream)
                .noneMatch(c -> usuarioId.equals(c.usuarioId()));
    }

    /** Una peticion repetida no movio dinero: queda como tal en la pista. */
    private void auditar(Resultado resultado, TipoDeEventoDeOperacion siEsNueva, UUID usuarioId) {
        auditoria.registrar(resultado.repetida() ? TipoDeEventoDeOperacion.OPERACION_REPETIDA : siEsNueva,
                usuarioId, resultado.comprobante().movimientoId());
    }

    /** El comprobante y si salio de una peticion repetida. */
    private record Resultado(Comprobante comprobante, boolean repetida) {
    }

    /**
     * La clave de idempotencia se mira dos veces. La primera, antes de bloquear, ahorra el
     * bloqueo a los reintentos normales. La segunda, ya con las cuentas bloqueadas, cubre
     * el caso de dos peticiones con la misma clave a la vez: la segunda espera el bloqueo,
     * y cuando lo obtiene la primera ya confirmo y su clave ya es visible. Sin esta segunda
     * mirada, la segunda peticion repetia el movimiento y solo la clave primaria de
     * {@code operacion_idempotente} lo impedia, con un error 500 en vez del comprobante.
     */
    private Resultado hacerTransferencia(UUID usuarioId, String numeroDestino, Dinero importe,
                                         String concepto, UUID clave, String confirmacion) {
        Cuenta origen = cuentaDe(usuarioId, importe.moneda());

        Optional<Comprobante> repetida = repetida(clave, origen);
        if (repetida.isPresent()) {
            return new Resultado(repetida.get(), true);
        }

        // Despues de mirar la clave y antes de tocar ningun saldo: un reintento de una
        // transferencia ya hecha no necesita confirmarse otra vez, pero una nueva si.
        confirmaciones.verificar(confirmacion, usuarioId,
                new OperacionAConfirmar(numeroDestino == null ? "" : numeroDestino, importe, clave));

        Cuenta destino = numeroValido(numeroDestino)
                .flatMap(libro::buscarPorNumero)
                .orElseThrow(() -> new OperacionRechazadaException(
                        MotivoDeRechazo.CUENTA_DESTINO_INEXISTENTE));

        libro.bloquear(ordenadas(origen.id(), destino.id()));
        Optional<Comprobante> adelantada = repetida(clave, origen);
        if (adelantada.isPresent()) {
            return new Resultado(adelantada.get(), true);
        }
        Dinero saldo = libro.saldoDe(origen.id(), origen.moneda());
        Dinero saldoDestino = libro.saldoDe(destino.id(), destino.moneda());

        Movimiento movimiento = Movimiento.transferencia(UUID.randomUUID(), origen, saldo,
                destino, importe, concepto, reloj.instant());

        // HU-13: el evento lleva lo que notification-service necesita para avisar a los dos
        // titulares sin consultar a nadie, y nada sensible: numeros enmascarados, ni nombres
        // ni documentos. Los saldos son los de despues del movimiento.
        return new Resultado(completar(movimiento, origen, clave,
                new TransferenciaRealizada(movimiento.id(), origen.id(), destino.id(),
                        importe.importe().toPlainString(), importe.moneda().name(),
                        origen.usuarioId(), destino.usuarioId(),
                        origen.numero().enmascarado(), destino.numero().enmascarado(),
                        saldo.menos(importe).importe().toPlainString(),
                        saldoDestino.mas(importe).importe().toPlainString(),
                        movimiento.asientos().get(0).registradoEn().toString())), false);
    }

    private Resultado hacerDeposito(UUID usuarioId, Dinero importe, UUID clave) {
        Cuenta destino = cuentaDe(usuarioId, importe.moneda());

        Optional<Comprobante> repetida = repetida(clave, destino);
        if (repetida.isPresent()) {
            return new Resultado(repetida.get(), true);
        }

        Cuenta fondeo = libro.cuentaDeFondeo(importe.moneda());
        libro.bloquear(ordenadas(fondeo.id(), destino.id()));
        Optional<Comprobante> adelantada = repetida(clave, destino);
        if (adelantada.isPresent()) {
            return new Resultado(adelantada.get(), true);
        }

        Dinero saldoDestino = libro.saldoDe(destino.id(), destino.moneda());
        Movimiento movimiento = Movimiento.depositoSimulado(UUID.randomUUID(), fondeo, destino,
                importe, reloj.instant());

        return new Resultado(completar(movimiento, destino, clave,
                new DepositoSimuladoRegistrado(movimiento.id(), destino.id(),
                        importe.importe().toPlainString(), importe.moneda().name(),
                        destino.usuarioId(), destino.numero().enmascarado(),
                        saldoDestino.mas(importe).importe().toPlainString(),
                        movimiento.asientos().get(0).registradoEn().toString())), false);
    }

    private Comprobante completar(Movimiento movimiento, Cuenta delTitular, UUID clave,
                                  Object evento) {
        libro.registrar(movimiento);
        eventos.registrar(AGREGADO, movimiento.id(), evento.getClass().getSimpleName(), evento);
        idempotencia.recordar(clave, movimiento.id());

        // Ni numeros de cuenta ni importes con nombre: solo identificadores internos.
        log.info("Movimiento registrado. movimientoId={} tipo={} cuentaId={}",
                movimiento.id(), movimiento.tipo(), delTitular.id());

        return comprobanteDe(movimiento.asientos(), delTitular);
    }

    private Cuenta cuentaDe(UUID usuarioId, Moneda moneda) {
        return cuentas.buscarActivaDe(usuarioId, moneda)
                .orElseThrow(() -> new OperacionRechazadaException(MotivoDeRechazo.SIN_CUENTA));
    }

    /**
     * Si la clave ya se uso, devuelve el comprobante de entonces.
     *
     * <p>Solo si el movimiento toca la cuenta de quien pregunta: una clave ajena no sirve
     * para leer la operacion de otro cliente.
     */
    private Optional<Comprobante> repetida(UUID clave, Cuenta delTitular) {
        return idempotencia.resultadoDe(clave).map(movimientoId -> {
            List<Asiento> asientos = libro.asientosDelMovimiento(movimientoId);
            boolean esSuya = asientos.stream().anyMatch(a -> a.cuentaId().equals(delTitular.id()));
            if (!esSuya) {
                throw new ClaveDeIdempotenciaReutilizadaException();
            }
            log.info("Peticion repetida: se devuelve el movimiento {} sin repetirlo.", movimientoId);
            return comprobanteDe(asientos, delTitular);
        });
    }

    /**
     * El comprobante visto desde la cuenta del titular.
     *
     * <p>Si la contraparte es la cuenta tecnica de fondeo, fue un deposito simulado; si no,
     * una transferencia. La contraparte se muestra siempre enmascarada.
     */
    private Comprobante comprobanteDe(List<Asiento> asientos, Cuenta delTitular) {
        Asiento cargo = unico(asientos, TipoDeAsiento.CARGO);
        Asiento abono = unico(asientos, TipoDeAsiento.ABONO);
        boolean pago = cargo.cuentaId().equals(delTitular.id());
        Asiento propio = pago ? cargo : abono;
        UUID contraparteId = pago ? abono.cuentaId() : cargo.cuentaId();

        TipoDeMovimiento tipo = tipoSegunContraparte(contraparteId, delTitular.moneda());
        String contraparte = switch (tipo) {
            case DEPOSITO_SIMULADO -> "Deposito simulado";
            case INTERES -> "Intereses Ayni";
            case TRANSFERENCIA -> libro.buscarPorId(contraparteId)
                    .map(c -> c.numero().enmascarado())
                    .orElse("Cuenta Ayni");
        };
        String propia = delTitular.numero().enmascarado();

        return new Comprobante(propio.movimientoId(), tipo,
                propio.importe(),
                pago ? propia : contraparte,
                pago ? contraparte : propia,
                propio.concepto(), propio.registradoEn(),
                libro.saldoDe(delTitular.id(), delTitular.moneda()));
    }

    /** Las cuentas tecnicas delatan el tipo: fondeo es un deposito; intereses, una capitalizacion. */
    private TipoDeMovimiento tipoSegunContraparte(UUID contraparteId, Moneda moneda) {
        if (libro.cuentaDeFondeo(moneda).id().equals(contraparteId)) {
            return TipoDeMovimiento.DEPOSITO_SIMULADO;
        }
        if (libro.cuentaDeIntereses(moneda).id().equals(contraparteId)) {
            return TipoDeMovimiento.INTERES;
        }
        return TipoDeMovimiento.TRANSFERENCIA;
    }

    private static Asiento unico(List<Asiento> asientos, TipoDeAsiento tipo) {
        return asientos.stream().filter(a -> a.tipo() == tipo).findFirst()
                .orElseThrow(() -> new IllegalStateException("Movimiento incompleto: falta el " + tipo));
    }

    private static List<UUID> ordenadas(UUID una, UUID otra) {
        return List.of(una, otra).stream().sorted(Comparator.naturalOrder()).toList();
    }

    private static Optional<NumeroDeCuenta> numeroValido(String numero) {
        if (numero == null) {
            return Optional.empty();
        }
        String limpio = numero.replaceAll("[\\s-]", "");
        try {
            return Optional.of(new NumeroDeCuenta(limpio));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** La misma clave de idempotencia se uso para una operacion de otro titular. */
    public static class ClaveDeIdempotenciaReutilizadaException extends RuntimeException {
        public ClaveDeIdempotenciaReutilizadaException() {
            super("La clave de idempotencia ya se uso en otra operacion.");
        }
    }

    /** Evento para el outbox. Sin numeros de cuenta: solo identificadores internos. */
    /** HU-13: lo que necesita notification-service para avisar a los dos titulares. */
    @SuppressWarnings("java:S107") // Un evento es un registro plano: un campo por dato.
    public record TransferenciaRealizada(UUID movimientoId, UUID cuentaOrigenId,
                                         UUID cuentaDestinoId, String importe, String moneda,
                                         UUID usuarioOrigenId, UUID usuarioDestinoId,
                                         String cuentaOrigen, String cuentaDestino,
                                         String saldoOrigen, String saldoDestino,
                                         String registradoEn) {
    }

    public record DepositoSimuladoRegistrado(UUID movimientoId, UUID cuentaId, String importe,
                                             String moneda, UUID usuarioId, String cuenta,
                                             String saldo, String registradoEn) {
    }
}
