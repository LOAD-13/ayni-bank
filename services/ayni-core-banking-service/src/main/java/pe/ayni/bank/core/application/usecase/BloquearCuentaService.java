package pe.ayni.bank.core.application.usecase;

import java.time.Clock;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.core.domain.model.CuatroOjosException;
import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.EstadoCuenta;
import pe.ayni.bank.core.domain.model.NumeroDeCuenta;
import pe.ayni.bank.core.domain.model.SolicitudDeBloqueo;
import pe.ayni.bank.core.domain.model.SolicitudDeBloqueoNoValidaException;
import pe.ayni.bank.core.domain.model.TipoDeEventoDeOperacion;
import pe.ayni.bank.core.domain.port.in.BloquearCuentaUseCase;
import pe.ayni.bank.core.domain.port.out.LibroMayorPort;
import pe.ayni.bank.core.domain.port.out.PistaDeAuditoriaPort;
import pe.ayni.bank.core.domain.port.out.RepositorioDeCuentasPort;
import pe.ayni.bank.core.domain.port.out.RepositorioDeSolicitudesDeBloqueoPort;

/**
 * Bloqueo de cuenta con cuatro ojos (HU-15, AYNI-282). Pedir el bloqueo no toca la cuenta: solo
 * la aprobacion de otra persona la bloquea, y deja la version en el historial (Envers) con el
 * supervisor como actor. Cada paso, y cada intento de aprobar lo propio, queda en la pista.
 */
@Service
public class BloquearCuentaService implements BloquearCuentaUseCase {

    private static final Logger log = LoggerFactory.getLogger(BloquearCuentaService.class);

    private final LibroMayorPort libro;
    private final RepositorioDeCuentasPort cuentas;
    private final RepositorioDeSolicitudesDeBloqueoPort solicitudes;
    private final PistaDeAuditoriaPort auditoria;
    private final Clock reloj;

    public BloquearCuentaService(LibroMayorPort libro, RepositorioDeCuentasPort cuentas,
                                 RepositorioDeSolicitudesDeBloqueoPort solicitudes,
                                 PistaDeAuditoriaPort auditoria, Clock reloj) {
        this.libro = libro;
        this.cuentas = cuentas;
        this.solicitudes = solicitudes;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public SolicitudDeBloqueo iniciar(UUID operador, String numeroDeCuenta, String motivo) {
        Cuenta cuenta = cuentaPorNumero(numeroDeCuenta);
        if (cuenta.estado() != EstadoCuenta.ACTIVA) {
            throw new SolicitudDeBloqueoNoValidaException("La cuenta no esta activa.");
        }
        if (solicitudes.existePendientePara(cuenta.id())) {
            throw new SolicitudDeBloqueoNoValidaException("La cuenta ya tiene un bloqueo pendiente de aprobacion.");
        }
        SolicitudDeBloqueo solicitud = SolicitudDeBloqueo.iniciar(UUID.randomUUID(), cuenta.id(), motivo,
                operador, reloj.instant());
        solicitudes.registrar(solicitud);
        auditoria.registrar(TipoDeEventoDeOperacion.BLOQUEO_SOLICITADO, operador, solicitud.id());
        log.info("Bloqueo solicitado. solicitudId={} cuentaId={}", solicitud.id(), cuenta.id());
        return solicitud;
    }

    @Override
    @Transactional
    public SolicitudDeBloqueo aprobar(UUID supervisor, UUID solicitudId) {
        SolicitudDeBloqueo solicitud = solicitudes.buscar(solicitudId)
                .orElseThrow(() -> new NoSuchElementException("No existe la solicitud de bloqueo."));
        SolicitudDeBloqueo aprobada;
        try {
            aprobada = solicitud.aprobar(supervisor, reloj.instant());
        } catch (CuatroOjosException e) {
            auditoria.registrarCuatroOjosDenegado(supervisor, solicitudId);
            log.warn("Cuatro ojos: quien inicio el bloqueo intento aprobarlo. solicitudId={}", solicitudId);
            throw e;
        }
        // Primero la aprobacion, que solo se guarda si nadie la aprobo antes; despues la cuenta.
        solicitudes.aprobar(aprobada);
        Cuenta cuenta = libro.buscarPorId(solicitud.cuentaId())
                .orElseThrow(() -> new IllegalStateException("La cuenta de la solicitud ya no existe."));
        cuentas.guardar(cuenta.bloquear());
        auditoria.registrar(TipoDeEventoDeOperacion.CUENTA_BLOQUEADA, supervisor, solicitudId);
        log.info("Cuenta bloqueada con cuatro ojos. solicitudId={} cuentaId={}", solicitudId, cuenta.id());
        return aprobada;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SolicitudDeBloqueo> pendientes() {
        return solicitudes.pendientes();
    }

    private Cuenta cuentaPorNumero(String numero) {
        if (numero == null || !numero.matches("^[0-9]{14}$")) {
            throw new NoSuchElementException("No existe una cuenta de cliente con ese numero.");
        }
        return libro.buscarPorNumero(new NumeroDeCuenta(numero))
                .orElseThrow(() -> new NoSuchElementException("No existe una cuenta de cliente con ese numero."));
    }
}
