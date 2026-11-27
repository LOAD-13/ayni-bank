package pe.ayni.bank.core.application.usecase;

import java.time.Instant;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.HistorialDeCuenta;
import pe.ayni.bank.core.domain.model.NumeroDeCuenta;
import pe.ayni.bank.core.domain.port.in.ConsultarHistorialDeCuentaUseCase;
import pe.ayni.bank.core.domain.port.out.HistorialDeCuentasPort;
import pe.ayni.bank.core.domain.port.out.LibroMayorPort;

/** Reconstruye el estado de una cuenta en una fecha a partir de su historial (HU-15, AYNI-280). */
@Service
public class ConsultarHistorialDeCuentaService implements ConsultarHistorialDeCuentaUseCase {

    private final LibroMayorPort libro;
    private final HistorialDeCuentasPort historial;

    public ConsultarHistorialDeCuentaService(LibroMayorPort libro, HistorialDeCuentasPort historial) {
        this.libro = libro;
        this.historial = historial;
    }

    @Override
    @Transactional(readOnly = true)
    public HistorialDeCuenta consultar(String numeroDeCuenta, Instant fecha) {
        if (numeroDeCuenta == null || !numeroDeCuenta.matches("^[0-9]{14}$")) {
            throw new NoSuchElementException("No existe una cuenta de cliente con ese numero.");
        }
        Cuenta cuenta = libro.buscarPorNumero(new NumeroDeCuenta(numeroDeCuenta))
                .orElseThrow(() -> new NoSuchElementException("No existe una cuenta de cliente con ese numero."));
        return HistorialDeCuenta.en(fecha, historial.versionesDe(cuenta.id()));
    }
}
