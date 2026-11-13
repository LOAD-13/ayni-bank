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

import pe.ayni.bank.core.domain.model.Conversion;
import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.MotivoDeRechazo;
import pe.ayni.bank.core.domain.model.OperacionRechazadaException;
import pe.ayni.bank.core.domain.model.TipoDeCambioAplicado;
import pe.ayni.bank.core.domain.model.TipoDeEventoDeOperacion;
import pe.ayni.bank.core.domain.port.in.ConvertirMonedaUseCase;
import pe.ayni.bank.core.domain.port.out.LibroMayorPort;
import pe.ayni.bank.core.domain.port.out.PistaDeAuditoriaPort;
import pe.ayni.bank.core.domain.port.out.RegistroDeConversionesPort;
import pe.ayni.bank.core.domain.port.out.RegistroDeIdempotenciaPort;
import pe.ayni.bank.core.domain.port.out.RepositorioDeCuentasPort;

/**
 * Conversion entre la cuenta en soles y la cuenta en dolares del titular (HU-12, AYNI-271).
 *
 * <p>Registra las dos patas en el libro mayor, cada una en su moneda, y la fila que las une con el
 * tipo aplicado, todo en la misma transaccion. Se bloquean solo las cuentas del cliente: las
 * posiciones cambiarias del banco no tienen saldo minimo que proteger, y bloquearlas pondria en
 * fila todas las conversiones del banco.
 */
@Service
public class ConvertirMonedaService implements ConvertirMonedaUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConvertirMonedaService.class);

    private final RepositorioDeCuentasPort cuentas;
    private final LibroMayorPort libro;
    private final RegistroDeConversionesPort conversiones;
    private final RegistroDeIdempotenciaPort idempotencia;
    private final PistaDeAuditoriaPort auditoria;
    private final Clock reloj;

    public ConvertirMonedaService(RepositorioDeCuentasPort cuentas, LibroMayorPort libro,
                                  RegistroDeConversionesPort conversiones,
                                  RegistroDeIdempotenciaPort idempotencia,
                                  PistaDeAuditoriaPort auditoria, Clock reloj) {
        this.cuentas = cuentas;
        this.libro = libro;
        this.conversiones = conversiones;
        this.idempotencia = idempotencia;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public Conversion convertir(UUID usuarioId, Dinero importe, TipoDeCambioAplicado tipo, UUID clave) {
        try {
            Optional<Conversion> repetida = repetida(clave, usuarioId);
            if (repetida.isPresent()) {
                auditoria.registrar(TipoDeEventoDeOperacion.OPERACION_REPETIDA, usuarioId,
                        repetida.get().salida().id());
                return repetida.get();
            }
            Moneda monedaDestino = importe.moneda() == Moneda.PEN ? Moneda.USD : Moneda.PEN;
            Cuenta origen = cuentaDe(usuarioId, importe.moneda());
            Cuenta destino = cuentaDe(usuarioId, monedaDestino);

            libro.bloquear(List.of(origen.id(), destino.id()).stream()
                    .sorted(Comparator.naturalOrder()).toList());
            // Otra peticion con la misma clave pudo terminar mientras se esperaba el bloqueo.
            Optional<Conversion> adelantada = repetida(clave, usuarioId);
            if (adelantada.isPresent()) {
                return adelantada.get();
            }

            Conversion conversion = Conversion.de(UUID.randomUUID(), origen,
                    libro.saldoDe(origen.id(), origen.moneda()), destino,
                    libro.cuentaDePosicionCambiaria(importe.moneda()),
                    libro.cuentaDePosicionCambiaria(monedaDestino), importe, tipo, reloj.instant());
            libro.registrar(conversion.salida());
            libro.registrar(conversion.entrada());
            conversiones.registrar(conversion);
            idempotencia.recordar(clave, conversion.salida().id());
            auditoria.registrar(TipoDeEventoDeOperacion.CONVERSION_REALIZADA, usuarioId, conversion.salida().id());

            log.info("Conversion registrada. conversionId={} salida={} entrada={}",
                    conversion.id(), conversion.salida().id(), conversion.entrada().id());
            return conversion;
        } catch (OperacionRechazadaException e) {
            auditoria.registrarRechazo(usuarioId, e.motivo());
            throw e;
        }
    }

    private Optional<Conversion> repetida(UUID clave, UUID usuarioId) {
        return idempotencia.resultadoDe(clave).map(salidaId -> conversiones.buscarPorSalida(salidaId)
                .filter(c -> c.usuarioId().equals(usuarioId))
                .orElseThrow(OperarCuentaService.ClaveDeIdempotenciaReutilizadaException::new));
    }

    private Cuenta cuentaDe(UUID usuarioId, Moneda moneda) {
        return cuentas.buscarActivaDe(usuarioId, moneda)
                .orElseThrow(() -> new OperacionRechazadaException(MotivoDeRechazo.SIN_CUENTA));
    }
}
