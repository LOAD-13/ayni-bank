package pe.ayni.bank.core.application.usecase;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.core.domain.model.ControlesDeTarjeta;
import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.EstadoDeTarjeta;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.MotivoDeRechazo;
import pe.ayni.bank.core.domain.model.Movimiento;
import pe.ayni.bank.core.domain.model.OperacionRechazadaException;
import pe.ayni.bank.core.domain.model.SinTarjetaException;
import pe.ayni.bank.core.domain.model.Tarjeta;
import pe.ayni.bank.core.domain.model.TipoDeEventoDeOperacion;
import pe.ayni.bank.core.domain.port.in.ControlarTarjetaUseCase;
import pe.ayni.bank.core.domain.port.out.LibroMayorPort;
import pe.ayni.bank.core.domain.port.out.PistaDeAuditoriaPort;
import pe.ayni.bank.core.domain.port.out.PublicadorDeEventosPort;
import pe.ayni.bank.core.domain.port.out.RepositorioDeTarjetasPort;

/**
 * Implementa los controles y la validacion de consumos de la tarjeta de debito virtual · HU-10
 * (AYNI-21, subtareas AYNI-246 a AYNI-251).
 */
@Service
public class ControlarTarjetaService implements ControlarTarjetaUseCase {

    private static final String AGREGADO_TARJETA = "Tarjeta";

    private final RepositorioDeTarjetasPort tarjetas;
    private final LibroMayorPort libro;
    private final PistaDeAuditoriaPort auditoria;
    private final PublicadorDeEventosPort eventos;
    private final Clock reloj;

