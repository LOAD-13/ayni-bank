package pe.ayni.bank.core.application.usecase;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.core.domain.model.Capitalizacion;
import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.Devengo;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.Movimiento;
import pe.ayni.bank.core.domain.model.TasaVigente;
import pe.ayni.bank.core.domain.model.TipoDeEventoDeOperacion;
import pe.ayni.bank.core.domain.port.in.CerrarDiaUseCase;
import pe.ayni.bank.core.domain.port.out.LibroMayorPort;
import pe.ayni.bank.core.domain.port.out.PistaDeAuditoriaPort;
import pe.ayni.bank.core.domain.port.out.PublicadorDeEventosPort;
import pe.ayni.bank.core.domain.port.out.RegistroDeInteresesPort;

/**
 * El cierre diario de HU-06 (ADR-0032).
 *
 * <p>El dia es el de Lima: el saldo de cierre del 11 de octubre es el que habia a las 00:00
 * del 12 en Peru. Cada dia se cierra en una sola transaccion: o quedan todos los devengos, la
 * capitalizacion y la marca de cierre, o no queda nada y el siguiente intento lo repite.
 *
 * <p>El abono de la capitalizacion se fecha en el primer instante del mes siguiente, no en
 * el momento en que corre el proceso. Asi, aunque el cierre del 31 se recupere con dias de
 * retraso, el interes cuenta en el saldo desde el dia 1 y empieza a rendir a su vez.
 */
@Service
public class CierreDiarioService implements CerrarDiaUseCase {

    public static final ZoneId LIMA = ZoneId.of("America/Lima");

    private static final Logger log = LoggerFactory.getLogger(CierreDiarioService.class);
    private static final String AGREGADO = "Movimiento";

    private final RegistroDeInteresesPort intereses;
    private final LibroMayorPort libro;
    private final PublicadorDeEventosPort eventos;
    private final PistaDeAuditoriaPort auditoria;
    private final Clock reloj;

    public CierreDiarioService(RegistroDeInteresesPort intereses, LibroMayorPort libro,
                               PublicadorDeEventosPort eventos, PistaDeAuditoriaPort auditoria, Clock reloj) {
        this.intereses = intereses;
        this.libro = libro;
        this.eventos = eventos;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    /**
     * Si nunca se cerro un dia, se empieza por ayer: no se devengan retroactivamente los
     * meses anteriores a la puesta en marcha.
     */
    @Override
    @Transactional(readOnly = true)
    public List<LocalDate> diasPendientes() {
        LocalDate ayer = LocalDate.now(reloj.withZone(LIMA)).minusDays(1);
        LocalDate desde = intereses.ultimoCierre().map(d -> d.plusDays(1)).orElse(ayer);
        return desde.datesUntil(ayer.plusDays(1)).toList();
    }

    @Override
    @Transactional
    public void cerrar(LocalDate fecha) {
        if (intereses.estaCerrado(fecha)) {
            return;
        }
        Instant corte = fecha.plusDays(1).atStartOfDay(LIMA).toInstant();
        Instant ahora = reloj.instant();
        int cuentas = 0;
        BigDecimal devengado = BigDecimal.ZERO;
        for (Cuenta cuenta : intereses.cuentasQueDevengan(corte)) {
            Optional<Devengo> devengo = devengar(cuenta, fecha, corte, ahora);
            if (devengo.isPresent()) {
                intereses.registrar(devengo.get());
                cuentas++;
                devengado = devengado.add(devengo.get().importe());
            }
        }
        if (fecha.equals(YearMonth.from(fecha).atEndOfMonth())) {
            capitalizar(YearMonth.from(fecha), fecha, corte, ahora);
        }
        intereses.registrarCierre(fecha, cuentas, devengado, ahora);
        log.info("Cierre del {}: {} cuentas devengaron intereses", fecha, cuentas);
    }

    private Optional<Devengo> devengar(Cuenta cuenta, LocalDate fecha, Instant corte, Instant ahora) {
        Optional<TasaVigente> tasa = intereses.tasaVigente(cuenta.productoId(), fecha);
        if (tasa.isEmpty()) {
            log.warn("El producto {} no tenia tasa vigente el {}: la cuenta {} no devenga",
                    cuenta.productoId(), fecha, cuenta.id());
            return Optional.empty();
        }
        Dinero saldo = intereses.saldoAlCorte(cuenta.id(), cuenta.moneda(), corte);
        return Devengo.calcular(cuenta.id(), fecha, saldo, tasa.get(), ahora);
    }

    private void capitalizar(YearMonth periodo, LocalDate hasta, Instant momento, Instant ahora) {
        for (UUID cuentaId : intereses.cuentasConDevengoPendiente(hasta)) {
            Cuenta cuenta = libro.buscarPorId(cuentaId)
                    .orElseThrow(() -> new IllegalStateException("No existe la cuenta " + cuentaId));
            Capitalizacion capitalizacion = Capitalizacion.de(cuentaId, periodo,
                    intereses.devengadoSinCapitalizar(cuentaId, hasta), intereses.ultimoResiduo(cuentaId),
                    cuenta.moneda());
            if (capitalizacion.hayQueAbonar()) {
                abonar(cuenta, capitalizacion, momento);
            }
            intereses.registrar(capitalizacion, hasta, ahora);
        }
    }

    private void abonar(Cuenta cuenta, Capitalizacion capitalizacion, Instant momento) {
        Cuenta fuente = libro.cuentaDeIntereses(cuenta.moneda());
        // Mismo orden de bloqueo que las transferencias, para no esperarse mutuamente.
        libro.bloquear(Stream.of(fuente.id(), cuenta.id()).sorted(Comparator.naturalOrder()).toList());
        Movimiento movimiento = Movimiento.capitalizacionDeIntereses(capitalizacion.movimientoId(), fuente, cuenta,
                capitalizacion.abonado(), etiqueta(capitalizacion.periodo()), momento);
        libro.registrar(movimiento);
        Dinero saldo = libro.saldoDe(cuenta.id(), cuenta.moneda());
        eventos.registrar(AGREGADO, movimiento.id(), InteresCapitalizado.class.getSimpleName(),
                new InteresCapitalizado(movimiento.id(), cuenta.id(), cuenta.usuarioId(),
                        cuenta.numero().enmascarado(), capitalizacion.abonado().importe().toPlainString(),
                        cuenta.moneda().name(), saldo.importe().toPlainString(),
                        capitalizacion.periodo().toString(), momento.toString()));
        auditoria.registrar(TipoDeEventoDeOperacion.INTERES_CAPITALIZADO, cuenta.usuarioId(), movimiento.id());
    }

    /** "10/2026": corto, sin depender del idioma del servidor. */
    static String etiqueta(YearMonth periodo) {
        return "%02d/%d".formatted(periodo.getMonthValue(), periodo.getYear());
    }

    /**
     * Lo que oye notification-service para avisar del interes ganado (escenario 2).
     *
     * @param periodo el mes capitalizado, {@code AAAA-MM}
     */
    public record InteresCapitalizado(UUID movimientoId, UUID cuentaId, UUID usuarioId, String cuenta,
                                      String importe, String moneda, String saldo, String periodo,
                                      String registradoEn) {
    }
}
