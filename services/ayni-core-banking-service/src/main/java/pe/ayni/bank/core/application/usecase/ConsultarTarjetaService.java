package pe.ayni.bank.core.application.usecase;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.core.domain.model.CvvDinamico;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.OperacionAConfirmar;
import pe.ayni.bank.core.domain.model.PanRevelado;
import pe.ayni.bank.core.domain.model.SinTarjetaException;
import pe.ayni.bank.core.domain.model.Tarjeta;
import pe.ayni.bank.core.domain.port.in.ConsultarTarjetaUseCase;
import pe.ayni.bank.core.domain.port.out.PistaDeAuditoriaPort;
import pe.ayni.bank.core.domain.port.out.RepositorioDeTarjetasPort;
import pe.ayni.bank.core.domain.port.out.VerificadorDeConfirmacionPort;
import pe.ayni.bank.core.domain.service.CalculadoraDeCvv;

/**
 * La tarjeta vista por su titular · HU-09.
 *
 * <p><strong>Revelar el PAN</strong> exige el mismo segundo factor que una transferencia
 * (ADR-0031). La confirmacion de identity se pide para la operacion
 * {@code REVELAR-PAN-<ultimos 4>} con un importe simbolico de S/ 0.01, porque identity exige
 * un importe positivo; no se cobra nada. Como el token firma esa operacion exacta, no sirve
 * para mover dinero ni para revelar otra tarjeta. El numero se muestra
 * {@link #TIEMPO_VISIBLE} y la revelacion queda en la auditoria con la IP.
 */
@Service
public class ConsultarTarjetaService implements ConsultarTarjetaUseCase {

    public static final Duration TIEMPO_VISIBLE = Duration.ofSeconds(30);
    public static final String PREFIJO_DE_LA_OPERACION = "REVELAR-PAN-";
    private static final Dinero IMPORTE_SIMBOLICO = new Dinero(new BigDecimal("0.01"), Moneda.PEN);

    private final RepositorioDeTarjetasPort tarjetas;
    private final CalculadoraDeCvv calculadora;
    private final VerificadorDeConfirmacionPort confirmaciones;
    private final PistaDeAuditoriaPort auditoria;
    private final Clock reloj;

    public ConsultarTarjetaService(RepositorioDeTarjetasPort tarjetas, CalculadoraDeCvv calculadora,
            VerificadorDeConfirmacionPort confirmaciones, PistaDeAuditoriaPort auditoria, Clock reloj) {
        this.tarjetas = tarjetas;
        this.calculadora = calculadora;
        this.confirmaciones = confirmaciones;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    @Override
    public Optional<Tarjeta> deTitular(UUID usuarioId) {
        return tarjetas.deTitular(usuarioId);
    }

    @Override
    public CvvDinamico cvv(UUID usuarioId) {
        Tarjeta tarjeta = tarjetaDe(usuarioId);
        Instant ahora = reloj.instant();
        return new CvvDinamico(calculadora.calcular(tarjeta.pan(), ahora), calculadora.validoHasta(ahora));
    }

    @Override
    @Transactional
    public PanRevelado revelar(UUID usuarioId, String confirmacion, UUID clave, String ip) {
        Tarjeta tarjeta = tarjetaDe(usuarioId);
        confirmaciones.verificar(confirmacion, usuarioId,
                new OperacionAConfirmar(PREFIJO_DE_LA_OPERACION + tarjeta.pan().ultimos4(), IMPORTE_SIMBOLICO, clave));
        auditoria.registrarRevelacionDePan(usuarioId, tarjeta.id(), ip);
        return new PanRevelado(tarjeta.pan().formateado(), reloj.instant().plus(TIEMPO_VISIBLE));
    }

    private Tarjeta tarjetaDe(UUID usuarioId) {
        return tarjetas.deTitular(usuarioId).orElseThrow(SinTarjetaException::new);
    }
}