    public ControlarTarjetaService(RepositorioDeTarjetasPort tarjetas, LibroMayorPort libro,
                                   PistaDeAuditoriaPort auditoria, PublicadorDeEventosPort eventos,
                                   Clock reloj) {
        this.tarjetas = tarjetas;
        this.libro = libro;
        this.auditoria = auditoria;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    @Override
    @Transactional(readOnly = true)
    public ControlesDeTarjeta consultarControles(UUID usuarioId) {
        Tarjeta tarjeta = tarjetaDe(usuarioId);
        return tarjetas.controlesDe(tarjeta, reloj.instant());
    }

    @Override
    @Transactional
    public ControlesDeTarjeta congelar(UUID usuarioId) {
        Tarjeta actual = tarjetaDe(usuarioId);
        Instant ahora = reloj.instant();
        Tarjeta congelada = actual.congelar();
        tarjetas.actualizarEstado(congelada.id(), EstadoDeTarjeta.CONGELADA);
        auditoria.registrar(TipoDeEventoDeOperacion.TARJETA_CONGELADA, usuarioId, congelada.id());
        eventos.registrar(AGREGADO_TARJETA, congelada.id(), "TarjetaCongelada", Map.of(
                "tarjetaId", congelada.id(),
                "usuarioId", usuarioId,
                "ultimos4", congelada.pan().ultimos4(),
                "estado", EstadoDeTarjeta.CONGELADA.name(),
                "ocurridoEn", ahora.toString()));
        return tarjetas.controlesDe(congelada, ahora);
    }

    @Override
    @Transactional
    public ControlesDeTarjeta descongelar(UUID usuarioId) {
        Tarjeta actual = tarjetaDe(usuarioId);
        Instant ahora = reloj.instant();
        Tarjeta activa = actual.descongelar();
        tarjetas.actualizarEstado(activa.id(), EstadoDeTarjeta.ACTIVA);
        auditoria.registrar(TipoDeEventoDeOperacion.TARJETA_DESCONGELADA, usuarioId, activa.id());
        eventos.registrar(AGREGADO_TARJETA, activa.id(), "TarjetaDescongelada", Map.of(
                "tarjetaId", activa.id(),
                "usuarioId", usuarioId,
                "ultimos4", activa.pan().ultimos4(),
                "estado", EstadoDeTarjeta.ACTIVA.name(),
                "ocurridoEn", ahora.toString()));
        return tarjetas.controlesDe(activa, ahora);
    }

    @Override
    @Transactional
    public ControlesDeTarjeta configurar(UUID usuarioId, Dinero limiteDiario, boolean comprasPorInternet) {
        if (limiteDiario == null || !limiteDiario.esPositivo()) {
            throw new IllegalArgumentException("El limite diario debe ser mayor que cero.");
        }
        Tarjeta tarjeta = tarjetaDe(usuarioId);
        Instant ahora = reloj.instant();
        tarjetas.actualizarControles(tarjeta.id(), limiteDiario, comprasPorInternet, ahora);
        auditoria.registrar(TipoDeEventoDeOperacion.CONTROL_TARJETA_ACTUALIZADO, usuarioId, tarjeta.id());
        eventos.registrar(AGREGADO_TARJETA, tarjeta.id(), "ControlTarjetaActualizado", Map.of(
                "tarjetaId", tarjeta.id(),
                "usuarioId", usuarioId,
                "ultimos4", tarjeta.pan().ultimos4(),
                "limiteDiario", limiteDiario.importe().toPlainString(),
                "comprasPorInternet", comprasPorInternet,
                "ocurridoEn", ahora.toString()));
        return tarjetas.controlesDe(tarjeta, ahora);
    }

    @Override
    @Transactional
    public void consumir(UUID usuarioId, Dinero importe, boolean porInternet, String comercio) {
        Tarjeta tarjeta = tarjetaDe(usuarioId);
        Instant ahora = reloj.instant();

        if (tarjeta.estado() == EstadoDeTarjeta.CONGELADA) {
            rechazar(usuarioId, MotivoDeRechazo.TARJETA_CONGELADA);
        }

        Cuenta origen = libro.buscarPorId(tarjeta.cuentaId())
                .orElseThrow(() -> new OperacionRechazadaException(MotivoDeRechazo.SIN_CUENTA));
        Cuenta contrapartida = libro.cuentaDeFondeo(importe.moneda());
        // Se bloquea antes de leer lo consumido hoy: dos consumos simultáneos no pueden pasar
        // los dos el límite diario con la misma lectura.
        libro.bloquear(List.of(origen.id(), contrapartida.id()));

        ControlesDeTarjeta controles = tarjetas.controlesDe(tarjeta, ahora);
        if (porInternet && !controles.comprasPorInternet()) {
            rechazar(usuarioId, MotivoDeRechazo.COMPRAS_POR_INTERNET_DESHABILITADAS);
        }

        Dinero nuevoAcumulado = controles.consumidoHoy().mas(importe);
        if (nuevoAcumulado.importe().compareTo(controles.limiteDiario().importe()) > 0) {
            rechazar(usuarioId, MotivoDeRechazo.LIMITE_DIARIO_TARJETA_EXCEDIDO);
        }

        Dinero saldoActual = libro.saldoDe(origen.id(), origen.moneda());
        if (saldoActual.menos(importe).esNegativo()) {
            rechazar(usuarioId, MotivoDeRechazo.SALDO_INSUFICIENTE);
        }

        UUID movimientoId = UUID.randomUUID();
        String descripcion = (comercio == null || comercio.isBlank())
                ? "Consumo con tarjeta"
                : "Consumo con tarjeta · " + comercio.trim();
        Movimiento movimiento = Movimiento.transferencia(
                movimientoId, origen, saldoActual, contrapartida, importe, descripcion, ahora);
        libro.registrar(movimiento);
        tarjetas.registrarConsumo(tarjeta.id(), movimientoId, importe, porInternet,
                comercio == null || comercio.isBlank() ? "Comercio" : comercio.trim(), ahora);
        auditoria.registrar(TipoDeEventoDeOperacion.CONSUMO_TARJETA_REALIZADO, usuarioId, movimientoId);
    }

    private Tarjeta tarjetaDe(UUID usuarioId) {
        return tarjetas.deTitular(usuarioId).orElseThrow(SinTarjetaException::new);
    }

    private void rechazar(UUID usuarioId, MotivoDeRechazo motivo) {
        auditoria.registrarRechazo(usuarioId, motivo);
        throw new OperacionRechazadaException(motivo);
    }
}
