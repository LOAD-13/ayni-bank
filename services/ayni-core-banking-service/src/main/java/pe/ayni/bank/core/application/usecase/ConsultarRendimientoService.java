package pe.ayni.bank.core.application.usecase;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.Rendimiento;
import pe.ayni.bank.core.domain.port.in.ConsultarRendimientoUseCase;
import pe.ayni.bank.core.domain.port.out.RegistroDeInteresesPort;
import pe.ayni.bank.core.domain.port.out.RepositorioDeCuentasPort;

@Service
public class ConsultarRendimientoService implements ConsultarRendimientoUseCase {

    /** Un mes de devengos: suficiente para ver el efecto de un cambio de tasa reciente. */
    static final int DEVENGOS_VISIBLES = 31;

    private final RepositorioDeCuentasPort cuentas;
    private final RegistroDeInteresesPort intereses;
    private final Clock reloj;

    public ConsultarRendimientoService(RepositorioDeCuentasPort cuentas, RegistroDeInteresesPort intereses,
                                       Clock reloj) {
        this.cuentas = cuentas;
        this.intereses = intereses;
        this.reloj = reloj;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Rendimiento> deTitular(UUID usuarioId) {
        LocalDate hoy = LocalDate.now(reloj.withZone(CierreDiarioService.LIMA));
        return cuentas.buscarActivaDe(usuarioId, Moneda.PEN).flatMap(cuenta ->
                intereses.tasaVigente(cuenta.productoId(), hoy).map(tasa -> new Rendimiento(tasa, cuenta.moneda(),
                        intereses.devengadoEntre(cuenta.id(), hoy.withDayOfMonth(1), hoy),
                        new Dinero(intereses.abonadoTotal(cuenta.id()), cuenta.moneda()),
                        intereses.ultimosDevengos(cuenta.id(), DEVENGOS_VISIBLES))));
    }
}
