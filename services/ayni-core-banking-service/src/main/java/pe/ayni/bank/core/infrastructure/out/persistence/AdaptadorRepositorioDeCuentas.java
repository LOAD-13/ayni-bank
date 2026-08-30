package pe.ayni.bank.core.infrastructure.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import pe.ayni.bank.core.domain.model.Asiento;
import pe.ayni.bank.core.domain.model.Cci;
import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.EstadoCuenta;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.NumeroDeCuenta;
import pe.ayni.bank.core.domain.model.TipoDeAsiento;
import pe.ayni.bank.core.domain.port.out.RepositorioDeCuentasPort;

/** Implementa {@link RepositorioDeCuentasPort} sobre JPA. */
@Repository
public class AdaptadorRepositorioDeCuentas implements RepositorioDeCuentasPort {

    private final CuentaJpaRepository cuentas;
    private final AsientoJpaRepository asientos;
    private final TasaJpaRepository tasas;

    AdaptadorRepositorioDeCuentas(CuentaJpaRepository cuentas, AsientoJpaRepository asientos,
                                  TasaJpaRepository tasas) {
        this.cuentas = cuentas;
        this.asientos = asientos;
        this.tasas = tasas;
    }

    @Override
    public Optional<java.math.BigDecimal> treaVigenteDe(short productoId) {
        return Optional.ofNullable(tasas.treaVigente(productoId));
    }

    @Override
    public boolean existeCuentaActiva(UUID usuarioId, Moneda moneda) {
        return cuentas.existsByUsuarioIdAndMonedaAndEstado(
                usuarioId, moneda.name(), EstadoCuenta.ACTIVA.name());
    }

    @Override
    public Optional<Cuenta> buscarActivaDe(UUID usuarioId, Moneda moneda) {
        return cuentas.findByUsuarioIdAndMonedaAndEstado(
                        usuarioId, moneda.name(), EstadoCuenta.ACTIVA.name())
                .map(AdaptadorRepositorioDeCuentas::aDominio);
    }

    @Override
    public long siguienteCorrelativo() {
        return cuentas.siguienteCorrelativo();
    }

    @Override
    public Cuenta guardar(Cuenta cuenta) {
        cuentas.save(new CuentaEntity(
                cuenta.id(), cuenta.usuarioId(), cuenta.productoId(),
                cuenta.numero().valor(), cuenta.cci().valor(),
                cuenta.moneda().name(), cuenta.estado().name(), cuenta.abiertaEn()));

        return cuenta;
    }

    @Override
    public List<Asiento> asientosDe(UUID cuentaId) {
        return asientos.findByCuentaIdOrderByRegistradoEnAsc(cuentaId).stream()
                .map(fila -> new Asiento(
                        fila.getId(), fila.getCuentaId(), fila.getMovimientoId(),
                        TipoDeAsiento.valueOf(fila.getTipo()),
                        new Dinero(fila.getImporte(), Moneda.valueOf(fila.getMoneda())),
                        fila.getConcepto(), fila.getRegistradoEn()))
                .toList();
    }

    private static Cuenta aDominio(CuentaEntity fila) {
        return Cuenta.reconstituir(
                fila.getId(), fila.getUsuarioId(), fila.getProductoId(),
                new NumeroDeCuenta(fila.getNumero()), new Cci(fila.getCci()),
                Moneda.valueOf(fila.getMoneda()),
                EstadoCuenta.valueOf(fila.getEstado()), fila.getAbiertaEn());
    }
}
