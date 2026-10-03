package pe.ayni.bank.core.infrastructure.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

import pe.ayni.bank.core.domain.model.Asiento;
import pe.ayni.bank.core.domain.model.Cci;
import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.EstadoCuenta;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.Movimiento;
import pe.ayni.bank.core.domain.model.NumeroDeCuenta;
import pe.ayni.bank.core.domain.model.TipoDeAsiento;
import pe.ayni.bank.core.domain.port.out.LibroMayorPort;

/** Implementa {@link LibroMayorPort} sobre JPA. */
@Repository
public class AdaptadorLibroMayor implements LibroMayorPort {

    private final CuentaJpaRepository cuentas;
    private final AsientoJpaRepository asientos;
    private final UUID fondeoPen;

    AdaptadorLibroMayor(CuentaJpaRepository cuentas, AsientoJpaRepository asientos,
                        @Value("${ayni.cuentas.fondeo-pen}") UUID fondeoPen) {
        this.cuentas = cuentas;
        this.asientos = asientos;
        this.fondeoPen = fondeoPen;
    }

    @Override
    public Optional<Cuenta> buscarPorNumero(NumeroDeCuenta numero) {
        return cuentas.findByNumero(numero.valor())
                .filter(fila -> !esTecnica(fila))
                .map(AdaptadorLibroMayor::aDominio);
    }

    /**
     * Las cuentas tecnicas —fondeo (V3), compensacion interbancaria (V4)— tienen titulares
     * reservados 00000000-0000-0000-0000-00000000000N. Identity genera UUID v4, cuyos 64 bits
     * altos nunca son cero, asi que ningun cliente puede coincidir: nadie les transfiere.
     */
    private static boolean esTecnica(CuentaEntity fila) {
        return fila.getUsuarioId().getMostSignificantBits() == 0L;
    }

    @Override
    public Optional<Cuenta> buscarPorId(UUID cuentaId) {
        return cuentas.findById(cuentaId).map(AdaptadorLibroMayor::aDominio);
    }

    @Override
    public Cuenta cuentaDeFondeo(Moneda moneda) {
        if (moneda != Moneda.PEN) {
            throw new IllegalStateException("No hay cuenta de fondeo para " + moneda);
        }
        return buscarPorId(fondeoPen).orElseThrow(() -> new IllegalStateException(
                "Falta la cuenta tecnica de fondeo: la siembra la migracion V3."));
    }

    @Override
    public void bloquear(List<UUID> cuentaIds) {
        cuentas.bloquearPorId(cuentaIds);
    }

    @Override
    public Dinero saldoDe(UUID cuentaId, Moneda moneda) {
        return new Dinero(asientos.saldoDe(cuentaId), moneda);
    }

    @Override
    public void registrar(Movimiento movimiento) {
        asientos.saveAll(movimiento.asientos().stream()
                .map(a -> new AsientoEntity(a.id(), a.cuentaId(), a.movimientoId(),
                        a.tipo().name(), a.importe().importe(), a.importe().moneda().name(),
                        a.concepto(), a.registradoEn()))
                .toList());
    }

    @Override
    public List<Asiento> ultimosAsientosDe(UUID cuentaId, int limite) {
        return asientos.findByCuentaIdOrderByRegistradoEnDesc(cuentaId, Limit.of(limite)).stream()
                .map(AdaptadorLibroMayor::aDominio)
                .toList();
    }

    @Override
    public List<Asiento> asientosDelMovimiento(UUID movimientoId) {
        return asientos.findByMovimientoId(movimientoId).stream()
                .map(AdaptadorLibroMayor::aDominio)
                .toList();
    }

    private static Asiento aDominio(AsientoEntity fila) {
        return new Asiento(fila.getId(), fila.getCuentaId(), fila.getMovimientoId(),
                TipoDeAsiento.valueOf(fila.getTipo()),
                new Dinero(fila.getImporte(), Moneda.valueOf(fila.getMoneda())),
                fila.getConcepto(), fila.getRegistradoEn());
    }

    private static Cuenta aDominio(CuentaEntity fila) {
        return Cuenta.reconstituir(
                fila.getId(), fila.getUsuarioId(), fila.getProductoId(),
                new NumeroDeCuenta(fila.getNumero()), new Cci(fila.getCci()),
                Moneda.valueOf(fila.getMoneda()),
                EstadoCuenta.valueOf(fila.getEstado()), fila.getAbiertaEn());
    }
}
